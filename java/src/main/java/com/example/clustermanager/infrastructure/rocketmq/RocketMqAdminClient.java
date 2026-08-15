package com.example.clustermanager.infrastructure.rocketmq;

import java.util.List;
import java.util.Map;

/**
 * RocketMQ Admin 客户端接口 —— 定义与真实 RocketMQ 集群交互的底层操作契约。
 *
 * <p>本接口属于 infrastructure/rocketmq 层，是 REAL 模式适配器
 * {@link RocketMqAdminAdapter} 所依赖的底层端口。它封装了从 RocketMQ
 * Admin API（如 {@code DefaultMQAdminExt}）拉取拓扑、操作节点生命周期、
 * 探测消息流等原始操作，使 Adapter 层无需关心具体的 Admin SDK 细节。
 *
 * <p><b>当前状态</b>：REAL 模式暂时搁置，专注 PSEUDO 模式。当前唯一实现为
 * {@link MockRocketMqAdminClient}，返回静态演示数据，用于无真实集群时的开发。
 * 待恢复 REAL 模式时，新增实现类对接 {@code DefaultMQAdminExt} 即可。
 *
 * @see RocketMqAdminAdapter
 * @see MockRocketMqAdminClient
 */
public interface RocketMqAdminClient {

    /**
     * 拉取指定集群下的所有节点快照。
     *
     * @param clusterName 集群名称（对应 RocketMQ Dashboard 中的集群标识）
     * @return 节点快照列表，每个元素包含节点 ID、显示名、地址、状态、标签等信息
     */
    List<RocketMqNodeSnapshot> fetchNodes(String clusterName);

    /**
     * 拉取指定集群下的所有网络链路快照。
     *
     * @param clusterName 集群名称
     * @return 链路快照列表，每个元素包含源节点、目标节点、健康状态、延迟等信息
     */
    List<RocketMqLinkSnapshot> fetchLinks(String clusterName);

    /**
     * 对指定节点执行生命周期操作（如 START / STOP / RESTART）。
     *
     * <p>此方法为 void 返回，调用方无法直接获知操作是否成功。
     * Adapter 层通过 {@link RocketMqAdminAdapter#tryInvokeNodeOperation}
     * 包装此方法以捕获异常并返回布尔结果。
     *
     * @param nodeId    目标节点 ID
     * @param operation 操作类型名称（如 "START"、"STOP"、"RESTART"）
     * @throws RuntimeException 当 Admin API 调用失败时抛出
     */
    void invokeBrokerLifecycle(String nodeId, String operation);

    /**
     * 从指定 topic 拉取最近的消息列表，用于消息流探测。
     *
     * @param topic        目标 topic 名称
     * @param messageCount 拉取的消息数量上限
     * @return 消息列表，每个元素为键值对 Map，包含 messageKey、queueId、status 等字段
     */
    List<Map<String, Object>> fetchMessages(String topic, int messageCount);

    /**
     * 向指定 topic 发送消息——真實 produce 操作。
     *
     * @param namesrvAddr  NameServer 地址（host:port）
     * @param topic        目标 topic
     * @param messageCount 消息数量
     * @param payloadTemplate 消息模板（可含占位符）
     * @return 發送結果列表，每個元素包含 messageKey、msgId、success、detail
     */
    List<Map<String, Object>> produceMessages(String namesrvAddr, String topic, int messageCount, String payloadTemplate);

    /**
     * 从指定 topic 消费消息——真實 consume 操作。
     *
     * @param namesrvAddr   NameServer 地址
     * @param topic         目标 topic
     * @param consumerGroup 消费者组名称
     * @param messageCount  期望消费的消息数量
     * @return 消费結果列表，每個元素包含 messageKey、msgId、body、success、detail
     */
    List<Map<String, Object>> consumeMessages(String namesrvAddr, String topic, String consumerGroup, int messageCount);
}
