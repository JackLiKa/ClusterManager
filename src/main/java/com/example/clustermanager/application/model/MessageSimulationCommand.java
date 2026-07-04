package com.example.clustermanager.application.model;

import java.util.List;
import java.util.Map;

public record MessageSimulationCommand(
        ClusterSelection cluster,
        String topic,
        String consumerGroup,
        int messageCount,
        String payloadTemplate,
        String producerNodeId,
        List<String> consumerNodeIds,
        Map<String, String> headers
) {
}
