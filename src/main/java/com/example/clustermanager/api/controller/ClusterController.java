package com.example.clustermanager.api.controller;

import com.example.clustermanager.api.dto.MessageSimulationRequest;
import com.example.clustermanager.api.dto.NodeOperationRequest;
import com.example.clustermanager.api.dto.ServiceRegistrationRequest;
import com.example.clustermanager.application.model.ClusterSelection;
import com.example.clustermanager.application.model.MessageSimulationCommand;
import com.example.clustermanager.application.model.NodeOperationCommand;
import com.example.clustermanager.application.model.ServiceRegistrationCommand;
import com.example.clustermanager.application.service.ClusterFacadeService;
import com.example.clustermanager.core.model.ClusterMode;
import com.example.clustermanager.core.model.LogEntry;
import com.example.clustermanager.core.model.MessageSimulationResult;
import com.example.clustermanager.core.model.MiddlewareType;
import com.example.clustermanager.core.model.MonitoringSnapshot;
import com.example.clustermanager.core.model.OperationResult;
import com.example.clustermanager.core.model.ProviderDescriptor;
import com.example.clustermanager.core.model.ServiceRegistration;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/clusters")
public class ClusterController {

    private final ClusterFacadeService clusterFacadeService;

    public ClusterController(ClusterFacadeService clusterFacadeService) {
        this.clusterFacadeService = clusterFacadeService;
    }

    @GetMapping("/providers")
    public List<ProviderDescriptor> listProviders() {
        return clusterFacadeService.listProviders();
    }

    @GetMapping("/{mode}/{middleware}/{clusterId}/topology")
    public Object topology(
            @PathVariable String mode,
            @PathVariable String middleware,
            @PathVariable String clusterId
    ) {
        return clusterFacadeService.loadTopology(selection(clusterId, mode, middleware));
    }

    @GetMapping("/{mode}/{middleware}/{clusterId}/metrics")
    public MonitoringSnapshot metrics(
            @PathVariable String mode,
            @PathVariable String middleware,
            @PathVariable String clusterId
    ) {
        return clusterFacadeService.loadMetrics(selection(clusterId, mode, middleware));
    }

    @GetMapping("/{mode}/{middleware}/{clusterId}/logs")
    public List<LogEntry> logs(
            @PathVariable String mode,
            @PathVariable String middleware,
            @PathVariable String clusterId,
            @RequestParam(required = false) String nodeId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return clusterFacadeService.loadLogs(selection(clusterId, mode, middleware), nodeId, limit);
    }

    @PostMapping("/{mode}/{middleware}/{clusterId}/nodes/{nodeId}/operations")
    public OperationResult operateNode(
            @PathVariable String mode,
            @PathVariable String middleware,
            @PathVariable String clusterId,
            @PathVariable String nodeId,
            @Valid @RequestBody NodeOperationRequest request
    ) {
        return clusterFacadeService.operateNode(new NodeOperationCommand(
                selection(clusterId, mode, middleware),
                nodeId,
                request.operationType()
        ));
    }

    @PostMapping("/{mode}/{middleware}/{clusterId}/services")
    public OperationResult registerService(
            @PathVariable String mode,
            @PathVariable String middleware,
            @PathVariable String clusterId,
            @Valid @RequestBody ServiceRegistrationRequest request
    ) {
        return clusterFacadeService.registerService(new ServiceRegistrationCommand(
                selection(clusterId, mode, middleware),
                new ServiceRegistration(
                        request.nodeId(),
                        request.displayName(),
                        request.role(),
                        request.hostName(),
                        request.address(),
                        request.port(),
                        request.labels() == null ? Map.of() : request.labels()
                )
        ));
    }

    @DeleteMapping("/{mode}/{middleware}/{clusterId}/services/{nodeId}")
    public OperationResult deleteService(
            @PathVariable String mode,
            @PathVariable String middleware,
            @PathVariable String clusterId,
            @PathVariable String nodeId
    ) {
        return clusterFacadeService.deleteService(selection(clusterId, mode, middleware), nodeId);
    }

    @PostMapping("/{mode}/{middleware}/{clusterId}/messages/simulate")
    public MessageSimulationResult simulateMessages(
            @PathVariable String mode,
            @PathVariable String middleware,
            @PathVariable String clusterId,
            @Valid @RequestBody MessageSimulationRequest request
    ) {
        return clusterFacadeService.simulateMessages(new MessageSimulationCommand(
                selection(clusterId, mode, middleware),
                request.topic(),
                request.consumerGroup(),
                request.messageCount(),
                request.payloadTemplate(),
                request.producerNodeId(),
                request.consumerNodeIds(),
                request.headers()
        ));
    }

    private ClusterSelection selection(String clusterId, String rawMode, String rawMiddleware) {
        return new ClusterSelection(
                clusterId,
                ClusterMode.valueOf(rawMode.toUpperCase(Locale.ROOT)),
                MiddlewareType.valueOf(rawMiddleware.toUpperCase(Locale.ROOT))
        );
    }
}
