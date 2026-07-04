package com.example.clustermanager.core.model;

import java.util.List;
import java.util.Map;

public record MessageScenario(
        String topic,
        String consumerGroup,
        int messageCount,
        String payloadTemplate,
        String producerNodeId,
        List<String> consumerNodeIds,
        Map<String, String> headers
) {
}
