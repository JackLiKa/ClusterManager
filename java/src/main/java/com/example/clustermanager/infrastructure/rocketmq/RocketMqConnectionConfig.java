package com.example.clustermanager.infrastructure.rocketmq;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 連接運行時配置——可變的連接參數持有器。
 *
 * <p>與 {@link RocketMqClusterProperties}（不可變 record，啟動時綁定）不同，
 * 本類持有可在運行時通過 API 修改的連接參數。修改後立即生效，並持久化到文件。
 *
 * <p>被以下組件依賴：
 * <ul>
 *   <li>{@link RealRocketMqAdminClient} — produce/consume 時讀取 NameServer 地址和超時</li>
 *   <li>{@link com.example.clustermanager.infrastructure.pseudo.messaging.EmbeddedMessageWorkbench} — HOST 路徑後備 NameServer</li>
 *   <li>{@link RocketMqClusterProvider} — simulate 時讀取 NameServer 地址</li>
 * </ul>
 */
@Component
public class RocketMqConnectionConfig {

    private static final Logger log = LoggerFactory.getLogger(RocketMqConnectionConfig.class);

    private volatile List<String> nameServers = List.of();
    private volatile int sendMsgTimeoutMs = 10000;
    private volatile int consumeTimeoutSeconds = 15;
    private volatile String consumerGroupPrefix = "mqcluster";

    /**
     * 從啟動配置初始化運行時配置。
     *
     * @param properties 啟動時綁定的配置
     */
    public void initFromProperties(RocketMqClusterProperties properties) {
        this.nameServers = properties.resolvedNameServers();
        log.info("RocketMqConnectionConfig initialized: nameServers={}, sendTimeout={}ms, consumeTimeout={}s",
                nameServers, sendMsgTimeoutMs, consumeTimeoutSeconds);
    }

    public List<String> getNameServers() {
        return nameServers;
    }

    public void setNameServers(List<String> nameServers) {
        this.nameServers = nameServers == null ? List.of() : List.copyOf(nameServers);
    }

    /** 返回分號分隔的 NameServer 地址字符串（RocketMQ 客戶端格式） */
    public String resolvedNameServerString() {
        return nameServers.isEmpty() ? "" : String.join(";", nameServers);
    }

    public int getSendMsgTimeoutMs() {
        return sendMsgTimeoutMs;
    }

    public void setSendMsgTimeoutMs(int sendMsgTimeoutMs) {
        this.sendMsgTimeoutMs = sendMsgTimeoutMs;
    }

    public int getConsumeTimeoutSeconds() {
        return consumeTimeoutSeconds;
    }

    public void setConsumeTimeoutSeconds(int consumeTimeoutSeconds) {
        this.consumeTimeoutSeconds = consumeTimeoutSeconds;
    }

    public String getConsumerGroupPrefix() {
        return consumerGroupPrefix;
    }

    public void setConsumerGroupPrefix(String consumerGroupPrefix) {
        this.consumerGroupPrefix = consumerGroupPrefix;
    }
}
