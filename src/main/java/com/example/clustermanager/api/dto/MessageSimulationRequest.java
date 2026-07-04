package com.example.clustermanager.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

public record MessageSimulationRequest(
        @NotBlank String topic,
        @NotBlank String consumerGroup,
        @Min(1) int messageCount,
        String payloadTemplate,
        @NotBlank String producerNodeId,
        List<String> consumerNodeIds,
        Map<String, String> headers
) {
}
