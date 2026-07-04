package com.example.clustermanager.api.dto;

import com.example.clustermanager.core.model.OperationType;
import jakarta.validation.constraints.NotNull;

public record NodeOperationRequest(
        @NotNull OperationType operationType
) {
}
