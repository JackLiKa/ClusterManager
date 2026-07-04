package com.example.clustermanager.api.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cluster.cors")
public record CorsProperties(
        List<String> allowedOrigins
) {
}
