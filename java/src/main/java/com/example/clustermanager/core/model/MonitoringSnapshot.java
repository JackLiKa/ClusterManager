package com.example.clustermanager.core.model;

import java.time.Instant;
import java.util.List;

/**
 * 监控快照，领域核心层的不可变值对象。
 *
 * <p>表示某时刻整个集群所有节点的指标快照，由 {@code IMonitoringGateway#loadMetrics}
 * 读取。该对象是遥测流式推送（{@code ClusterTelemetryPushService}）的核心数据载体。
 * 作为 record 不可变；{@link Instant} 与 {@link List} 应以不可变形式提供。
 */
public record MonitoringSnapshot(
        /** 快照采集的时间戳（UTC 瞬时）。 */
        Instant capturedAt,
        /** 全部节点的指标列表，元素为 {@link NodeMetrics}；不应为 {@code null}。 */
        List<NodeMetrics> nodes
) {
}
