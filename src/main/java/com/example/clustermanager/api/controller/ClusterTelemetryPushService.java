package com.example.clustermanager.api.controller;

import com.example.clustermanager.api.config.ClusterStreamProperties;
import com.example.clustermanager.application.model.ClusterSelection;
import com.example.clustermanager.application.service.ClusterFacadeService;
import com.example.clustermanager.core.model.ClusterMode;
import com.example.clustermanager.core.model.MiddlewareType;
import com.example.clustermanager.infrastructure.pseudo.PseudoClusterProperties;
import com.example.clustermanager.infrastructure.rocketmq.RocketMqClusterProperties;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ClusterTelemetryPushService {

    private final SimpMessagingTemplate messagingTemplate;
    private final ClusterFacadeService clusterFacadeService;
    private final PseudoClusterProperties pseudoProperties;
    private final RocketMqClusterProperties rocketMqClusterProperties;
    private final ClusterStreamProperties streamProperties;

    public ClusterTelemetryPushService(
            SimpMessagingTemplate messagingTemplate,
            ClusterFacadeService clusterFacadeService,
            PseudoClusterProperties pseudoProperties,
            RocketMqClusterProperties rocketMqClusterProperties,
            ClusterStreamProperties streamProperties
    ) {
        this.messagingTemplate = messagingTemplate;
        this.clusterFacadeService = clusterFacadeService;
        this.pseudoProperties = pseudoProperties;
        this.rocketMqClusterProperties = rocketMqClusterProperties;
        this.streamProperties = streamProperties;
    }

    @Scheduled(fixedRateString = "${cluster.stream.publish-interval-ms:5000}")
    public void pushSnapshots() {
        publish(selection(pseudoProperties.clusterId(), ClusterMode.PSEUDO), "metrics");
        publish(selection(rocketMqClusterProperties.clusterId(), ClusterMode.REAL), "metrics");
    }

    @Scheduled(fixedRateString = "${cluster.stream.publish-interval-ms:5000}", initialDelayString = "${cluster.stream.publish-interval-ms:5000}")
    public void pushLogs() {
        publish(selection(pseudoProperties.clusterId(), ClusterMode.PSEUDO), "logs");
        publish(selection(rocketMqClusterProperties.clusterId(), ClusterMode.REAL), "logs");
    }

    private void publish(ClusterSelection selection, String channel) {
        String destination = "/topic/clusters/%s/%s".formatted(selection.clusterId(), channel);
        Object payload = switch (channel) {
            case "metrics" -> clusterFacadeService.loadMetrics(selection);
            case "logs" -> clusterFacadeService.loadLogs(selection, null, 10);
            default -> throw new IllegalArgumentException("Unsupported stream channel: " + channel);
        };
        messagingTemplate.convertAndSend(destination, payload);
    }

    private ClusterSelection selection(String clusterId, ClusterMode mode) {
        return new ClusterSelection(clusterId, mode, MiddlewareType.ROCKETMQ);
    }
}
