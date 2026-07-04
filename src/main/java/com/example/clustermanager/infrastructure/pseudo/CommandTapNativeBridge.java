package com.example.clustermanager.infrastructure.pseudo;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
@ConditionalOnProperty(prefix = "cluster.pseudo.tap", name = "enabled", havingValue = "true")
public class CommandTapNativeBridge implements TapNativeBridge {

    private static final Logger log = LoggerFactory.getLogger(CommandTapNativeBridge.class);

    private final PseudoTapProperties properties;

    public CommandTapNativeBridge(PseudoTapProperties properties) {
        this.properties = properties;
    }

    @Override
    public void createIsolatedSegment(String tapDeviceName, String cidr) {
        run(template(properties.createSegmentCommand(), tapDeviceName, cidr, null, null));
    }

    @Override
    public void assignNodeIp(String tapDeviceName, String nodeId, String ipAddress) {
        run(template(properties.assignIpCommand(), tapDeviceName, null, nodeId, ipAddress));
    }

    @Override
    public void applyIsolationRules(String tapDeviceName, String nodeId, Collection<String> allowedPeers) {
        List<String> command = properties.isolationCommand();
        if (command == null || command.isEmpty()) {
            return;
        }
        run(template(command.stream()
                .map(token -> token.replace("{allowedPeers}", String.join(",", allowedPeers)))
                .toList(), tapDeviceName, null, nodeId, null));
    }

    @Override
    public void releaseNode(String tapDeviceName, String nodeId, String ipAddress) {
        run(template(properties.releaseIpCommand(), tapDeviceName, null, nodeId, ipAddress));
    }

    private void run(List<String> command) {
        if (command.isEmpty()) {
            return;
        }
        try {
            Process process = new ProcessBuilder(command).start();
            boolean finished = process.waitFor(properties.resolvedTimeout().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("TAP command timed out: " + command);
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("TAP command failed with exit " + process.exitValue() + ": " + command);
            }
            log.info("TAP command succeeded: {}", command);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to execute TAP command: " + command, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while executing TAP command: " + command, exception);
        }
    }

    private List<String> template(List<String> raw, String tapDeviceName, String cidr, String nodeId, String ipAddress) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> rendered = new ArrayList<>();
        for (String token : raw) {
            rendered.add(token
                    .replace("{tapDeviceName}", value(tapDeviceName))
                    .replace("{cidr}", value(cidr))
                    .replace("{nodeId}", value(nodeId))
                    .replace("{ipAddress}", value(ipAddress)));
        }
        return rendered;
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
