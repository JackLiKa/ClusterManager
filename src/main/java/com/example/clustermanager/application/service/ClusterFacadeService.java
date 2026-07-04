package com.example.clustermanager.application.service;

import com.example.clustermanager.application.model.ClusterSelection;
import com.example.clustermanager.application.model.MessageSimulationCommand;
import com.example.clustermanager.application.model.NodeOperationCommand;
import com.example.clustermanager.application.model.ServiceRegistrationCommand;
import com.example.clustermanager.core.model.ClusterTopology;
import com.example.clustermanager.core.model.LogEntry;
import com.example.clustermanager.core.model.MessageScenario;
import com.example.clustermanager.core.model.MessageSimulationResult;
import com.example.clustermanager.core.model.MonitoringSnapshot;
import com.example.clustermanager.core.model.OperationResult;
import com.example.clustermanager.core.model.ProviderDescriptor;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ClusterFacadeService {

    private final ClusterProviderRegistry providerRegistry;

    public ClusterFacadeService(ClusterProviderRegistry providerRegistry) {
        this.providerRegistry = providerRegistry;
    }

    public List<ProviderDescriptor> listProviders() {
        return providerRegistry.listProviders();
    }

    public ClusterTopology loadTopology(ClusterSelection cluster) {
        return providerRegistry.resolve(cluster).loadTopology(cluster.toClusterRef());
    }

    public MonitoringSnapshot loadMetrics(ClusterSelection cluster) {
        return providerRegistry.resolve(cluster).loadMetrics(cluster.toClusterRef());
    }

    public List<LogEntry> loadLogs(ClusterSelection cluster, String nodeId, int limit) {
        return providerRegistry.resolve(cluster).loadLogs(cluster.toClusterRef(), nodeId, limit);
    }

    public OperationResult operateNode(NodeOperationCommand command) {
        return providerRegistry.resolve(command.cluster())
                .operateNode(command.cluster().toClusterRef(), command.nodeId(), command.operationType());
    }

    public OperationResult registerService(ServiceRegistrationCommand command) {
        return providerRegistry.resolve(command.cluster())
                .registerService(command.cluster().toClusterRef(), command.service());
    }

    public OperationResult deleteService(ClusterSelection cluster, String nodeId) {
        return providerRegistry.resolve(cluster).deleteService(cluster.toClusterRef(), nodeId);
    }

    public MessageSimulationResult simulateMessages(MessageSimulationCommand command) {
        MessageScenario scenario = new MessageScenario(
                command.topic(),
                command.consumerGroup(),
                command.messageCount(),
                command.payloadTemplate(),
                command.producerNodeId(),
                command.consumerNodeIds(),
                command.headers()
        );
        return providerRegistry.resolve(command.cluster())
                .simulate(command.cluster().toClusterRef(), scenario);
    }
}
