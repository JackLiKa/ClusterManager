package com.example.clustermanager.application.model;

import com.example.clustermanager.core.model.OperationType;

public record NodeOperationCommand(
        ClusterSelection cluster,
        String nodeId,
        OperationType operationType
) {
}
