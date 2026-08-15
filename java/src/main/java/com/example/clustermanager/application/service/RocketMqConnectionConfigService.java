package com.example.clustermanager.application.service;

import com.example.clustermanager.infrastructure.rocketmq.RocketMqClusterProperties;
import com.example.clustermanager.infrastructure.rocketmq.RocketMqConnectionConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * RocketMQ 連接配置服務——管理運行時配置的讀取、修改和持久化。
 *
 * <p><b>持久化</b>：配置保存到 {@code run/rocketmq-connection.properties} 文件，
 * 應用啟動時自動加載。文件格式為標準 Java Properties。
 *
 * <p><b>立即生效</b>：修改後立即更新 {@link RocketMqConnectionConfig} 內存狀態，
 * 下次消息收發操作即使用新配置，無需重啟。
 *
 * <p>被 {@link com.example.clustermanager.api.controller.ClusterController} 依賴，
 * 暴露 GET/PUT API 端點。
 */
@Service
public class RocketMqConnectionConfigService {

    private static final Logger log = LoggerFactory.getLogger(RocketMqConnectionConfigService.class);
    private static final Path CONFIG_FILE = Paths.get("run", "rocketmq-connection.properties");

    private final RocketMqConnectionConfig config;
    private final RocketMqClusterProperties bootProperties;

    public RocketMqConnectionConfigService(RocketMqConnectionConfig config,
                                           RocketMqClusterProperties bootProperties) {
        this.config = config;
        this.bootProperties = bootProperties;
    }

    /**
     * 應用啟動後加載持久化配置，若文件不存在則用啟動配置初始化。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        loadFromFile();
    }

    /**
     * 從文件加載配置。文件不存在時用啟動配置初始化。
     */
    private void loadFromFile() {
        if (Files.exists(CONFIG_FILE)) {
            try {
                Properties props = new Properties();
                try (var reader = Files.newBufferedReader(CONFIG_FILE)) {
                    props.load(reader);
                }
                String nameServersStr = props.getProperty("name-servers", "");
                if (!nameServersStr.isBlank()) {
                    config.setNameServers(parseNameServers(nameServersStr));
                } else {
                    config.setNameServers(bootProperties.resolvedNameServers());
                }
                config.setSendMsgTimeoutMs(Integer.parseInt(props.getProperty("send-msg-timeout-ms", "10000")));
                config.setConsumeTimeoutSeconds(Integer.parseInt(props.getProperty("consume-timeout-seconds", "15")));
                config.setConsumerGroupPrefix(props.getProperty("consumer-group-prefix", "mqcluster"));
                log.info("Loaded RocketMQ connection config from {}", CONFIG_FILE);
            } catch (IOException e) {
                log.warn("Failed to load config from {}, using boot properties", CONFIG_FILE, e);
                config.initFromProperties(bootProperties);
            }
        } else {
            config.initFromProperties(bootProperties);
        }
    }

    /**
     * 獲取當前配置快照。
     */
    public ConfigSnapshot getSnapshot() {
        return new ConfigSnapshot(
                config.getNameServers(),
                config.getSendMsgTimeoutMs(),
                config.getConsumeTimeoutSeconds(),
                config.getConsumerGroupPrefix()
        );
    }

    /**
     * 更新配置並持久化到文件，立即生效。
     *
     * @param snapshot 新配置
     */
    public ConfigSnapshot update(ConfigSnapshot snapshot) {
        // 驗證
        if (snapshot.sendMsgTimeoutMs() < 1000 || snapshot.sendMsgTimeoutMs() > 600000) {
            throw new IllegalArgumentException("sendMsgTimeoutMs must be between 1000 and 600000");
        }
        if (snapshot.consumeTimeoutSeconds() < 1 || snapshot.consumeTimeoutSeconds() > 300) {
            throw new IllegalArgumentException("consumeTimeoutSeconds must be between 1 and 300");
        }
        if (snapshot.consumerGroupPrefix() == null || snapshot.consumerGroupPrefix().isBlank()) {
            throw new IllegalArgumentException("consumerGroupPrefix must not be blank");
        }

        // 立即更新內存配置
        config.setNameServers(snapshot.nameServers());
        config.setSendMsgTimeoutMs(snapshot.sendMsgTimeoutMs());
        config.setConsumeTimeoutSeconds(snapshot.consumeTimeoutSeconds());
        config.setConsumerGroupPrefix(snapshot.consumerGroupPrefix());

        // 持久化到文件
        persistToFile(snapshot);

        log.info("RocketMQ connection config updated: nameServers={}, sendTimeout={}ms, consumeTimeout={}s, groupPrefix={}",
                snapshot.nameServers(), snapshot.sendMsgTimeoutMs(), snapshot.consumeTimeoutSeconds(),
                snapshot.consumerGroupPrefix());

        return getSnapshot();
    }

    private void persistToFile(ConfigSnapshot snapshot) {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            Properties props = new Properties();
            props.setProperty("name-servers", String.join(",", snapshot.nameServers()));
            props.setProperty("send-msg-timeout-ms", String.valueOf(snapshot.sendMsgTimeoutMs()));
            props.setProperty("consume-timeout-seconds", String.valueOf(snapshot.consumeTimeoutSeconds()));
            props.setProperty("consumer-group-prefix", snapshot.consumerGroupPrefix());
            try (var writer = Files.newBufferedWriter(CONFIG_FILE)) {
                props.store(writer, "MQCluster RocketMQ connection config (managed by web UI)");
            }
        } catch (IOException e) {
            log.error("Failed to persist config to {}", CONFIG_FILE, e);
            throw new IllegalStateException("Failed to save config: " + e.getMessage(), e);
        }
    }

    private List<String> parseNameServers(String raw) {
        return Arrays.stream(raw.split("[,;]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** 配置快照 record——不可變的配置視圖 */
    public record ConfigSnapshot(
            List<String> nameServers,
            int sendMsgTimeoutMs,
            int consumeTimeoutSeconds,
            String consumerGroupPrefix
    ) {}
}
