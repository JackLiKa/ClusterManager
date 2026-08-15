package com.example.clustermanager.core.model;

/**
 * 节点指标，领域核心层的不可变值对象。
 *
 * <p>记录单个节点在某时刻的资源使用指标（CPU、内存、网络吞吐），
 * 由 {@code IMonitoringGateway#loadMetrics} 聚合到 {@link MonitoringSnapshot} 中返回。
 * 作为 record 不可变，所有字段为基本类型或不可变字符串引用，
 * 适合在遥测流式推送场景下安全传递。
 */
public record NodeMetrics(
        /** 节点唯一标识，对应 {@link ClusterNode#nodeId()}。 */
        String nodeId,
        /** CPU 使用率，取值范围 0.0~100.0（百分比）。 */
        double cpuUsage,
        /** 内存使用率，取值范围 0.0~100.0（百分比）。 */
        double memoryUsage,
        /** 网络入站速率，单位字节/秒；非负。 */
        double networkInBytesPerSecond,
        /** 网络出站速率，单位字节/秒；非负。 */
        double networkOutBytesPerSecond
) {
}
