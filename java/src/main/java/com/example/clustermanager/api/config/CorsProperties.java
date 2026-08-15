package com.example.clustermanager.api.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS 属性，绑定 application.properties 中前缀为 {@code cluster.cors} 的配置项。
 *
 * <p>API 层角色：集中管理允许跨域访问的来源列表，供 {@link CorsConfig}（REST 端点）
 * 与 {@link WebSocketBrokerConfig}（STOMP 端点）共用，避免来源配置分散。
 *
 * <p>默认值见 application.properties：{@code cluster.cors.allowed-origins=http://localhost:5173,127.0.0.1:5173}，
 * 仅放通前端 Vite 开发服务器。
 */
@ConfigurationProperties(prefix = "cluster.cors")
public record CorsProperties(
        /** 允许跨域访问的来源列表，对应配置项 {@code cluster.cors.allowed-origins}。 */
        List<String> allowedOrigins
) {
}
