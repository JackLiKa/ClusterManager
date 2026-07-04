package com.example.clustermanager.infrastructure.pseudo;

import com.example.clustermanager.core.model.LogEntry;
import com.example.clustermanager.core.model.NodeMetrics;
import com.example.clustermanager.core.model.NodeStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class LocalPseudoNodeRuntime implements PseudoNodeRuntime {

    private final PseudoClusterProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<String, Process> processes = new ConcurrentHashMap<>();

    public LocalPseudoNodeRuntime(PseudoClusterProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.resolvedHealthTimeout())
                .build();
    }

    @Override
    public void ensurePrepared(List<PseudoNodeSpec> specs) {
        specs.forEach(spec -> {
            try {
                Files.createDirectories(nodeDir(spec));
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to prepare pseudo node directory: " + spec.nodeId(), exception);
            }
        });
    }

    @Override
    public synchronized void start(PseudoNodeSpec spec) {
        Process existing = processes.get(spec.nodeId());
        if (existing != null && existing.isAlive()) {
            return;
        }
        try {
            Files.createDirectories(nodeDir(spec));
            List<String> command = buildCommand();
            command.add(spec.nodeId());
            command.add(spec.role());
            command.add(String.valueOf(spec.port()));
            command.add(nodeDir(spec).toAbsolutePath().toString());
            Process process = new ProcessBuilder(command)
                    .directory(rootDir().toFile())
                    .redirectOutput(nodeDir(spec).resolve("stdout.log").toFile())
                    .redirectError(nodeDir(spec).resolve("stderr.log").toFile())
                    .start();
            processes.put(spec.nodeId(), process);
            waitUntilHealthy(spec);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to start pseudo node: " + spec.nodeId(), exception);
        }
    }

    @Override
    public synchronized void stop(String nodeId) {
        Process process = processes.remove(nodeId);
        if (process == null) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    @Override
    public synchronized void restart(PseudoNodeSpec spec) {
        stop(spec.nodeId());
        start(spec);
    }

    @Override
    public NodeStatus status(PseudoNodeSpec spec) {
        Process process = processes.get(spec.nodeId());
        if (process == null || !process.isAlive()) {
            return NodeStatus.STOPPED;
        }
        return isHealthy(spec) ? NodeStatus.RUNNING : NodeStatus.STARTING;
    }

    @Override
    public NodeMetrics metrics(PseudoNodeSpec spec) {
        if (status(spec) != NodeStatus.RUNNING) {
            return new NodeMetrics(spec.nodeId(), 0, 0, 0, 0);
        }
        try {
            JsonNode node = objectMapper.readTree(get(spec, "/metrics"));
            return new NodeMetrics(
                    spec.nodeId(),
                    node.path("cpuUsage").asDouble(),
                    node.path("memoryUsage").asDouble(),
                    node.path("networkInBytesPerSecond").asDouble(),
                    node.path("networkOutBytesPerSecond").asDouble()
            );
        } catch (Exception exception) {
            return new NodeMetrics(spec.nodeId(), 0, 0, 0, 0);
        }
    }

    @Override
    public List<LogEntry> logs(PseudoNodeSpec spec, int limit) {
        try {
            List<String> lines = objectMapper.readValue(get(spec, "/logs"), new TypeReference<>() {
            });
            return lines.stream()
                    .skip(Math.max(0, lines.size() - Math.max(1, limit)))
                    .map(line -> new LogEntry(Instant.now(), spec.nodeId(), "INFO", line))
                    .toList();
        } catch (Exception exception) {
            return List.of(new LogEntry(
                    Instant.now(),
                    spec.nodeId(),
                    "WARN",
                    "Pseudo node log endpoint unavailable: " + exception.getMessage()
            ));
        }
    }

    @Override
    public boolean deliverMessage(PseudoNodeSpec brokerSpec, String topic, String messageKey, String payload) {
        if (status(brokerSpec) != NodeStatus.RUNNING) {
            return false;
        }
        try {
            String endpoint = "/messages?topic=" + encode(topic);
            HttpRequest request = HttpRequest.newBuilder(uri(brokerSpec, endpoint))
                    .timeout(properties.resolvedHealthTimeout())
                    .POST(HttpRequest.BodyPublishers.ofString("""
                            {"messageKey":"%s","payload":%s}
                            """.formatted(messageKey, objectMapper.writeValueAsString(payload))))
                    .header("Content-Type", "application/json")
                    .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() / 100 == 2;
        } catch (Exception exception) {
            return false;
        }
    }

    @Override
    public boolean consumeMessage(PseudoNodeSpec brokerSpec, String topic, String consumerGroup) {
        if (status(brokerSpec) != NodeStatus.RUNNING) {
            return false;
        }
        try {
            String endpoint = "/messages/consume?topic=%s&group=%s".formatted(encode(topic), encode(consumerGroup));
            JsonNode response = objectMapper.readTree(get(brokerSpec, endpoint));
            return response.path("consumed").asBoolean(false);
        } catch (Exception exception) {
            return false;
        }
    }

    @PreDestroy
    public void shutdown() {
        List.copyOf(processes.keySet()).forEach(this::stop);
    }

    private boolean isHealthy(PseudoNodeSpec spec) {
        try {
            return objectMapper.readTree(get(spec, "/health")).path("status").asText().equals("UP");
        } catch (Exception exception) {
            return false;
        }
    }

    private void waitUntilHealthy(PseudoNodeSpec spec) {
        long deadline = System.nanoTime() + properties.resolvedHealthTimeout().toNanos();
        while (System.nanoTime() < deadline) {
            if (isHealthy(spec)) {
                return;
            }
            try {
                Thread.sleep(150);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new IllegalStateException("Pseudo node did not become healthy: " + spec.nodeId());
    }

    private String get(PseudoNodeSpec spec, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri(spec, path))
                .timeout(properties.resolvedHealthTimeout())
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    private URI uri(PseudoNodeSpec spec, String path) {
        return URI.create("http://127.0.0.1:%d%s".formatted(spec.port(), path));
    }

    private Path rootDir() {
        return Path.of(properties.resolvedWorkDir()).toAbsolutePath().normalize();
    }

    private Path nodeDir(PseudoNodeSpec spec) {
        return rootDir().resolve(spec.nodeId());
    }

    private List<String> buildCommand() {
        List<String> command = new ArrayList<>();
        command.add(properties.resolvedJavaExecutable());
        String classPath = System.getProperty("java.class.path");
        if (classPath != null && classPath.endsWith(".jar")) {
            command.add("-Dloader.main=" + PseudoNodeAgent.class.getName());
            command.add("-cp");
            command.add(classPath);
            command.add("org.springframework.boot.loader.launch.PropertiesLauncher");
            return command;
        }
        command.add("-cp");
        command.add(classPath);
        command.add(PseudoNodeAgent.class.getName());
        return command;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
