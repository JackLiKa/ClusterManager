package com.example.clustermanager.core.model;

/**
 * 网络链路，领域核心层的不可变值对象。
 *
 * <p>描述集群拓扑中两个节点之间的网络连接关系，包括源节点、目标节点、
 * 健康状态、链路类型与延迟。由 {@code ITopologyReader} 聚合到 {@link ClusterTopology}
 * 中返回，供前端拓扑图渲染链路与延迟标注。
 * 作为 record 不可变，所有字段为基本类型或不可变字符串引用。
 */
public record NetworkLink(
        /** 链路源节点标识，对应 {@link ClusterNode#nodeId()}。 */
        String sourceNodeId,
        /** 链路目标节点标识，对应 {@link ClusterNode#nodeId()}。 */
        String targetNodeId,
        /** 链路是否健康：{@code true} 表示连通正常。 */
        boolean healthy,
        /** 链路类型描述，如 {@code BROKER->NAMESRV}、{@code MASTER->SLAVE} 等。 */
        String linkType,
        /** 链路单向延迟，单位毫秒；非负浮点数。 */
        double latencyMs
) {
}
