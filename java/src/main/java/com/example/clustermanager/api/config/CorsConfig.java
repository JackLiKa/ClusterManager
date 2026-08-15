package com.example.clustermanager.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS 跨域配置，作用于所有 {@code /api/**} REST 端点。
 *
 * <p>API 层角色：允许前端开发服务器（默认 http://localhost:5173）跨域访问后端 REST API。
 * 允许的来源来自 {@link CorsProperties}（前缀 {@code cluster.cors}），方法与请求头均放通。
 *
 * <p>注意：WebSocket 端点的 CORS 在 {@link WebSocketBrokerConfig} 中单独配置，
 * 因为 Spring MVC 的 CORS 配置不覆盖 STOMP 端点。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /** CORS 属性来源，提供允许的来源列表。 */
    private final CorsProperties corsProperties;

    /**
     * 构造器注入 CORS 属性。
     *
     * @param corsProperties 来自 {@code cluster.cors.allowed-origins} 的允许来源列表
     */
    public CorsConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    /**
     * 注册 CORS 映射：对 {@code /api/**} 路径放通指定来源、所有方法与所有请求头。
     *
     * @param registry Spring MVC 提供的 CORS 注册表
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
                .allowedMethods("*")
                .allowedHeaders("*");
    }
}
