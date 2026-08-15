package com.example.clustermanager.infrastructure.pseudo;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TAP 配置屬性——綁定 {@code cluster.pseudo.tap.*} 配置前綴。
 *
 * <p>為 {@link CommandTapNativeBridge} 提供命令模板和超時設置。
 * 作為 record 不可變值對象，由 Spring Boot {@code @ConfigurationProperties} 注入。
 *
 * <p>命令模板支持佔位符替換（見 {@link CommandTapNativeBridge#template}）。
 *
 * @param enabled              是否啟用真實 TAP 操作（true 時裝配 {@link CommandTapNativeBridge}）
 * @param timeout              命令執行超時時間
 * @param createSegmentCommand 創建隔離段的命令模板
 * @param assignIpCommand      綁定虛擬 IP 的命令模板
 * @param isolationCommand     應用隔離規則的命令模板
 * @param releaseIpCommand     釋放虛擬 IP 的命令模板
 */
@ConfigurationProperties(prefix = "cluster.pseudo.tap")
public record PseudoTapProperties(
        Boolean enabled,
        Duration timeout,
        List<String> createSegmentCommand,
        List<String> assignIpCommand,
        List<String> isolationCommand,
        List<String> releaseIpCommand
) {

    /**
     * 解析命令執行超時時間，null 時默認 10 秒。
     *
     * @return 超時 Duration
     */
    public Duration resolvedTimeout() {
        return timeout == null ? Duration.ofSeconds(10) : timeout;
    }
}
