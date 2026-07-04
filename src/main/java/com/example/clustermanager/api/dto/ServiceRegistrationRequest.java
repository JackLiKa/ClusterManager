package com.example.clustermanager.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record ServiceRegistrationRequest(
        @NotBlank String nodeId,
        @NotBlank String displayName,
        @NotBlank String role,
        @NotBlank String hostName,
        @NotBlank String address,
        @NotNull Integer port,
        Map<String, String> labels
) {
}
