package com.example.clustermanager.infrastructure.rocketmq;

import com.example.clustermanager.core.model.ClusterRef;
import com.example.clustermanager.core.model.ClusterNode;
import com.example.clustermanager.core.model.ClusterTopology;
import com.example.clustermanager.core.model.LogEntry;
import com.example.clustermanager.core.model.MessageScenario;
import com.example.clustermanager.core.model.MessageSimulationResult;
import com.example.clustermanager.core.model.MiddlewareType;
import com.example.clustermanager.core.model.MonitoringSnapshot;
import com.example.clustermanager.core.model.NodeMetrics;
import com.example.clustermanager.core.model.OperationResult;
import com.example.clustermanager.core.model.OperationType;
import com.example.clustermanager.core.model.ProviderDescriptor;
import com.example.clustermanager.core.model.ServiceRegistration;
import com.example.clustermanager.core.port.IClusterProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

@Component
public class RocketMqClusterProvider implements IClusterProvider {

    private static final ProviderDescriptor DESCRIPTOR = new ProviderDescriptor(
            "rocketmq-admin",
            "RocketMQ Admin Provider",
            com.example.clustermanager.core.model.ClusterMode.REAL,
            MiddlewareType.ROCKETMQ
    );

    private final RocketMqClusterProperties properties;
    private final RocketMqAdminAdapter adminAdapter;
    private final Map<String, ClusterNode> manualNodes = new ConcurrentHashMap<>();
    private final Deque<LogEntry> auditLogs = new ConcurrentLinkedDeque<>();

    public RocketMqClusterProvider(RocketMqClusterProperties properties, RocketMqAdminAdapter adminAdapter) {
        this.properties = properties;
        this.adminAdapter = adminAdapter;
    }

    @Override
    public ProviderDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ClusterTopology loadTopology(ClusterRef clusterRef) {
        List<ClusterNode> nodes = new ArrayList<>(adminAdapter.loadNodes(properties.dashboardName()));
        nodes.addAll(manualNodes.values());
        return new ClusterTopology(
                clusterRef,
                nodes,
                adminAdapter.loadLinks(properties.dashboardName(), nodes),
                properties.nameServers().isEmpty() ? "unavailable" : properties.nameServers().get(0)
        );
    }

    @Override
    public OperationResult operateNode(ClusterRef clusterRef, String nodeId, OperationType operationType) {
        ClusterNode manualNode = manualNodes.get(nodeId);
        if (manualNode != null) {
            ClusterNode updated = new ClusterNode(
                    manualNode.nodeId(),
                    manualNode.displayName(),
                    manualNode.hostName(),
                    manualNode.virtualIp(),
                    switch (operationType) {
                        case START, RESTART -> com.example.clustermanager.core.model.NodeStatus.RUNNING;
                        case STOP -> com.example.clustermanager.core.model.NodeStatus.STOPPED;
                    },
                    manualNode.labels()
            );
            manualNodes.put(nodeId, updated);
            return new OperationResult(nodeId, operationType, true, "Manual RocketMQ service updated");
        }
        adminAdapter.invokeNodeOperation(nodeId, operationType.name());
        return new OperationResult(nodeId, operationType, true, "Delegated to RocketMQ Admin API");
    }

    @Override
    public OperationResult registerService(ClusterRef clusterRef, ServiceRegistration registration) {
        if (manualNodes.containsKey(registration.nodeId())) {
            throw new IllegalArgumentException("Node already exists: " + registration.nodeId());
        }
        Map<String, String> labels = new java.util.HashMap<>(registration.labels() == null ? Map.of() : registration.labels());
        labels.putIfAbsent("role", registration.role());
        labels.put("source", "manual");
        labels.put("nameserver", registration.address());
        manualNodes.put(registration.nodeId(), new ClusterNode(
                registration.nodeId(),
                registration.displayName(),
                registration.hostName(),
                "%s:%d".formatted(registration.address(), registration.port()),
                com.example.clustermanager.core.model.NodeStatus.RUNNING,
                Map.copyOf(labels)
        ));
        appendAuditLog(registration.nodeId(), "INFO", "Manual RocketMQ service registered at %s:%d".formatted(
                registration.address(),
                registration.port()
        ));
        return new OperationResult(registration.nodeId(), OperationType.START, true, "RocketMQ manual service registered");
    }

    @Override
    public OperationResult deleteService(ClusterRef clusterRef, String nodeId) {
        ClusterNode removed = manualNodes.remove(nodeId);
        if (removed == null) {
            throw new IllegalArgumentException("Manual service not found: " + nodeId);
        }
        appendAuditLog(nodeId, "WARN", "Manual RocketMQ service deleted from cluster " + clusterRef.clusterId());
        return new OperationResult(nodeId, OperationType.STOP, true, "RocketMQ manual service deleted");
    }

    @Override
    public MessageSimulationResult simulate(ClusterRef clusterRef, MessageScenario scenario) {
        return new MessageSimulationResult(
                Instant.now(),
                adminAdapter.probeMessageFlow(
                        scenario.topic(),
                        scenario.producerNodeId(),
                        scenario.consumerNodeIds(),
                        scenario.messageCount(),
                        manualNodes.values().stream().toList()
                )
        );
    }

    @Override
    public MonitoringSnapshot loadMetrics(ClusterRef clusterRef) {
        List<ClusterNode> nodes = new ArrayList<>(adminAdapter.loadNodes(properties.dashboardName()));
        nodes.addAll(manualNodes.values());
        List<NodeMetrics> metrics = nodes.stream()
                .map(node -> new NodeMetrics(
                        node.nodeId(),
                        ThreadLocalRandom.current().nextDouble(10, 75),
                        ThreadLocalRandom.current().nextDouble(20, 80),
                        ThreadLocalRandom.current().nextDouble(2048, 16384),
                        ThreadLocalRandom.current().nextDouble(2048, 16384)
                ))
                .toList();
        return new MonitoringSnapshot(Instant.now(), metrics);
    }

    @Override
    public List<LogEntry> loadLogs(ClusterRef clusterRef, String nodeId, int limit) {
        List<ClusterNode> nodes = new ArrayList<>(adminAdapter.loadNodes(properties.dashboardName()));
        nodes.addAll(manualNodes.values());
        List<LogEntry> mergedLogs = new ArrayList<>(auditLogs.stream()
                .filter(entry -> nodeId == null || entry.nodeId().equals(nodeId))
                .toList());
        mergedLogs.addAll(nodes.stream()
                .filter(node -> nodeId == null || node.nodeId().equals(nodeId))
                .map(node -> new LogEntry(
                        Instant.now(),
                        node.nodeId(),
                        "INFO",
                        "Admin API observed %s on %s".formatted(node.status(), node.hostName())
                ))
                .toList());
        mergedLogs.sort(Comparator.comparing(LogEntry::timestamp).reversed());
        return mergedLogs.stream()
                .limit(Math.max(1, limit))
                .toList();
    }

    private void appendAuditLog(String nodeId, String level, String message) {
        auditLogs.addFirst(new LogEntry(Instant.now(), nodeId, level, message));
        while (auditLogs.size() > 200) {
            auditLogs.pollLast();
        }
    }
}
