package com.example.clustermanager.infrastructure.rocketmq;

/**
 * RocketMQ 链路快照 —— 从 RocketMQ Admin API 拉取的节点间网络链路原始数据。
 *
 * <p>本 record 属于 infrastructure/rocketmq 层的内部数据载体，用于在
 * {@link RocketMqAdminClient} 与 {@link RocketMqAdminAdapter} 之间传递
 * 从真实 RocketMQ 集群（或 Mock 实现）获取的网络拓扑链路信息。Adapter 会将其
 * 转换为领域模型 {@code NetworkLink} 后供上层使用。
 *
 * <p><b>当前状态</b>：REAL 模式暂时搁置，专注 PSEUDO 模式。当前唯一实现
 * {@link MockRocketMqAdminClient} 返回静态演示链路，待接入真实 Admin API
 * 时替换为实际网络探测结果。
 *
 * @param sourceNodeId 链路源节点 ID
 * @param targetNodeId 链路目标节点 ID
 * @param healthy      链路是否健康（true=正常，false=异常）
 * @param latencyMs    链路延迟（毫秒），用于前端展示网络质量
 */
public record RocketMqLinkSnapshot(
        String sourceNodeId,
        String targetNodeId,
        boolean healthy,
        double latencyMs
) {
}
