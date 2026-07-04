package com.example.clustermanager.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cluster.stream")
public record ClusterStreamProperties(
        long publishIntervalMs
) {
}
