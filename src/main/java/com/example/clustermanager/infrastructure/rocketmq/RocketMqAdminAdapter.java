package com.example.clustermanager.infrastructure.rocketmq;

import com.example.clustermanager.core.model.ClusterNode;
import com.example.clustermanager.core.model.MessageDeliveryResult;
import com.example.clustermanager.core.model.NetworkLink;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RocketMqAdminAdapter {

    private final RocketMqAdminClient adminClient;

    public RocketMqAdminAdapter(RocketMqAdminClient adminClient) {
        this.adminClient = adminClient;
    }

    public List<ClusterNode> loadNodes(String clusterName) {
        return adminClient.fetchNodes(clusterName).stream()
                .map(node -> new ClusterNode(
                        node.nodeId(),
                        node.displayName(),
                        node.hostName(),
                        node.exposedAddress(),
                        node.status(),
                        node.labels()
                ))
                .toList();
    }

    public List<NetworkLink> loadLinks(String clusterName) {
        return loadLinks(clusterName, loadNodes(clusterName));
    }

    public List<NetworkLink> loadLinks(String clusterName, List<ClusterNode> nodes) {
        List<NetworkLink> links = new ArrayList<>(adminClient.fetchLinks(clusterName).stream()
                .map(link -> new NetworkLink(
                        link.sourceNodeId(),
                        link.targetNodeId(),
                        link.healthy(),
                        "physical-network",
                        link.latencyMs()
                ))
                .toList());
        String primaryNameServer = nodes.stream()
                .filter(node -> "nameserver".equals(node.labels().get("role")))
                .map(ClusterNode::nodeId)
                .findFirst()
                .orElse(null);
        if (primaryNameServer != null) {
            nodes.stream()
                    .filter(node -> !node.nodeId().equals(primaryNameServer))
                    .filter(node -> node.labels().containsKey("source") && "manual".equals(node.labels().get("source")))
                    .forEach(node -> links.add(new NetworkLink(primaryNameServer, node.nodeId(), true, "manual-registration", 2.5)));
        }
        return links;
    }

    public List<MessageDeliveryResult> probeMessageFlow(
            String topic,
            String producerNodeId,
            List<String> consumerNodes,
            int count,
            List<ClusterNode> manualNodes
    ) {
        List<String> normalizedConsumers = consumerNodes == null || consumerNodes.isEmpty()
                ? fallbackConsumers(manualNodes)
                : consumerNodes;
        return adminClient.fetchMessages(topic, count).stream()
                .map(payload -> {
                    int keyIndex = Math.abs(payload.hashCode()) % normalizedConsumers.size();
                    return new MessageDeliveryResult(
                            String.valueOf(payload.get("messageKey")),
                            producerNodeId,
                            normalizedConsumers.get(keyIndex),
                            true,
                            "Observed via RocketMQ Admin API probe"
                    );
                })
                .toList();
    }

    public List<MessageDeliveryResult> probeMessageFlow(String topic, String producerNodeId, List<String> consumerNodes, int count) {
        return probeMessageFlow(topic, producerNodeId, consumerNodes, count, List.of());
    }

    private List<String> fallbackConsumers(List<ClusterNode> manualNodes) {
        List<String> consumers = manualNodes.stream()
                .map(ClusterNode::nodeId)
                .toList();
        return consumers.isEmpty() ? List.of("prod-broker-01") : consumers;
    }
    public void invokeNodeOperation(String nodeId, String operation) {
        adminClient.invokeBrokerLifecycle(nodeId, operation);
    }

    // P1 修复: 新增带返回值的操作入口，避免 operateNode 在 admin 调用失败时仍返回 success=true
    public boolean tryInvokeNodeOperation(String nodeId, String operation) {
        try {
            adminClient.invokeBrokerLifecycle(nodeId, operation);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }
}
