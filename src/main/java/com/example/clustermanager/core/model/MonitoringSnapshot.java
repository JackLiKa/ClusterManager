package com.example.clustermanager.core.model;

import java.time.Instant;
import java.util.List;

public record MonitoringSnapshot(
        Instant capturedAt,
        List<NodeMetrics> nodes
) {
}
