package com.example.clustermanager.core.port;

import com.example.clustermanager.core.model.ClusterRef;
import com.example.clustermanager.core.model.LogEntry;
import com.example.clustermanager.core.model.MonitoringSnapshot;
import java.util.List;

/**
 * 监控网关端口（六边形架构的出站端口）。
 *
 * <p>定义领域核心层对集群监控数据（指标快照与日志）读取能力的抽象。
 * infrastructure 层的适配器实现该端口，从真实或伪集群中采集监控数据。
 * 该端口不引入任何外部依赖。
 */
public interface IMonitoringGateway {

    /**
     * 加载指定集群的监控指标快照。
     *
     * @param clusterRef 目标集群引用
     * @return 监控快照，包含采集时间与全部节点指标列表
     * @throws IllegalStateException 若集群不可达或指标采集失败
     */
    MonitoringSnapshot loadMetrics(ClusterRef clusterRef);

    /**
     * 加载指定集群的日志条目。
     *
     * @param clusterRef 目标集群引用
     * @param nodeId     节点标识；为 {@code null} 时表示加载全集群日志
     * @param limit      返回日志条数的上限，应为正整数
     * @return 日志条目列表，按时间倒序排列；不应为 {@code null}
     * @throws IllegalArgumentException 若 limit 为非正数
     * @throws IllegalStateException    若集群不可达
     */
    List<LogEntry> loadLogs(ClusterRef clusterRef, String nodeId, int limit);
}
