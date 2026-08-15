package com.example.clustermanager.infrastructure.pseudo.messaging;

import com.example.clustermanager.core.model.MessageDeliveryResult;
import com.example.clustermanager.core.model.MessageScenario;
import com.example.clustermanager.infrastructure.pseudo.node.ManagedNode;
import com.example.clustermanager.infrastructure.pseudo.node.NodeRegistry;
import com.example.clustermanager.infrastructure.pseudo.runtime.EmbeddedRocketMqRuntime;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.rocketmq.broker.BrokerController;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.topic.TopicValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 嵌入式消息工作台——統一處理 PSEUDO 模式下的消息模擬。
 *
 * <p>取代舊的 PseudoRocketMqBridge 和 PseudoNodeRuntime.deliverMessage/consumeMessage。
 * 作為 Spring 單例 {@code @Component}，被 {@link com.example.clustermanager.infrastructure.pseudo.PseudoClusterProvider}
 * 委託執行消息模擬操作。
 *
 * <p><b>兩條消息路徑</b>：
 * <ul>
 *   <li><b>嵌入式路徑</b>（{@link #simulateWithEmbedded}）：通過 {@link EmbeddedRocketMqRuntime} 的
 *       進程內 NameServer 地址 produce/consume。適用於所有節點均為 VIRTUAL 類型的場景。</li>
 *   <li><b>HOST 路徑</b>（{@link #simulateWithHost}）：當 producer 或任一 consumer 為 HOST 類型時，
 *       通過外部 NameServer 地址 produce/consume。從 {@link NodeRegistry#hostNodes()} 中
 *       查找 HOST 類型的 NameServer 地址。</li>
 * </ul>
 * 路徑選擇邏輯在 {@link #simulate} 中根據節點類型自動判斷。
 *
 * <p><b>Topic 創建的 Spike 修復</b>（{@link #doSimulate} 中）：
 * <ul>
 *   <li>Broker 首次心跳不註冊 topic，導致生產者首次 send 報 "No route info of this topic"</li>
 *   <li>解決：直接調用 {@code brokerController.getTopicConfigManager().createTopicInSendMessageMethod(...)} 建 topic</li>
 *   <li>然後調用 {@code brokerController.registerBrokerAll(true, false, true)} 強制立即註冊到 NameServer</li>
 *   <li>最後調用 {@code producer.getDefaultMQProducerImpl().getmQClientFactory().updateTopicRouteInfoFromNameServer(topic)} 拉取路由</li>
 * </ul>
 *
 * <p><b>P0 修復</b>：HOST 路徑預檢 NameServer 是否存在，缺失時返回失敗結果而非拋
 * {@code IllegalStateException}（見 {@link #resolveHostNameServersOrNull}）。
 *
 * <p><b>P3 修復</b>：consumer group 追加唯一後綴（UUID 前 8 位），避免連續模擬時
 * "consumer group has been created already" 異常。
 *
 * <p><b>線程安全</b>：無實例可變狀態，{@code runtime} 和 {@code nodeRegistry} 為線程安全的依賴。
 * 每次模擬創建獨立的 producer/consumer 實例，模擬完成後關閉。
 */
@Component
public class EmbeddedMessageWorkbench {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedMessageWorkbench.class);

    /** 嵌入式 RocketMQ 運行時——提供 NameServer 地址和 Broker 控制器 */
    private final EmbeddedRocketMqRuntime runtime;
    /** 節點註冊表——查詢 producer/consumer 節點和 HOST 節點 */
    private final NodeRegistry nodeRegistry;
    /** 消息模板服務——占位符替換 */
    private final com.example.clustermanager.application.service.MessageTemplateService templateService;
    /** 真實 RocketMQ 配置——提供環境變量配置的外部 NameServer 地址作為 HOST 路徑後備 */
    private final com.example.clustermanager.infrastructure.rocketmq.RocketMqClusterProperties rocketMqProperties;
    /** 運行時連接配置——可變的 NameServer 地址，優先於啟動配置 */
    private final com.example.clustermanager.infrastructure.rocketmq.RocketMqConnectionConfig connectionConfig;

    /**
     * 構造器注入運行時、節點註冊表、模板服務、RocketMQ 配置和連接配置。
     *
     * @param runtime            嵌入式 RocketMQ 運行時
     * @param nodeRegistry       節點註冊表
     * @param templateService    消息模板服務
     * @param rocketMqProperties 真實 RocketMQ 配置（提供外部 NameServer 後備地址）
     * @param connectionConfig   運行時連接配置（可變，優先於啟動配置）
     */
    public EmbeddedMessageWorkbench(EmbeddedRocketMqRuntime runtime, NodeRegistry nodeRegistry,
                                     com.example.clustermanager.application.service.MessageTemplateService templateService,
                                     com.example.clustermanager.infrastructure.rocketmq.RocketMqClusterProperties rocketMqProperties,
                                     com.example.clustermanager.infrastructure.rocketmq.RocketMqConnectionConfig connectionConfig) {
        this.runtime = runtime;
        this.nodeRegistry = nodeRegistry;
        this.templateService = templateService;
        this.rocketMqProperties = rocketMqProperties;
        this.connectionConfig = connectionConfig;
    }

    /**
     * 執行消息模擬。根據節點類型自動選擇嵌入式或 HOST 路徑。
     *
     * <p>若 producer 或任一 consumer 為 HOST 類型，走 HOST 路徑；否則走嵌入式路徑。
     * consumer 列表為空時自動使用所有 Broker 節點作為 consumer。
     *
     * @param scenario 消息場景（topic、producer/consumer 節點、消息數量等）
     * @return 按 consumer 數量 × messageCount 生成投遞結果列表
     * @throws IllegalArgumentException producer 或 consumer 節點不存在時拋出
     */
    public List<MessageDeliveryResult> simulate(MessageScenario scenario) {
        ManagedNode producerNode = nodeRegistry.require(scenario.producerNodeId());

        List<String> consumerNodeIds = (scenario.consumerNodeIds() == null || scenario.consumerNodeIds().isEmpty())
                ? nodeRegistry.brokers().stream().map(ManagedNode::nodeId).toList()
                : scenario.consumerNodeIds();

        boolean hasHostNode = producerNode.hostBound()
                || consumerNodeIds.stream().map(nodeRegistry::require).anyMatch(ManagedNode::hostBound);

        if (hasHostNode) {
            return simulateWithHost(scenario, consumerNodeIds);
        }

        return simulateWithEmbedded(scenario, consumerNodeIds);
    }

    /**
     * 嵌入式路徑——通過進程內 NameServer produce/consume。
     *
     * <p>若 NameServer 未運行，為每個 consumer 返回失敗結果（不拋異常）。
     * 獲取第一個運行中的 Broker 控制器用於 topic 創建（Spike 修復）。
     * 模擬過程中任何異常都轉為失敗結果返回。
     *
     * @param scenario       消息場景
     * @param consumerNodeIds consumer 節點 ID 列表
     * @return 投遞結果列表（成功或失敗）
     */
    private List<MessageDeliveryResult> simulateWithEmbedded(MessageScenario scenario, List<String> consumerNodeIds) {
        String namesrvAddr = runtime.namesrvAddr();
        if (namesrvAddr == null) {
            return consumerNodeIds.stream()
                    .map(consumerNodeId -> new MessageDeliveryResult(
                            "embedded-ns-not-running",
                            scenario.producerNodeId(),
                            consumerNodeId,
                            false,
                            "Embedded NameServer is not running. Start the cluster first."
                    ))
                    .toList();
        }

        // 獲取第一個運行中的 broker 用於 topic 創建
        BrokerController brokerController = findRunningBroker();

        try {
            return doSimulate(namesrvAddr, scenario, consumerNodeIds, brokerController);
        } catch (Exception exception) {
            log.warn("Embedded message simulation failed: {}", exception.getMessage());
            return consumerNodeIds.stream()
                    .map(consumerNodeId -> new MessageDeliveryResult(
                            "embedded-failed",
                            scenario.producerNodeId(),
                            consumerNodeId,
                            false,
                            "Embedded RocketMQ simulation failed: " + exception.getMessage()
                    ))
                    .toList();
        }
    }

    /**
     * HOST 路徑——通過外部 NameServer produce/consume。
     *
     * <p>P0 修復：預檢 HOST NameServer 是否存在，缺失時返回失敗結果而非拋 IllegalStateException。
     * 模擬過程中任何異常都轉為失敗結果返回。
     *
     * @param scenario       消息場景
     * @param consumerNodeIds consumer 節點 ID 列表
     * @return 投遞結果列表（成功或失敗）
     */
    private List<MessageDeliveryResult> simulateWithHost(MessageScenario scenario, List<String> consumerNodeIds) {
        // P0 修復: 預檢 HOST NameServer 是否存在，缺失時返回失敗結果而非拋 IllegalStateException
        String hostNameServer = resolveHostNameServersOrNull();
        if (hostNameServer == null) {
            return consumerNodeIds.stream()
                    .map(consumerNodeId -> new MessageDeliveryResult(
                            "host-ns-missing",
                            scenario.producerNodeId(),
                            consumerNodeId,
                            false,
                            "Host RocketMQ bridge disabled: no HOST nameserver registered in pseudo cluster"
                    ))
                    .toList();
        }

        try {
            return doSimulate(hostNameServer, scenario, consumerNodeIds, null);
        } catch (Exception exception) {
            return consumerNodeIds.stream()
                    .map(consumerNodeId -> new MessageDeliveryResult(
                            "host-bridge-failed",
                            scenario.producerNodeId(),
                            consumerNodeId,
                            false,
                            "Host RocketMQ bridge failed: " + exception.getMessage()
                    ))
                    .toList();
        }
    }

    /**
     * 核心 produce/consume 邏輯——嵌入式和 HOST 共用。
     *
     * <p>流程：
     * <ol>
     *   <li>創建並啟動 DefaultMQProducer（唯一 group）</li>
     *   <li>若有 BrokerController（嵌入式路徑），執行 Spike 修復的 topic 創建流程：
     *       <ul>
     *         <li>調用 createTopicInSendMessageMethod 建 topic</li>
     *         <li>調用 registerBrokerAll 強制註冊</li>
     *         <li>調用 updateTopicRouteInfoFromNameServer 拉取路由</li>
     *       </ul>
     *   </li>
     *   <li>先發送所有消息（確保消息已入 broker）</li>
     *   <li>再創建並啟動 DefaultMQPushConsumer（FIRST_OFFSET，P3 修復：唯一 group 後綴）</li>
     *   <li>等待消費確認（CountDownLatch，超時 5-30 秒）</li>
     *   <li>返回每條消息的投遞結果（含是否被消費確認）</li>
     * </ol>
     *
     * @param namesrvAddr      NameServer 地址
     * @param scenario         消息場景
     * @param consumerNodeIds  consumer 節點 ID 列表
     * @param brokerController Broker 控制器（嵌入式路徑用於 topic 創建，HOST 路徑為 null）
     * @return 投遞結果列表
     * @throws Exception RocketMQ 客戶端操作失敗時拋出
     */
    private List<MessageDeliveryResult> doSimulate(
            String namesrvAddr,
            MessageScenario scenario,
            List<String> consumerNodeIds,
            BrokerController brokerController
    ) throws Exception {
        String producerGroup = "pseudo-producer-" + UUID.randomUUID();
        DefaultMQProducer producer = new DefaultMQProducer(producerGroup);
        producer.setNamesrvAddr(namesrvAddr);
        producer.start();

        try {
            // Spike 修復: 如果有 broker 控制器，直接創建 topic 並強制註冊到 NameServer
            if (brokerController != null) {
                log.info("Creating topic {} on broker controller", scenario.topic());
                brokerController.getTopicConfigManager()
                        .createTopicInSendMessageMethod(scenario.topic(), scenario.topic(), scenario.topic(), 4, 4);
                brokerController.registerBrokerAll(true, false, true);
                Thread.sleep(1000);
                producer.getDefaultMQProducerImpl().getmQClientFactory()
                        .updateTopicRouteInfoFromNameServer(scenario.topic());
                Thread.sleep(500);
            } else {
                log.warn("No broker controller available, relying on auto-topic-creation for {}", scenario.topic());
            }

            // 先發送所有消息，再啟動 consumer（用 FIRST_OFFSET 確保消費到剛發送的消息）
            // 時序修復: 原先 consumer 先 start 用 LAST_OFFSET，但 rebalance 未完成就發送消息導致錯過
            List<MessageDeliveryResult> deliveries = new ArrayList<>();
            for (int index = 0; index < scenario.messageCount(); index++) {
                String messageKey = scenario.topic() + "-" + index;
                String consumerNodeId = consumerNodeIds.get(index % consumerNodeIds.size());
                String payload = templateService.render(scenario.payloadTemplate(), scenario.topic(), index);

                Message message = new Message(scenario.topic(), null, messageKey, payload.getBytes(StandardCharsets.UTF_8));
                SendResult sendResult = producer.send(message);

                deliveries.add(new MessageDeliveryResult(
                        messageKey,
                        scenario.producerNodeId(),
                        consumerNodeId,
                        false,
                        "Sent to RocketMQ via " + namesrvAddr + ", msgId=" + sendResult.getMsgId()
                ));
            }

            // P3 修復: consumer group 追加唯一後綴，避免連續模擬時 "consumer group has been created already" 異常
            String uniqueConsumerGroup = scenario.consumerGroup() + "-" + UUID.randomUUID().toString().substring(0, 8);
            DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(uniqueConsumerGroup);
            consumer.setNamesrvAddr(namesrvAddr);
            // 從最早 offset 開始消費，確保能消費到剛發送的消息（與 Spike 測試一致）
            consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
            consumer.subscribe(scenario.topic(), "*");

            Map<String, Boolean> consumptionState = new ConcurrentHashMap<>();
            CountDownLatch latch = new CountDownLatch(scenario.messageCount());
            consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
                for (MessageExt message : messages) {
                    String key = message.getKeys();
                    if (key != null && consumptionState.putIfAbsent(key, true) == null) {
                        latch.countDown();
                    }
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });
            consumer.start();

            try {
                latch.await(Math.min(30, Math.max(5, scenario.messageCount())), TimeUnit.SECONDS);
                return deliveries.stream()
                        .map(delivery -> {
                            boolean consumed = Boolean.TRUE.equals(consumptionState.get(delivery.messageKey()));
                            String detail = consumed
                                    ? "Delivered through RocketMQ via " + namesrvAddr
                                    : "Sent to RocketMQ but did not confirm consumption before timeout";
                            return new MessageDeliveryResult(
                                    delivery.messageKey(),
                                    delivery.producerNodeId(),
                                    delivery.consumerNodeId(),
                                    consumed,
                                    detail
                            );
                        })
                        .toList();
            } finally {
                shutdownQuietly(consumer);
            }
        } finally {
            shutdownQuietly(producer);
        }
    }

    /**
     * 查找第一個運行中的 Broker 控制器。用於嵌入式路徑的 topic 創建。
     *
     * @return 第一個運行中的 BrokerController，無可用時返回 null
     */
    private BrokerController findRunningBroker() {
        for (ManagedNode node : nodeRegistry.brokers()) {
            try {
                BrokerController controller = runtime.brokerController(node.nodeId());
                log.info("Found running broker controller for node {}", node.nodeId());
                return controller;
            } catch (IllegalStateException ignored) {
                log.warn("Broker node {} not running or not a broker: {}", node.nodeId(), ignored.getMessage());
            }
        }
        log.warn("No running broker found among {} broker nodes", nodeRegistry.brokers().size());
        return null;
    }

    /**
     * 解析 HOST 類型 NameServer 地址。
     *
     * <p>P0 修復：非拋異常版本，供 simulate 預檢使用。從節點註冊表中查找
     * HOST 類型且角色為 nameserver 的節點地址。
     *
     * @return HOST NameServer 地址，無可用時返回 null
     */
    // P0 修復: 非拋異常版本，供 simulate 預檢使用
    private String resolveHostNameServersOrNull() {
        // 優先使用登記的 HOST NameServer 節點
        String hostNs = nodeRegistry.hostNodes().stream()
                .filter(node -> "nameserver".equals(node.role()))
                .map(ManagedNode::address)
                .findFirst()
                .orElse(null);
        if (hostNs != null) {
            return hostNs;
        }
        // 後備 1：使用運行時配置的外部 NameServer 地址（UI 配置）
        String runtimeNs = connectionConfig.resolvedNameServerString();
        if (!runtimeNs.isEmpty()) {
            return runtimeNs;
        }
        // 後備 2：使用啟動配置（環境變量）的外部 NameServer 地址
        List<String> configuredNameServers = rocketMqProperties.resolvedNameServers();
        return configuredNameServers.isEmpty() ? null : String.join(";", configuredNameServers);
    }

    /**
     * 安全關閉 Producer，忽略異常。
     *
     * @param producer 待關閉的 Producer
     */
    private static void shutdownQuietly(DefaultMQProducer producer) {
        try {
            producer.shutdown();
        } catch (Exception ignored) {
        }
    }

    /**
     * 安全關閉 Consumer，忽略異常。
     *
     * @param consumer 待關閉的 Consumer
     */
    private static void shutdownQuietly(DefaultMQPushConsumer consumer) {
        try {
            consumer.shutdown();
        } catch (Exception ignored) {
        }
    }
}
