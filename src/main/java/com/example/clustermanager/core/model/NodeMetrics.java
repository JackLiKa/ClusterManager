package com.example.clustermanager.core.model;

public record NodeMetrics(
        String nodeId,
        double cpuUsage,
        double memoryUsage,
        double networkInBytesPerSecond,
        double networkOutBytesPerSecond
) {
}
