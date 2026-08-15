package com.example.clustermanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.topic.TopicValidator;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.namesrv.NamesrvController;
import org.apache.rocketmq.common.namesrv.NamesrvConfig;
import org.apache.rocketmq.broker.BrokerController;
import org.apache.rocketmq.common.BrokerConfig;
import org.apache.rocketmq.remoting.netty.NettyClientConfig;
import org.apache.rocketmq.remoting.netty.NettyServerConfig;
import org.apache.rocketmq.store.config.MessageStoreConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Phase 1 Spike: 验证 RocketMQ 4.9.8 能否在同一个 JVM 进程内启动
 * 1 个 NameServer + 1 个 Broker，并完成真实的 produce/consume。
 *
 * <p>此测试默认禁用（需要 -DrunEmbeddedRocketMqSpike=true 激活），
 * 因为嵌入式 RocketMQ 启动较重（端口、磁盘存储、Netty 线程），
 * 不应纳入常规 CI。Spike 通过后，后续 Phase 1 完整实现会把嵌入式
 * 启动封装为生产代码 + 轻量集成测试 fixture。
 *
 * <p><b>为何默认禁用：</b>嵌入式 RocketMQ 需要占用真实端口（NameServer、Broker、HA）、
 * 创建磁盘存储目录（commitlog）、启动 Netty 线程池，且对 Java 17 模块系统有
 * {@code --add-opens} 要求。这些副作用在常规 CI 中不可接受，因此仅在本地或
 * 专门环境通过系统属性手动激活。
 *
 * <p><b>Spike 的验证目标：</b>
 * <ul>
 *   <li>验证 {@code NamesrvController} 和 {@code BrokerController} 可直接 {@code new} 构造并启动，
 *       绕过 {@code BrokerStartup} 对 {@code ROCKETMQ_HOME} 的依赖。</li>
 *   <li>验证真实的 {@code DefaultMQProducer} 发送和 {@code DefaultMQPushConsumer} 消费链路端到端可用。</li>
 *   <li>验证 topic/tag/key/body 在 produce 与 consume 之间完整对齐。</li>
 *   <li>验证 {@code BrokerController.shutdown()} 能干净退出。</li>
 * </ul>
 *
 * <p><b>关键 API 发现（4.9.8 实测）：</b>
 * <ul>
 *   <li>NameServer 监听端口：{@code NettyServerConfig.setListenPort(int)}（不是 setBindPort）。</li>
 *   <li>Broker 控制器：{@code BrokerController} 4 参构造（BrokerConfig, NettyServerConfig, NettyClientConfig, MessageStoreConfig）。</li>
 *   <li>缩短注册间隔：{@code BrokerConfig.setRegisterNameServerPeriod(int)}（不是 setBrokerHeartbeatInterval）。</li>
 *   <li>自动建 topic 常量：{@code TopicValidator.AUTO_CREATE_TOPIC_KEY_TOPIC}（不是 MixAll.AUTO_CREATE_TOPIC_KEY_TOPIC）。</li>
 * </ul>
 *
 * <p><b>已知坑（务必规避）：</b>
 * <ol>
 *   <li>Broker 首次心跳不注册 topic——需手动调用 {@code createTopicInSendMessageMethod} + {@code registerBrokerAll} 强制注册。</li>
 *   <li>Java 17 模块反射——需 Surefire argLine 加 {@code --add-opens java.base/java.nio=ALL-UNNAMED} 等。</li>
 *   <li>{@code BrokerStartup} 不可用——依赖 ROCKETMQ_HOME 加载 logback，进程内嵌入会崩。</li>
 * </ol>
 */
@EnabledIfSystemProperty(named = "runEmbeddedRocketMqSpike", matches = "true") // 默认禁用，需 -DrunEmbeddedRocketMqSpike=true 激活，避免重型嵌入式 RocketMQ 纳入常规 CI
class EmbeddedRocketMqSpikeTest {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedRocketMqSpikeTest.class);

    private NamesrvController namesrvController;
    private BrokerController brokerController;
    private Path baseDir;
    private int namesrvPort;
    private int brokerPort;

    /**
     * 启动嵌入式 RocketMQ 集群（1 NameServer + 1 Broker）。
     *
     * <p>步骤：
     * <ol>
     *   <li>创建临时存储目录，分配空闲端口。</li>
     *   <li>构造并启动 {@code NamesrvController}，设置 Netty 监听端口。</li>
     *   <li>构造并启动 {@code BrokerController}，配置 broker 名称/集群、NameServer 地址、
     *       缩短注册间隔（确保 TBW102 快速注册）、存储路径。</li>
     *   <li>等待 3 秒，确保 Broker 完成向 NameServer 的注册和路由传播。</li>
     * </ol>
     */
    @BeforeEach
    void startEmbeddedCluster() throws Exception {
        baseDir = Files.createTempDirectory("rocketmq-spike-");
        namesrvPort = findFreePort();
        brokerPort = findFreePort();

        // 1. 启动 NameServer
        NettyServerConfig namesrvNettyServerConfig = new NettyServerConfig();
        namesrvNettyServerConfig.setListenPort(namesrvPort); // 关键 API: setListenPort 设置 NameServer 监听端口（不是 setBindPort）
        NamesrvConfig namesrvConfig = new NamesrvConfig();
        namesrvController = new NamesrvController(namesrvConfig, namesrvNettyServerConfig);
        namesrvController.initialize();
        namesrvController.start();
        log.info("Spike NameServer started on port {}", namesrvPort);

        // 2. 启动 Broker（直接构造 BrokerController，绕过 BrokerStartup 的 logback 初始化）
        BrokerConfig brokerConfig = new BrokerConfig();
        brokerConfig.setBrokerName("spike-broker");
        brokerConfig.setBrokerClusterName("spike-cluster");
        brokerConfig.setBrokerId(0);
        brokerConfig.setNamesrvAddr("127.0.0.1:" + namesrvPort);
        brokerConfig.setBrokerIP1("127.0.0.1");
        brokerConfig.setEnablePropertyFilter(true);
        // Spike: 缩短注册间隔，确保 TBW102（autoCreateTopic）快速注册到 NameServer
        brokerConfig.setRegisterNameServerPeriod(2000); // 关键 API: setRegisterNameServerPeriod（不是 setBrokerHeartbeatInterval），缩短心跳间隔加速 topic 注册

        NettyServerConfig brokerNettyServerConfig = new NettyServerConfig();
        brokerNettyServerConfig.setListenPort(brokerPort);

        NettyClientConfig nettyClientConfig = new NettyClientConfig();

        MessageStoreConfig messageStoreConfig = new MessageStoreConfig();
        messageStoreConfig.setStorePathRootDir(baseDir.resolve("store").toString());
        messageStoreConfig.setStorePathCommitLog(baseDir.resolve("store/commitlog").toString());
        messageStoreConfig.setHaListenPort(findFreePort()); // HA 端口也需分配，避免与默认端口冲突

        brokerController = new BrokerController(brokerConfig, brokerNettyServerConfig, nettyClientConfig, messageStoreConfig); // 4 参构造，绕过 BrokerStartup 的 ROCKETMQ_HOME 依赖
        brokerController.initialize();
        brokerController.start();
        log.info("Spike Broker started on port {}, storePath={}", brokerPort, baseDir);

        // 等待 Broker 注册到 NameServer 并完成路由传播
        Thread.sleep(3000);
    }

    /**
     * 关闭嵌入式 RocketMQ 集群，释放端口和线程资源。
     *
     * <p>先关闭 Broker 再关闭 NameServer，确保 Broker 能完成最后的卸载注册。
     * 注意：shutdown 后事务消息检查线程可能报 {@code RejectedExecutionException}，
     * 属无害噪声，不影响测试结果。
     */
    @AfterEach
    void stopEmbeddedCluster() {
        if (brokerController != null) {
            brokerController.shutdown();
            log.info("Spike Broker stopped");
        }
        if (namesrvController != null) {
            namesrvController.shutdown();
            log.info("Spike NameServer stopped");
        }
    }

    /**
     * 验证通过嵌入式 RocketMQ 集群完成真实的消息 produce 和 consume 全链路。
     *
     * <p>步骤：
     * <ol>
     *   <li>Producer 发送一条消息到 SpikeTestTopic——先手动在 BrokerController 上创建 topic
     *       并强制注册到 NameServer，绕过首次心跳不注册 topic 的时序问题。</li>
     *   <li>Consumer 订阅 topic 并消费消息，使用 CountDownLatch 等待异步消费完成。</li>
     * </ol>
     *
     * <p>预期结果：发送状态为 SEND_OK，消费到的消息 topic/tag/key/body 与发送时完全对齐。
     */
    @Test
    void shouldProduceAndConsumeMessageThroughEmbeddedCluster() throws Exception {
        String topic = "SpikeTestTopic";
        String producerGroup = "spike-producer-" + UUID.randomUUID(); // UUID 避免多次运行时 group 冲突
        String consumerGroup = "spike-consumer-" + UUID.randomUUID();
        String tag = "spike-tag";
        String messageKey = "spike-key-001";
        String payload = "hello embedded rocketmq";

        // 1. Producer 发送一条消息
        DefaultMQProducer producer = new DefaultMQProducer(producerGroup);
        producer.setNamesrvAddr("127.0.0.1:" + namesrvPort);
        producer.start();
        try {
            // Spike: 直接在 BrokerController 上创建 topic 并强制注册到 NameServer，绕过心跳时序问题
            brokerController.getTopicConfigManager()
                    .createTopicInSendMessageMethod(topic, topic, topic, 4, 4); // 手动建 topic，绕过首次心跳不注册的坑
            brokerController.registerBrokerAll(true, false, true); // 强制立即向 NameServer 注册 broker 路由信息
            log.info("Spike topic {} created and broker re-registered", topic);
            // 等待路由传播到 producer
            Thread.sleep(1000);
            producer.getDefaultMQProducerImpl().getmQClientFactory()
                    .updateTopicRouteInfoFromNameServer(topic); // 主动拉取路由，避免 producer 缓存过期
            Thread.sleep(500);

            Message message = new Message(topic, tag, messageKey, payload.getBytes());
            SendResult sendResult = producer.send(message);
            log.info("Spike send result: msgId={}, queueId={}, offset={}",
                    sendResult.getMsgId(),
                    sendResult.getMessageQueue().getQueueId(),
                    sendResult.getQueueOffset());
            assertThat(sendResult.getSendStatus()).isEqualTo(org.apache.rocketmq.client.producer.SendStatus.SEND_OK); // 发送状态必须为 SEND_OK

            // 2. Consumer 消费消息
            CountDownLatch latch = new CountDownLatch(1);
            java.util.concurrent.atomic.AtomicReference<MessageExt> received = new java.util.concurrent.atomic.AtomicReference<>();

            DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(consumerGroup);
            consumer.setNamesrvAddr("127.0.0.1:" + namesrvPort);
            consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET); // 从最早 offset 开始消费，确保能消费到刚发送的消息
            consumer.subscribe(topic, tag);
            consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
                for (MessageExt msg : messages) {
                    received.set(msg);
                    latch.countDown();
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });
            consumer.start();
            try {
                boolean consumed = latch.await(10, TimeUnit.SECONDS);
                assertThat(consumed).as("Message should be consumed within 10 seconds").isTrue(); // 10 秒内必须消费到消息，否则视为消费失败

                MessageExt msg = received.get();
                assertThat(msg).isNotNull();
                assertThat(msg.getTopic()).isEqualTo(topic); // topic 应与发送时一致
                assertThat(msg.getTags()).isEqualTo(tag);   // tag 应与发送时一致
                assertThat(msg.getKeys()).isEqualTo(messageKey); // key 应与发送时一致
                assertThat(new String(msg.getBody())).isEqualTo(payload); // body 应与发送时完全一致
                log.info("Spike consumed message: msgId={}, body={}", msg.getMsgId(), new String(msg.getBody()));
            } finally {
                consumer.shutdown();
            }
        } finally {
            producer.shutdown();
        }
    }

    /**
     * 查找一个空闲端口用于嵌入式 RocketMQ 组件监听。
     *
     * <p>通过打开 ServerSocket(0) 让操作系统分配空闲端口，然后立即关闭 socket。
     * 注意：存在端口竞态（关闭后可能被其他进程占用），但在测试环境中足够可靠。
     */
    private int findFreePort() throws Exception {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
