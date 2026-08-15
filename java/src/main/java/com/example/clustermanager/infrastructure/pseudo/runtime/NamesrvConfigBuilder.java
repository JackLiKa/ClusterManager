package com.example.clustermanager.infrastructure.pseudo.runtime;

import org.apache.rocketmq.common.namesrv.NamesrvConfig;
import org.apache.rocketmq.remoting.netty.NettyServerConfig;

/**
 * NameServer 配置構建器——從 {@link EmbeddedNodeSpec} 構建 RocketMQ 4.9.8 NameServer 所需配置對象。
 *
 * <p>工具類，所有方法為靜態，私有構造器防止實例化。
 * 被 {@link EmbeddedRocketMqNode#nameserver} 調用。
 *
 * <p>Spike 驗證關鍵點：NameServer 的監聽端口通過 {@code NettyServerConfig.setListenPort()}
 * 設置（不是 setBindPort）。
 */
final class NamesrvConfigBuilder {

    /** 私有構造器——工具類不可實例化 */
    private NamesrvConfigBuilder() {
    }

    /**
     * 構建 NameServer 的 Netty 服務器配置。設置監聽端口為 spec 中的 listenPort。
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
     * 構建 NameServer 業務配置。使用默認值即可——進程內嵌入無需特殊配置。
     *
     * @return 默認的 NamesrvConfig
     */
    static NamesrvConfig buildNamesrvConfig() {
        return new NamesrvConfig();
    }
}
