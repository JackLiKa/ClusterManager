package com.example.clustermanager.core.model;

import java.time.Instant;

/**
 * 日志条目，领域核心层的不可变值对象。
 *
 * <p>表示集群中某节点在某时刻产生的一条日志，由 {@code IMonitoringGateway#loadLogs}
 * 读取并聚合返回。{@link Instant} 为时间戳类型，天然线程安全且不可变。
 * 该对象常用于前端活动日志面板与审计追踪。
 */
public record LogEntry(
        /** 日志产生的时间戳（UTC 瞬时），精确到毫秒。 */
        Instant timestamp,
        /** 产生该日志的节点标识，对应 {@link ClusterNode#nodeId()}。 */
        String nodeId,
        /** 日志级别，如 {@code INFO}、{@code WARN}、{@code ERROR} 等。 */
        String level,
        /** 日志正文内容。 */
        String message
) {
}
