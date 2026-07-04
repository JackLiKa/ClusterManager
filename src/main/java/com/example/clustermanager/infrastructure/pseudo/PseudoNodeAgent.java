package com.example.clustermanager.infrastructure.pseudo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

public final class PseudoNodeAgent {

    private final String nodeId;
    private final String role;
    private final int port;
    private final Path workDir;
    private final Map<String, Queue<String>> topicMessages = new ConcurrentHashMap<>();

    private PseudoNodeAgent(String nodeId, String role, int port, Path workDir) {
        this.nodeId = nodeId;
        this.role = role;
        this.port = port;
        this.workDir = workDir;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException("Usage: PseudoNodeAgent <nodeId> <role> <port> <workDir>");
        }
        PseudoNodeAgent agent = new PseudoNodeAgent(
                args[0],
                args[1],
                Integer.parseInt(args[2]),
                Path.of(args[3])
        );
        agent.start();
    }

    private void start() throws IOException {
        Files.createDirectories(workDir);
        log("starting pseudo node role=%s port=%d".formatted(role, port));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/health", exchange -> json(exchange, 200, """
                {"status":"UP","nodeId":"%s","role":"%s"}
                """.formatted(nodeId, role).trim()));
        server.createContext("/metrics", this::metrics);
        server.createContext("/logs", this::logs);
        server.createContext("/messages", this::messages);
        server.createContext("/messages/consume", this::consume);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        log("pseudo node ready");
    }

    private void metrics(HttpExchange exchange) throws IOException {
        json(exchange, 200, """
                {"nodeId":"%s","cpuUsage":%.2f,"memoryUsage":%.2f,"networkInBytesPerSecond":%.2f,"networkOutBytesPerSecond":%.2f}
                """.formatted(
                        nodeId,
                        ThreadLocalRandom.current().nextDouble(8, 60),
                        ThreadLocalRandom.current().nextDouble(18, 72),
                        ThreadLocalRandom.current().nextDouble(1024, 8192),
                        ThreadLocalRandom.current().nextDouble(1024, 8192)
                ).trim());
    }

    private void logs(HttpExchange exchange) throws IOException {
        Path logFile = workDir.resolve("node.log");
        List<String> lines = Files.exists(logFile) ? Files.readAllLines(logFile, StandardCharsets.UTF_8) : List.of();
        int from = Math.max(0, lines.size() - 30);
        StringBuilder payload = new StringBuilder("[");
        for (int index = from; index < lines.size(); index++) {
            if (index > from) {
                payload.append(',');
            }
            payload.append('"').append(escape(lines.get(index))).append('"');
        }
        payload.append(']');
        json(exchange, 200, payload.toString());
    }

    private void messages(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            json(exchange, 405, "{\"error\":\"METHOD_NOT_ALLOWED\"}");
            return;
        }
        String query = exchange.getRequestURI().getQuery();
        String topic = queryParam(query, "topic", "DefaultTopic");
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        topicMessages.computeIfAbsent(topic, ignored -> new ConcurrentLinkedQueue<>()).add(body);
        log("accepted message topic=%s payload=%s".formatted(topic, body));
        json(exchange, 202, "{\"accepted\":true}");
    }

    private void consume(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String topic = queryParam(query, "topic", "DefaultTopic");
        String group = queryParam(query, "group", "default");
        Queue<String> queue = topicMessages.computeIfAbsent(topic, ignored -> new ConcurrentLinkedQueue<>());
        String payload = queue.poll();
        boolean consumed = payload != null;
        log("consume topic=%s group=%s success=%s".formatted(topic, group, consumed));
        json(exchange, 200, "{\"consumed\":%s}".formatted(consumed));
    }

    private void log(String message) throws IOException {
        Files.createDirectories(workDir);
        Files.writeString(
                workDir.resolve("node.log"),
                "%s [%s] %s%n".formatted(Instant.now(), nodeId, message),
                StandardCharsets.UTF_8,
                Files.exists(workDir.resolve("node.log"))
                        ? java.nio.file.StandardOpenOption.APPEND
                        : java.nio.file.StandardOpenOption.CREATE
        );
    }

    private static void json(HttpExchange exchange, int statusCode, String payload) throws IOException {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private static String queryParam(String query, String name, String fallback) {
        if (query == null || query.isBlank()) {
            return fallback;
        }
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && pair[0].equals(name)) {
                return java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            }
        }
        return fallback;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
