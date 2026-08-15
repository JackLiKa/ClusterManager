package com.example.clustermanager.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 遥测推送配置属性，绑定 application.properties 中前缀为 {@code cluster.stream} 的配置项。
 *
 * <p>API 层角色：为 {@link com.example.clustermanager.api.controller.ClusterTelemetryPushService}
 * 提供推送周期等参数。该服务按固定周期通过 WebSocket STOMP 向前端推送集群指标与日志，
 * 推送频率由此处的 {@code publishIntervalMs} 决定（默认 5000ms，见 application.properties）。
 *
 * <p>采用 record 形式以保持不可变；Spring Boot 通过 @ConfigurationProperties 自动绑定。
 */
@ConfigurationProperties(prefix = "cluster.stream")
public record ClusterStreamProperties(
        /** 遥测推送周期（毫秒）。对应配置项 {@code cluster.stream.publish-interval-ms}，默认 5000。 */
        long publishIntervalMs
) {
}
