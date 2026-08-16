package com.example.clustermanager.infrastructure.rocketmq;

import com.example.clustermanager.application.service.MessageTemplateService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 真實 RocketMQ Admin 客戶端——通過 RocketMQ Client SDK 連接外部集群。
 *
 * <p>當 {@code cluster.rocketmq.name-servers} 配置了有效地址時啟用（替代 {@link MockRocketMqAdminClient}）。
 * 支持真實的 produce/consume 操作，用於與本地 MQ 項目通信。
 *
 * <p><b>節點/鏈路拉取</b>：當前使用簡化實現（返回空列表），因為 RocketMQ 5.3.3 的
 * Admin API（DefaultMQAdminExt）需要額外的認證配置。拓撲信息主要通過手工登記節點獲取。
 *
 * <p><b>消息收發</b>：使用 {@link DefaultMQProducer} 和 {@link DefaultMQPushConsumer}
 * 直接連接 NameServer 進行真實的 produce/consume。
 *
 * <p>被 {@link RocketMqAdminAdapter} 依賴。
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "cluster.rocketmq", name = "name-servers")
public class RealRocketMqAdminClient implements RocketMqAdminClient {

    private static final Logger log = LoggerFactory.getLogger(RealRocketMqAdminClient.class);

    private final RocketMqClusterProperties properties;
    private final RocketMqConnectionConfig connectionConfig;
    private final MessageTemplateService templateService;

    /**
     * 構造器注入配置和模板服務。
     *
     * @param properties       RocketMQ 集群配置（啟動時綁定）
     * @param connectionConfig 運行時連接配置（可變）
     * @param templateService  消息模板服務（用於 produce 時渲染 payload）
     */
    public RealRocketMqAdminClient(RocketMqClusterProperties properties,
                                   RocketMqConnectionConfig connectionConfig,
                                   MessageTemplateService templateService) {
        this.properties = properties;
        this.connectionConfig = connectionConfig;
        this.templateService = templateService;
    }

    @Override
    public List<RocketMqNodeSnapshot> fetchNodes(String clusterName) {
        // 簡化實現：節點主要通過手工登記獲取，Admin API 拓撲拉取暫不實現
        return List.of();
    }

    @Override
    public List<RocketMqLinkSnapshot> fetchLinks(String clusterName) {
        return List.of();
    }

    @Override
    public void invokeBrokerLifecycle(String nodeId, String operation) {
        log.warn("Lifecycle operation {} on node {} not supported for external clusters", operation, nodeId);
    }

    @Override
    public List<Map<String, Object>> fetchMessages(String topic, int messageCount) {
        // 使用 consume 方式拉取最近消息
        String namesrvAddr = connectionConfig.resolvedNameServerString();
        if (namesrvAddr.isEmpty()) {
            namesrvAddr = String.join(";", properties.resolvedNameServers());
        }
        if (namesrvAddr.isEmpty()) {
            return List.of();
        }
        return consumeMessages(namesrvAddr, topic, "admin-fetch-" + UUID.randomUUID().toString().substring(0, 8), messageCount);
    }

    @Override
    public List<Map<String, Object>> produceMessages(String namesrvAddr, String topic, int messageCount, String payloadTemplate) {
        List<Map<String, Object>> results = new ArrayList<>();
        DefaultMQProducer producer = null;
        try {
            String producerGroup = connectionConfig.getConsumerGroupPrefix() + "-producer-" + UUID.randomUUID().toString().substring(0, 8);
            producer = new DefaultMQProducer(producerGroup);
            producer.setNamesrvAddr(namesrvAddr);
            producer.setSendMsgTimeout(connectionConfig.getSendMsgTimeoutMs());
            producer.start();

            for (int index = 0; index < messageCount; index++) {
                String messageKey = topic + "-" + index;
                String payload = templateService.render(payloadTemplate, topic, index);
                try {
                    Message message = new Message(topic, null, messageKey, payload.getBytes(StandardCharsets.UTF_8));
                    SendResult sendResult = producer.send(message);
                    Map<String, Object> result = new HashMap<>();
                    result.put("messageKey", messageKey);
                    result.put("msgId", sendResult.getMsgId());
                    result.put("success", true);
                    result.put("detail", "Sent to " + topic + " via " + namesrvAddr + ", msgId=" + sendResult.getMsgId());
                    results.add(result);
                } catch (Exception e) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("messageKey", messageKey);
                    result.put("msgId", "");
                    result.put("success", false);
                    result.put("detail", "Failed to send: " + e.getMessage());
                    results.add(result);
                    log.error("Failed to send message {} to topic {}", index, topic, e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to start producer for topic {}", topic, e);
            for (int index = 0; index < messageCount; index++) {
                Map<String, Object> result = new HashMap<>();
                result.put("messageKey", topic + "-" + index);
                result.put("msgId", "");
                result.put("success", false);
                result.put("detail", "Producer startup failed: " + e.getMessage());
                results.add(result);
            }
        } finally {
            if (producer != null) {
                try { producer.shutdown(); } catch (Exception ignored) { }
            }
        }
        return results;
    }

    @Override
    public List<Map<String, Object>> consumeMessages(String namesrvAddr, String topic, String consumerGroup, int messageCount) {
        List<Map<String, Object>> results = new ArrayList<>();
        List<MessageExt> consumedMessages = new ArrayList<>();
        DefaultMQPushConsumer consumer = null;
        try {
            String uniqueGroup = connectionConfig.getConsumerGroupPrefix() + "-" + consumerGroup + "-" + UUID.randomUUID().toString().substring(0, 8);
            consumer = new DefaultMQPushConsumer(uniqueGroup);
            consumer.setNamesrvAddr(namesrvAddr);
            consumer.subscribe(topic, "*");
            consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
            consumer.setConsumeTimeout(connectionConfig.getConsumeTimeoutSeconds());

            consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
                consumedMessages.addAll(msgs);
                if (consumedMessages.size() >= messageCount) {
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });

            consumer.start();
            // 等待消費完成或超時
            long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(connectionConfig.getConsumeTimeoutSeconds());
            while (consumedMessages.size() < messageCount && System.currentTimeMillis() < deadline) {
                Thread.sleep(200);
            }

            for (MessageExt msg : consumedMessages) {
                Map<String, Object> result = new HashMap<>();
                result.put("messageKey", msg.getKeys() != null ? msg.getKeys() : "");
                result.put("msgId", msg.getMsgId());
                result.put("body", new String(msg.getBody(), StandardCharsets.UTF_8));
                result.put("success", true);
                result.put("detail", "Consumed from " + topic + ", msgId=" + msg.getMsgId());
                results.add(result);
            }

            if (results.isEmpty()) {
                Map<String, Object> result = new HashMap<>();
                result.put("messageKey", "");
                result.put("msgId", "");
                result.put("body", "");
                result.put("success", false);
                result.put("detail", "No messages consumed from " + topic + " within timeout");
                results.add(result);
            }
        } catch (Exception e) {
            log.error("Failed to consume from topic {}", topic, e);
            Map<String, Object> result = new HashMap<>();
            result.put("messageKey", "");
            result.put("msgId", "");
            result.put("body", "");
            result.put("success", false);
            result.put("detail", "Consume failed: " + e.getMessage());
            results.add(result);
        } finally {
            if (consumer != null) {
                try { consumer.shutdown(); } catch (Exception ignored) { }
            }
        }
        return results;
    }
}
