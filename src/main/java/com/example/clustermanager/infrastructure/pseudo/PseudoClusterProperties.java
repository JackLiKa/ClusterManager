package com.example.clustermanager.infrastructure.pseudo;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cluster.pseudo")
public record PseudoClusterProperties(
        String clusterId,
        String tapDeviceName,
        String cidr,
        String workDir,
        Boolean autoStart,
        String javaExecutable,
        Duration healthTimeout
) {

    public String resolvedWorkDir() {
        return workDir == null || workDir.isBlank() ? "run/pseudo-cluster" : workDir;
    }

    public boolean resolvedAutoStart() {
        return autoStart == null || autoStart;
    }

    public String resolvedJavaExecutable() {
        return javaExecutable == null || javaExecutable.isBlank() ? "java" : javaExecutable;
    }

    public Duration resolvedHealthTimeout() {
        return healthTimeout == null ? Duration.ofSeconds(5) : healthTimeout;
    }
}
