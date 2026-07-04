package com.example.clustermanager.core.model;

public record OperationResult(
        String targetId,
        OperationType operationType,
        boolean success,
        String message
) {
}
