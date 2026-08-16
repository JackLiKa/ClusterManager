package com.example.clustermanager.infrastructure.pseudo.runtime;

import org.apache.rocketmq.common.BrokerConfig;
import org.apache.rocketmq.remoting.netty.NettyClientConfig;
import org.apache.rocketmq.remoting.netty.NettyServerConfig;
import org.apache.rocketmq.store.config.MessageStoreConfig;

/**
 * Broker 配置構建器——從 {@link EmbeddedNodeSpec} 構建 RocketMQ 5.3.3 Broker 所需的四個配置對象。
 *
 * <p>工具類，所有方法為靜態，私有構造器防止實例化。
 * 被 {@link EmbeddedRocketMqNode#broker} 調用，構建 {@code BrokerController} 的四參構造參數。
 *
 * <p>Spike 驗證的關鍵坑：
 * <ul>
 *   <li>首次心跳不註冊 topic → 設 {@code setRegisterNameServerPeriod(2000)} 縮短註冊間隔</li>
 *   <li>直接構造 BrokerController 繞過 BrokerStartup 的 ROCKETMQ_HOME 依賴</li>
 * </ul>
 */
final class BrokerConfigBuilder {

    /** 私有構造器——工具類不可實例化 */
    private BrokerConfigBuilder() {
    }

    /**
     * 構建 Broker 業務配置。設置 broker 名稱、集群名稱、ID、NameServer 地址等。
     *
     * <p>關鍵配置：
     * <ul>
     *   <li>{@code brokerIP1} 固定為 127.0.0.1（進程內嵌入）</li>
     *   <li>啟用屬性過濾（{@code enablePropertyFilter=true}）</li>
     *   <li>縮短註冊間隔為 2 秒（Spike 修復：確保 topic 快速註冊到 NameServer）</li>
     * </ul>
     *
     * @param spec 節點規格
     * @return 配置好的 BrokerConfig
     */
    static BrokerConfig buildBrokerConfig(EmbeddedNodeSpec spec) {
        BrokerConfig config = new BrokerConfig();
        config.setBrokerName(spec.brokerName());
        config.setBrokerClusterName(spec.brokerClusterName());
        config.setBrokerId(spec.brokerId());
        config.setNamesrvAddr(spec.namesrvAddr());
        config.setBrokerIP1("127.0.0.1");
        config.setEnablePropertyFilter(true);
        // Spike 修復: 縮短註冊間隔，確保 topic 快速註冊到 NameServer
        config.setRegisterNameServerPeriod(2000);
        return config;
    }

    /**
     * 構建 Broker 的 Netty 服務器配置。設置監聽端口為 spec 中的 listenPort。
     *
     * @param spec 節點規格
     * @return 配置好的 NettyServerConfig
     */
    static NettyServerConfig buildNettyServerConfig(EmbeddedNodeSpec spec) {
        NettyServerConfig config = new NettyServerConfig();
        config.setListenPort(spec.listenPort());
        return config;
    }

    /**
     * 構建 Broker 的 Netty 客戶端配置。使用默認配置即可。
     *
     * @return 默認的 NettyClientConfig
     */
    static NettyClientConfig buildNettyClientConfig() {
        return new NettyClientConfig();
    }

    /**
     * 構建消息存儲配置。設置 commitlog 和存儲根路徑（按節點隔離），以及 HA 端口。
     *
     * @param spec 節點規格
     * @return 配置好的 MessageStoreConfig
     */
    static MessageStoreConfig buildMessageStoreConfig(EmbeddedNodeSpec spec) {
        MessageStoreConfig config = new MessageStoreConfig();
        config.setStorePathRootDir(spec.storePath().resolve("store").toString());
        config.setStorePathCommitLog(spec.storePath().resolve("store/commitlog").toString());
        if (spec.haPort() > 0) {
            config.setHaListenPort(spec.haPort());
        }
        return config;
    }
}
