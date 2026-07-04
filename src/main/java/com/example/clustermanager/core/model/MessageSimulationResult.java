package com.example.clustermanager.core.model;

import java.time.Instant;
import java.util.List;

public record MessageSimulationResult(
        Instant executedAt,
        List<MessageDeliveryResult> deliveries
) {
}
