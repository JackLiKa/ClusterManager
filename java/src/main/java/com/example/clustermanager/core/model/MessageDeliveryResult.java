package com.example.clustermanager.core.model;

/**
 * 单条消息投递结果，领域核心层的不可变值对象。
 *
 * <p>记录一次消息模拟中，从生产者节点到消费者节点单条投递的成败与详情。
 * 多个该对象聚合到 {@link MessageSimulationResult} 中返回。
 * 作为 record 不可变，适合在并发流式推送场景下安全传递。
 */
public record MessageDeliveryResult(
        /** 消息键（Message Key），用于唯一标识该条消息，便于追踪。 */
        String messageKey,
        /** 生产者节点标识，对应 {@link ClusterNode#nodeId()}。 */
        String producerNodeId,
        /** 消费者节点标识，对应 {@link ClusterNode#nodeId()}。 */
        String consumerNodeId,
        /** 投递是否成功：{@code true} 表示成功送达并被消费。 */
        boolean success,
        /** 投递详情描述；成功时可为摘要，失败时为错误原因。 */
        String detail
) {
}
