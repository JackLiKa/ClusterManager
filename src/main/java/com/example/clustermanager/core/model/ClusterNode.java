package com.example.clustermanager.core.model;

import java.util.Map;

public record ClusterNode(
        String nodeId,
        String displayName,
        String hostName,
        String virtualIp,
        NodeStatus status,
        Map<String, String> labels
) {
}
