package com.example.clustermanager.infrastructure.rocketmq;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cluster.rocketmq")
public record RocketMqClusterProperties(
        String clusterId,
        String dashboardName,
        List<String> nameServers
) {
}
