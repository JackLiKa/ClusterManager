package com.example.clustermanager.core.model;

public record NetworkLink(
        String sourceNodeId,
        String targetNodeId,
        boolean healthy,
        String linkType,
        double latencyMs
) {
}
