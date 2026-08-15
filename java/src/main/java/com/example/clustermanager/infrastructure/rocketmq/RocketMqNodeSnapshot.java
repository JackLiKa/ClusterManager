package com.example.clustermanager.infrastructure.rocketmq;

import com.example.clustermanager.core.model.NodeStatus;
import java.util.Map;

/**
 * RocketMQ 节点快照 —— 从 RocketMQ Admin API 拉取的单个节点原始数据。
 *
 * <p>本 record 属于 infrastructure/rocketmq 层的内部数据载体，用于在
 * {@link RocketMqAdminClient} 与 {@link RocketMqAdminAdapter} 之间传递
 * 从真实 RocketMQ 集群（或 Mock 实现）获取的节点信息。Adapter 会将其
 * 转换为领域模型 {@code ClusterNode} 后供上层使用。
 *
 * <p><b>当前状态</b>：REAL 模式暂时搁置，专注 PSEUDO 模式。当前唯一实现
 * {@link MockRocketMqAdminClient} 返回静态演示数据，待接入真实 Admin API
 * 时替换为 {@code DefaultMQAdminExt} 调用结果。
 *
 * @param nodeId         节点唯一标识（如 broker name 或自定义 id）
 * @param displayName    节点显示名称，用于前端展示
 * @param hostName       节点主机名或 IP 地址
 * @param exposedAddress 节点对外暴露地址（host:port 格式）
 * @param status         节点运行状态（RUNNING / STOPPED 等）
 * @param labels         节点标签键值对，包含 role（角色）、source（来源）等元数据
 */
public record RocketMqNodeSnapshot(
        String nodeId,
        String displayName,
        String hostName,
        String exposedAddress,
        NodeStatus status,
        Map<String, String> labels
) {
}
