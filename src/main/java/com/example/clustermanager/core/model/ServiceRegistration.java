package com.example.clustermanager.core.model;

import java.util.Map;

public record ServiceRegistration(
        String nodeId,
        String displayName,
        String role,
        String hostName,
        String address,
        Integer port,
        Map<String, String> labels
) {
}
