package com.example.clustermanager.core.model;

public record MessageDeliveryResult(
        String messageKey,
        String producerNodeId,
        String consumerNodeId,
        boolean success,
        String detail
) {
}
