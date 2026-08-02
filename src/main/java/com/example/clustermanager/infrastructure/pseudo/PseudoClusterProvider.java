package com.example.clustermanager.infrastructure.pseudo;

import com.example.clustermanager.core.model.ClusterNode;
import com.example.clustermanager.core.model.ClusterRef;
import com.example.clustermanager.core.model.ClusterTopology;
import com.example.clustermanager.core.model.LogEntry;
import com.example.clustermanager.core.model.MessageDeliveryResult;
import com.example.clustermanager.core.model.MessageScenario;
import com.example.clustermanager.core.model.MessageSimulationResult;
import com.example.clustermanager.core.model.MiddlewareType;
import com.example.clustermanager.core.model.MonitoringSnapshot;
import com.example.clustermanager.core.model.NetworkLink;
import com.example.clustermanager.core.model.NodeMetrics;
import com.example.clustermanager.core.model.NodeStatus;
import com.example.clustermanager.core.model.OperationResult;
import com.example.clustermanager.core.model.OperationType;
import com.example.clustermanager.core.model.ProviderDescriptor;
import com.example.clustermanager.core.model.ServiceRegistration;
import com.example.clustermanager.core.port.IClusterProvider;
import com.example.clustermanager.core.port.IVirtualNetwork;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

@Component
public class PseudoClusterProvider implements IClusterProvider {

    private static final ProviderDescriptor DESCRIPTOR = new ProviderDescriptor(
            "pseudo-rocketmq",
            "Pseudo RocketMQ Cluster",
            com.example.clustermanager.core.model.ClusterMode.PSEUDO,
            MiddlewareType.ROCKETMQ
    );

    private final IVirtualNetwork virtualNetwork;
    private final PseudoClusterProperties properties;
    private final PseudoNodeRuntime nodeRuntime;
    private final PseudoRocketMqBridge rocketMqBridge;
    private final Map<String, ManagedPseudoNode> nodes = new ConcurrentHashMap<>();
    private final Deque<LogEntry> auditLogs = new ConcurrentLinkedDeque<>();
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public PseudoClusterProvider(
            IVirtualNetwork virtualNetwork,
            PseudoClusterProperties properties,
            PseudoNodeRuntime nodeRuntime,
            PseudoRocketMqBridge rocketMqBridge
    ) {
        this.virtualNetwork = virtualNetwork;
        this.properties = properties;
        this.nodeRuntime = nodeRuntime;
        this.rocketMqBridge = rocketMqBridge;
    }

    @Override
    public ProviderDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ClusterTopology loadTopology(ClusterRef clusterRef) {
        initializeIfNecessary(clusterRef.clusterId());
        reconcileAutoStart();
        List<ClusterNode> topologyNodes = orderedNodes().stream()
                .map(ManagedPseudoNode::toNode)
                .toList();
        List<NetworkLink> links = buildLinks(topologyNodes);
        return new ClusterTopology(clusterRef, topologyNodes, links, activeVip());
    }

    @Override
    public OperationResult operateNode(ClusterRef clusterRef, String nodeId, OperationType operationType) {
        initializeIfNecessary(clusterRef.clusterId());
        ManagedPseudoNode node = requireNode(nodeId);
        if (node.hostBound()) {
            node.transition(switch (operationType) {
                case START, RESTART -> NodeStatus.RUNNING;
                case STOP -> NodeStatus.STOPPED;
            });
            appendAuditLog(nodeId, "INFO", "Host-bound pseudo node marked as " + node.status());
            return new OperationResult(nodeId, operationType, true, "Host-bound pseudo node transitioned to " + node.status());
        }
        switch (operationType) {
            case START -> nodeRuntime.start(node.spec());
            case STOP -> nodeRuntime.stop(node.nodeId());
            case RESTART -> nodeRuntime.restart(node.spec());
        }
        node.transition(nodeRuntime.status(node.spec()));
        return new OperationResult(nodeId, operationType, true, "Pseudo node transitioned to " + node.status());
    }

    @Override
    public OperationResult registerService(ClusterRef clusterRef, ServiceRegistration registration) {
        initializeIfNecessary(clusterRef.clusterId());
        validatePseudoRegistration(registration);
        if (nodes.containsKey(registration.nodeId())) {
            throw new IllegalArgumentException("Node already exists: " + registration.nodeId());
        }
        ManagedPseudoNode node = new ManagedPseudoNode(
                registration.nodeId(),
                registration.displayName(),
                registration.hostName(),
                registration.role(),
                registration.port(),
                true,
                resolvePseudoNodeKind(registration),
                NodeStatus.STOPPED
        );
        if (node.hostBound()) {
            node.bindAddress("%s:%d".formatted(registration.address(), registration.port()));
            node.transition(NodeStatus.RUNNING);
            nodes.put(node.nodeId(), node);
        } else {
            String virtualIp = virtualNetwork.attachNode(clusterRef.clusterId(), node.nodeId(), registration.address()).virtualIp();
            virtualNetwork.isolateNode(clusterRef.clusterId(), node.nodeId());
            node.bindAddress(virtualIp);
            nodes.put(node.nodeId(), node);
            nodeRuntime.ensurePrepared(List.of(node.spec()));
            if (properties.resolvedAutoStart()) {
                nodeRuntime.start(node.spec());
                node.transition(nodeRuntime.status(node.spec()));
            }
        }
        appendAuditLog(node.nodeId(), "INFO", "Manual pseudo service registered at %s:%d".formatted(
                registration.address(),
                registration.port()
        ));
        return new OperationResult(node.nodeId(), OperationType.START, true, "Pseudo service registered: " + node.address());
    }

    @Override
    public OperationResult deleteService(ClusterRef clusterRef, String nodeId) {
        initializeIfNecessary(clusterRef.clusterId());
        ManagedPseudoNode node = requireNode(nodeId);
        if (!node.managed()) {
            throw new IllegalArgumentException("Seeded pseudo services cannot be deleted: " + nodeId);
        }
        if (!node.hostBound()) {
            nodeRuntime.stop(nodeId);
            virtualNetwork.detachNode(clusterRef.clusterId(), nodeId);
        }
        nodes.remove(nodeId);
        appendAuditLog(nodeId, "WARN", "Manual pseudo service deleted from cluster " + clusterRef.clusterId());
        return new OperationResult(nodeId, OperationType.STOP, true, "Pseudo service deleted");
    }

    @Override
    public MessageSimulationResult simulate(ClusterRef clusterRef, MessageScenario scenario) {
        initializeIfNecessary(clusterRef.clusterId());
        reconcileAutoStart();
        List<String> consumerNodeIds = scenario.consumerNodeIds() == null || scenario.consumerNodeIds().isEmpty()
                ? orderedNodes().stream().filter(node -> node.role().contains("broker")).map(ManagedPseudoNode::nodeId).toList()
                : scenario.consumerNodeIds();
        List<MessageDeliveryResult> deliveries = new ArrayList<>();
        ManagedPseudoNode producerNode = requireNode(scenario.producerNodeId());
        boolean hostBackedFlow = producerNode.hostBound() || consumerNodeIds.stream().map(this::requireNode).anyMatch(ManagedPseudoNode::hostBound);
        if (hostBackedFlow) {
            // P0 修复: 预检 HOST NameServer 是否存在，缺失时返回失败结果而非抛 IllegalStateException 导致 HTTP 500
            String hostNameServer = resolveHostNameServersOrNull();
            if (hostNameServer == null) {
                List<MessageDeliveryResult> failedDeliveries = consumerNodeIds.stream()
                        .map(consumerNodeId -> new MessageDeliveryResult(
                                "host-ns-missing",
                                scenario.producerNodeId(),
                                consumerNodeId,
                                false,
                                "Host RocketMQ bridge disabled: no HOST nameserver registered in pseudo cluster"
                        ))
                        .toList();
                return new MessageSimulationResult(Instant.now(), failedDeliveries);
            }
            return new MessageSimulationResult(
                    Instant.now(),
                    rocketMqBridge.simulateWithHostRocketMq(hostNameServer, scenario, consumerNodeIds)
            );
        }
        for (int index = 0; index < scenario.messageCount(); index++) {
            String consumerNodeId = consumerNodeIds.get(index % consumerNodeIds.size());
            ManagedPseudoNode consumerNode = requireNode(consumerNodeId);
            String messageKey = scenario.topic() + "-" + index;
            boolean delivered = nodeRuntime.deliverMessage(
                    consumerNode.spec(),
                    scenario.topic(),
                    messageKey,
                    scenario.payloadTemplate() == null ? "{}" : scenario.payloadTemplate()
            );
            boolean consumed = delivered && nodeRuntime.consumeMessage(
                    consumerNode.spec(),
                    scenario.topic(),
                    scenario.consumerGroup()
            );
            deliveries.add(new MessageDeliveryResult(
                    messageKey,
                    scenario.producerNodeId(),
                    consumerNodeId,
                    consumed,
                    consumed
                            ? "Delivered and consumed by local pseudo node on 127.0.0.1:%d".formatted(consumerNode.port())
                            : "Message flow failed because pseudo node is not running or unhealthy"
            ));
        }
        return new MessageSimulationResult(Instant.now(), deliveries);
    }

    @Override
    public MonitoringSnapshot loadMetrics(ClusterRef clusterRef) {
        initializeIfNecessary(clusterRef.clusterId());
        reconcileAutoStart();
        List<NodeMetrics> metrics = orderedNodes().stream()
                .map(node -> {
                    if (node.hostBound()) {
                        return new NodeMetrics(node.nodeId(), 0, 0, 0, 0);
                    }
                    node.transition(nodeRuntime.status(node.spec()));
                    return nodeRuntime.metrics(node.spec());
                })
                .toList();
        return new MonitoringSnapshot(Instant.now(), metrics);
    }

    @Override
    public List<LogEntry> loadLogs(ClusterRef clusterRef, String nodeId, int limit) {
        initializeIfNecessary(clusterRef.clusterId());
        reconcileAutoStart();
        List<LogEntry> mergedLogs = new ArrayList<>(auditLogs.stream()
                .filter(entry -> nodeId == null || entry.nodeId().equals(nodeId))
                .toList());
        mergedLogs.addAll(orderedNodes().stream()
                .filter(node -> nodeId == null || node.nodeId().equals(nodeId))
                .flatMap(node -> {
                    if (node.hostBound()) {
                        return java.util.stream.Stream.of(new LogEntry(
                                Instant.now(),
                                node.nodeId(),
                                "INFO",
                                "Host-bound pseudo node is attached to the topology and does not expose pseudo runtime logs."
                        ));
                    }
                    return nodeRuntime.logs(node.spec(), limit).stream();
                })
                .toList());
        mergedLogs.sort(Comparator.comparing(LogEntry::timestamp).reversed());
        return mergedLogs.stream()
                .limit(Math.max(1, limit))
                .toList();
    }

    private void initializeIfNecessary(String clusterId) {
        if (initialized.compareAndSet(false, true)) {
            virtualNetwork.ensureSegment(clusterId, properties.tapDeviceName(), properties.cidr());
            seedNode("rmq-ns-01", "NameServer-01", "ns01.local", "nameserver", 9876);
            seedNode("rmq-broker-m-01", "Broker-Master-01", "broker-master-01.local", "broker-master", 10911);
            seedNode("rmq-broker-s-01", "Broker-Slave-01", "broker-slave-01.local", "broker-slave", 10921);
            nodeRuntime.ensurePrepared(orderedNodes().stream().map(ManagedPseudoNode::spec).toList());
            if (properties.resolvedAutoStart()) {
                orderedNodes().forEach(node -> {
                    nodeRuntime.start(node.spec());
                    node.transition(nodeRuntime.status(node.spec()));
                });
            }
        }
    }

    private void seedNode(String nodeId, String displayName, String hostName, String role, int port) {
        // P2 修复: 种子节点初始状态改为 STOPPED，与默认 auto-start=false 语义一致
        // 原代码为 NodeStatus.STARTING，会在首次 loadMetrics 时被 nodeRuntime.status() 覆盖为 STOPPED，导致前端状态闪烁
        // ManagedPseudoNode node = new ManagedPseudoNode(nodeId, displayName, hostName, role, port, false, "VIRTUAL", NodeStatus.STARTING);
        ManagedPseudoNode node = new ManagedPseudoNode(nodeId, displayName, hostName, role, port, false, "VIRTUAL", NodeStatus.STOPPED);
        String virtualIp = virtualNetwork.attachNode(properties.clusterId(), nodeId).virtualIp();
        virtualNetwork.isolateNode(properties.clusterId(), nodeId);
        node.bindAddress(virtualIp);
        nodes.put(nodeId, node);
    }

    private ManagedPseudoNode requireNode(String nodeId) {
        ManagedPseudoNode node = nodes.get(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("Node not found: " + nodeId);
        }
        return node;
    }

    private List<ManagedPseudoNode> orderedNodes() {
        return nodes.values().stream()
                .sorted(Comparator.comparing(ManagedPseudoNode::nodeId))
                .toList();
    }

    private List<NetworkLink> buildLinks(List<ClusterNode> topologyNodes) {
        List<NetworkLink> links = new ArrayList<>();
        for (int index = 0; index < topologyNodes.size() - 1; index++) {
            ClusterNode source = topologyNodes.get(index);
            ClusterNode target = topologyNodes.get(index + 1);
            boolean healthy = source.status() == NodeStatus.RUNNING && target.status() == NodeStatus.RUNNING;
            links.add(new NetworkLink(source.nodeId(), target.nodeId(), healthy, "tap-overlay", healthy ? 1.2 : 99.0));
        }
        return links;
    }

    private String activeVip() {
        return orderedNodes().stream()
                .filter(node -> node.role().startsWith("broker"))
                .filter(node -> node.status() == NodeStatus.RUNNING)
                .map(ManagedPseudoNode::address)
                .findFirst()
                .orElse("unavailable");
    }

    private void validatePseudoRegistration(ServiceRegistration registration) {
        String nodeKind = resolvePseudoNodeKind(registration);
        if ("HOST".equals(nodeKind)) {
            if (registration.port() == null || registration.port() <= 0) {
                throw new IllegalArgumentException("Host pseudo node requires a valid port");
            }
            return;
        }
        if (!registration.address().matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
            throw new IllegalArgumentException("Pseudo virtual nodes require a virtual IPv4 address");
        }
        if (orderedNodes().stream().anyMatch(node -> registration.address().equals(node.address()))) {
            throw new IllegalArgumentException("Virtual IP already exists: " + registration.address());
        }
        if (registration.port() == null || registration.port() <= 0) {
            throw new IllegalArgumentException("Port must be greater than 0");
        }
    }

    private void reconcileAutoStart() {
        if (!properties.resolvedAutoStart()) {
            return;
        }
        orderedNodes().forEach(node -> {
            if (node.hostBound()) {
                return;
            }
            NodeStatus status = nodeRuntime.status(node.spec());
            node.transition(status);
            if (status == NodeStatus.STOPPED) {
                try {
                    nodeRuntime.start(node.spec());
                    node.transition(nodeRuntime.status(node.spec()));
                } catch (Exception exception) {
                    node.transition(NodeStatus.FAILED);
                }
            }
        });
    }

    private void appendAuditLog(String nodeId, String level, String message) {
        auditLogs.addFirst(new LogEntry(Instant.now(), nodeId, level, message));
        while (auditLogs.size() > 200) {
            auditLogs.pollLast();
        }
    }

    private String resolvePseudoNodeKind(ServiceRegistration registration) {
        return registration.labels() != null && "HOST".equalsIgnoreCase(registration.labels().get("nodeKind"))
                ? "HOST"
                : "VIRTUAL";
    }

    private String resolveHostNameServers() {
        return orderedNodes().stream()
                .filter(ManagedPseudoNode::hostBound)
                .filter(node -> "nameserver".equals(node.role()))
                .map(ManagedPseudoNode::address)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Host-backed message simulation requires at least one HOST nameserver node in the pseudo cluster"
                ));
    }

    // P0 修复: 新增非抛异常版本，供 simulate 预检使用，避免触发 HTTP 500
    private String resolveHostNameServersOrNull() {
        return orderedNodes().stream()
                .filter(ManagedPseudoNode::hostBound)
                .filter(node -> "nameserver".equals(node.role()))
                .map(ManagedPseudoNode::address)
                .findFirst()
                .orElse(null);
    }
}
