package com.example.clustermanager.infrastructure.rocketmq;

import com.example.clustermanager.core.model.NodeStatus;
import java.util.Map;

public record RocketMqNodeSnapshot(
        String nodeId,
        String displayName,
        String hostName,
        String exposedAddress,
        NodeStatus status,
        Map<String, String> labels
) {
}
