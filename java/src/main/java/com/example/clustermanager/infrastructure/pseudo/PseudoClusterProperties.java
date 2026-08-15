package com.example.clustermanager.infrastructure.pseudo;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 偽集群配置屬性——綁定 {@code cluster.pseudo.*} 配置前綴。
 *
 * <p>作為 record 不可變值對象，由 Spring Boot {@code @ConfigurationProperties} 機制
 * 從 {@code application.properties} 注入。提供帶默認值的解析方法，避免 null 檢查散落各處。
 *
 * <p>被以下組件依賴：
 * <ul>
 *   <li>{@link PseudoClusterProvider}——讀取 clusterId、autoStart</li>
 *   <li>{@link EmbeddedRocketMqRuntime}——讀取 workDir 作為嵌入式存儲根路徑</li>
 *   <li>{@link PseudoTopologySeeder}——讀取 clusterId、tapDeviceName、cidr 種子化網絡段</li>
 * </ul>
 *
 * @param clusterId    偽集群標識（默認 "local-lab"），同時用作虛擬網絡段 ID
 * @param tapDeviceName TAP 虛擬網絡設備名稱
 * @param cidr         CIDR 地址段（如 "10.77.0.0/24"），供 {@link CidrAddressPool} 分配虛擬 IP
 * @param workDir      嵌入式 RocketMQ 工作目錄（存儲 commitlog 等），默認 "run/pseudo-cluster"
 * @param autoStart    是否自動啟動種子節點，默認 true
 * @param cleanStoreOnStart 是否在節點啟動時清理舊 store 目錄，默認 true。
 *                          學習平台場景下每次啟動為乾淨狀態；如需保留歷史消息設為 false。
 * @param healthTimeout 健康輪詢超時時間
 */
@ConfigurationProperties(prefix = "cluster.pseudo")
public record PseudoClusterProperties(
        String clusterId,
        String tapDeviceName,
        String cidr,
        String workDir,
        Boolean autoStart,
        Boolean cleanStoreOnStart,
        Duration healthTimeout
) {

    /**
     * 解析工作目錄，空值時回退到默認路徑 {@code run/pseudo-cluster}。
     *
     * @return 有效的工作目錄路徑
     */
    public String resolvedWorkDir() {
        return workDir == null || workDir.isBlank() ? "run/pseudo-cluster" : workDir;
    }

    /**
     * 解析 auto-start 配置，null 時默認為 true（自動啟動種子節點）。
     *
     * @return 是否自動啟動
     */
    public boolean resolvedAutoStart() {
        return autoStart == null || autoStart;
    }

    /**
     * 解析 clean-store-on-start 配置，null 時默認為 true（啟動時清理舊 store）。
     *
     * <p>學習平台場景下默認清理，確保每次啟動為乾淨狀態。如需保留歷史消息用於調試，
     * 可設為 false。
     *
     * @return 是否在啟動時清理舊 store 目錄
     */
    public boolean resolvedCleanStoreOnStart() {
        return cleanStoreOnStart == null || cleanStoreOnStart;
    }

    /**
     * 解析健康超時時間，null 時默認 5 秒。
     *
     * @return 健康輪詢超時 Duration
     */
    public Duration resolvedHealthTimeout() {
        return healthTimeout == null ? Duration.ofSeconds(5) : healthTimeout;
    }
}
