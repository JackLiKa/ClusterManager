package com.example.clustermanager.core.model;

public record ProviderDescriptor(
        String providerId,
        String displayName,
        ClusterMode mode,
        MiddlewareType middleware
) {
}
