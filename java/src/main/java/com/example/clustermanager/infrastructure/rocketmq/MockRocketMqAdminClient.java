package com.example.clustermanager.infrastructure.rocketmq;

import com.example.clustermanager.core.model.NodeStatus;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Mock RocketMQ Admin 客户端 —— {@link RocketMqAdminClient} 的 Mock 实现，返回静态演示数据。
 *
 * <p>本类属于 infrastructure/rocketmq 层，是 {@link RocketMqAdminClient} 接口的当前唯一实现。
 * 在没有真实 RocketMQ 集群可连接的开发环境下，提供固定的演示拓扑（1 个 NameServer、
 * 1 个 Broker Master、1 个 Proxy）和链路数据，使 REAL 模式的适配器代码能够在
 * 无真实集群时正常编译运行和开发调试。
 *
 * <p><b>用途</b>：用于无真实集群时的本地开发与测试。所有方法返回静态或简单生成的数据，
 * 不涉及任何真实网络调用或 RocketMQ SDK 交互。
 *
 * <p><b>当前状态</b>：REAL 模式暂时搁置，专注 PSEUDO 模式。待恢复 REAL 模式时，
 * 应新增基于 {@code DefaultMQAdminExt} 的真实实现替换此类，或将其标注为
 * {@code @Profile("mock")} 仅在测试环境激活。
 *
 * @see RocketMqAdminClient
 * @see RocketMqAdminAdapter
 */
@Component
public class MockRocketMqAdminClient implements RocketMqAdminClient {

    /**
     * 返回静态演示节点列表，模拟一个包含 NameServer、Broker Master、Proxy 的最小集群拓扑。
     *
     * @param clusterName 集群名称（Mock 实现忽略此参数，返回固定数据）
     * @return 包含 3 个演示节点的快照列表
     */
    @Override
    public List<RocketMqNodeSnapshot> fetchNodes(String clusterName) {
        return List.of(
                new RocketMqNodeSnapshot(
                        "demo-ns-01",
                        "NameServer-01",
                        "192.168.50.78",
                        "192.168.50.78:9876",
                        NodeStatus.RUNNING,
                        Map.of("role", "nameserver", "source", "admin-api")
                ),
                new RocketMqNodeSnapshot(
                        "demo-broker-01",
                        "Broker-Master-01",
                        "169.254.11.77",
                        "169.254.11.77:10911",
                        NodeStatus.RUNNING,
                        Map.of("role", "broker-master", "source", "admin-api")
                ),
                new RocketMqNodeSnapshot(
                        "demo-proxy-01",
                        "Proxy-01",
                        "192.168.50.78",
                        "192.168.50.78:8080",
                        NodeStatus.RUNNING,
                        Map.of("role", "proxy", "source", "admin-api")
                )
        );
    }

    /**
     * 返回静态演示链路列表，模拟 NameServer 到 Broker 和 Proxy 的网络连接。
     *
     * @param clusterName 集群名称（Mock 实现忽略此参数，返回固定数据）
     * @return 包含 2 条演示链路的快照列表
     */
    @Override
    public List<RocketMqLinkSnapshot> fetchLinks(String clusterName) {
        return List.of(
                new RocketMqLinkSnapshot("demo-ns-01", "demo-broker-01", true, 2.4),
                new RocketMqLinkSnapshot("demo-ns-01", "demo-proxy-01", true, 2.9)
        );
    }

    /**
     * Mock 实现 —— 空操作，不执行任何真实节点生命周期控制。
     *
     * <p>待接入真实 RocketMQ 依赖时，应替换为 {@code DefaultMQAdminExt}
     * 或 {@code MQAdminExtImpl} 的实际调用。
     *
     * @param nodeId    目标节点 ID（Mock 实现忽略）
     * @param operation 操作类型（Mock 实现忽略）
     */
    @Override
    public void invokeBrokerLifecycle(String nodeId, String operation) {
        // Replace this mock with DefaultMQAdminExt or MQAdminExtImpl when wiring the real RocketMQ dependency.
    }

    /**
     * 生成指定数量的模拟消息，用于消息流探测演示。
     *
     * <p>每条消息包含 messageKey（topic-序号）、queueId（0/1 交替）、status（OK），
     * 数据结构模拟真实 RocketMQ 消息的基本字段。
     *
     * @param topic        目标 topic 名称（用于生成 messageKey 前缀）
     * @param messageCount 生成的消息数量
     * @return 模拟消息列表，每个元素为包含 messageKey、queueId、status 的 Map
     */
    @Override
    public List<Map<String, Object>> fetchMessages(String topic, int messageCount) {
        return java.util.stream.IntStream.range(0, messageCount)
                .mapToObj(index -> Map.<String, Object>of(
                        "messageKey", topic + "-" + index,
                        "queueId", index % 2,
                        "status", "OK"
                ))
                .toList();
    }

    @Override
    public List<Map<String, Object>> produceMessages(String namesrvAddr, String topic, int messageCount, String payloadTemplate) {
        return java.util.stream.IntStream.range(0, messageCount)
                .mapToObj(index -> Map.<String, Object>of(
                        "messageKey", topic + "-" + index,
                        "msgId", "MOCK-MSG-" + index,
                        "success", true,
                        "detail", "Mock produce to " + topic + " via " + namesrvAddr
                ))
                .toList();
    }

    @Override
    public List<Map<String, Object>> consumeMessages(String namesrvAddr, String topic, String consumerGroup, int messageCount) {
        return java.util.stream.IntStream.range(0, messageCount)
                .mapToObj(index -> Map.<String, Object>of(
                        "messageKey", topic + "-" + index,
                        "msgId", "MOCK-CONSUMED-" + index,
                        "body", "mock-body-" + index,
                        "success", true,
                        "detail", "Mock consume from " + topic + " via " + namesrvAddr
                ))
                .toList();
    }
}
