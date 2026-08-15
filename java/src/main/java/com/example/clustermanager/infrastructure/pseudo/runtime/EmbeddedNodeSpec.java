package com.example.clustermanager.infrastructure.pseudo.runtime;

import com.example.clustermanager.infrastructure.pseudo.node.NodeRole;
import java.nio.file.Path;

/**
 * 嵌入式 RocketMQ 節點規格——描述一個待啟動的節點。
 *
 * <p>record 保證不可變。端口由 {@link PortPool} 動態分配。
 * 存儲路徑按節點隔離，避免多 broker 衝突。
 *
 * <p>被 {@link EmbeddedRocketMqRuntime#buildSpec} 構建，傳遞給
 * {@link EmbeddedRocketMqNode#nameserver} 或 {@link EmbeddedRocketMqNode#broker}
 * 用於初始化 RocketMQ 控制器。也用於運行時查詢節點規格（端口、角色等）。
 *
 * @param nodeId            節點唯一標識
 * @param role              節點角色（NAMESERVER / BROKER_MASTER / BROKER_SLAVE）
 * @param listenPort        監聽端口（由 PortPool 動態分配）
 * @param haPort            高可用端口（僅 Broker 使用，NameServer 為 -1）
 * @param storePath         存儲根路徑（按節點 ID 隔離）
 * @param brokerClusterName Broker 集群名稱（僅 Broker 使用，NameServer 為 null）
 * @param brokerName        Broker 名稱（僅 Broker 使用，NameServer 為 null）
 * @param brokerId          Broker ID（主=0，從=1；NameServer 為 -1）
 * @param namesrvAddr       NameServer 地址（僅 Broker 使用，NameServer 為 null）
 */
public record EmbeddedNodeSpec(
        String nodeId,
        NodeRole role,
        int listenPort,
        int haPort,
        Path storePath,
        String brokerClusterName,
        String brokerName,
        int brokerId,
        String namesrvAddr
) {

    /**
     * 創建 NameServer 節點規格。
     *
     * <p>NameServer 不需要 HA 端口、broker 集群名稱等字段，這些設為 -1 或 null。
     *
     * @param nodeId        節點 ID
     * @param listenPort    監聽端口
     * @param baseStorePath 存儲基路徑（會自動 resolve nodeId 子目錄）
     * @return NameServer 節點規格
     */
    public static EmbeddedNodeSpec nameserver(String nodeId, int listenPort, Path baseStorePath) {
        return new EmbeddedNodeSpec(
                nodeId,
                NodeRole.NAMESERVER,
                listenPort,
                -1,
                baseStorePath.resolve(nodeId),
                null,
                null,
                -1,
                null
        );
    }

    /**
     * 創建 Broker 節點規格。
     *
     * @param nodeId            節點 ID
     * @param role              Broker 角色（必須為 BROKER_MASTER 或 BROKER_SLAVE）
     * @param listenPort        監聽端口
     * @param haPort            HA 端口（主從同步用）
     * @param baseStorePath     存儲基路徑（會自動 resolve nodeId 子目錄）
     * @param brokerClusterName Broker 集群名稱
     * @param brokerName        Broker 名稱
     * @param brokerId          Broker ID（主=0，從=1）
     * @param namesrvAddr       NameServer 地址（用於 broker 註冊）
     * @return Broker 節點規格
     * @throws IllegalArgumentException role 不是 Broker 類型時拋出
     */
    public static EmbeddedNodeSpec broker(
            String nodeId,
            NodeRole role,
            int listenPort,
            int haPort,
            Path baseStorePath,
            String brokerClusterName,
            String brokerName,
            int brokerId,
            String namesrvAddr
    ) {
        if (!role.isBroker()) {
            throw new IllegalArgumentException("Role must be a broker type: " + role);
        }
        return new EmbeddedNodeSpec(
                nodeId,
                role,
                listenPort,
                haPort,
                baseStorePath.resolve(nodeId),
                brokerClusterName,
                brokerName,
                brokerId,
                namesrvAddr
        );
    }
}
