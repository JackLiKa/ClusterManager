package com.example.clustermanager.infrastructure.rocketmq;

public record RocketMqLinkSnapshot(
        String sourceNodeId,
        String targetNodeId,
        boolean healthy,
        double latencyMs
) {
}
