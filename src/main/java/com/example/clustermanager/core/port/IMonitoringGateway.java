package com.example.clustermanager.core.port;

import com.example.clustermanager.core.model.ClusterRef;
import com.example.clustermanager.core.model.LogEntry;
import com.example.clustermanager.core.model.MonitoringSnapshot;
import java.util.List;

public interface IMonitoringGateway {

    MonitoringSnapshot loadMetrics(ClusterRef clusterRef);

    List<LogEntry> loadLogs(ClusterRef clusterRef, String nodeId, int limit);
}
