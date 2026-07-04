package com.example.clustermanager.core.model;

public record ClusterRef(
        String clusterId,
        ClusterMode mode,
        MiddlewareType middleware
) {
}
