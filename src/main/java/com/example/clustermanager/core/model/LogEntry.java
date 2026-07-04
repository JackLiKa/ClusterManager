package com.example.clustermanager.core.model;

import java.time.Instant;

public record LogEntry(
        Instant timestamp,
        String nodeId,
        String level,
        String message
) {
}
