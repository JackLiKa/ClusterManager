package com.example.clustermanager.infrastructure.pseudo;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cluster.pseudo.tap")
public record PseudoTapProperties(
        Boolean enabled,
        Duration timeout,
        List<String> createSegmentCommand,
        List<String> assignIpCommand,
        List<String> isolationCommand,
        List<String> releaseIpCommand
) {

    public Duration resolvedTimeout() {
        return timeout == null ? Duration.ofSeconds(10) : timeout;
    }
}
