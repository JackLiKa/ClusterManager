package com.example.clustermanager.infrastructure.pseudo.node;

import com.example.clustermanager.core.model.ClusterNode;
import com.example.clustermanager.core.model.NodeStatus;
import java.util.Map;

/**
 * 受管理的偽集群節點——取代舊的 ManagedPseudoNode。
 *
 * <p>使用 record 保證不可變。狀態變更通過 {@link #withStatus(NodeStatus)} 返回新實例，
 * 由 {@link NodeRegistry} 管理狀態流轉。所有字段在構造時確定，運行時僅 status 可通過
 * with* 方法衍生新實例。
 *
 * <p>被以下組件依賴：
 * <ul>
 *   <li>{@link NodeRegistry}——存儲和流轉節點狀態</li>
 *   <li>{@link com.example.clustermanager.infrastructure.pseudo.PseudoClusterProvider}——節點操作與拓撲構建</li>
 *   <li>{@link com.example.clustermanager.infrastructure.pseudo.runtime.EmbeddedRocketMqRuntime}——根據節點規格啟動嵌入式實例</li>
 *   <li>{@link com.example.clustermanager.infrastructure.pseudo.messaging.EmbeddedMessageWorkbench}——根據節點類型選擇消息路徑</li>
 * </ul>
 *
 * @param nodeId      節點唯一標識（如 "rmq-ns-01"）
 * @param displayName 顯示名稱（如 "NameServer-01"）
 * @param hostName    主機名（如 "ns01.local"）
 * @param role        角色字串（舊式格式，如 "nameserver"、"broker-master"）
 * @param port        邏輯端口（種子節點的配置端口，嵌入式運行時會動態分配實際端口）
 * @param managed     是否為手動註冊的節點（true=可刪除，false=種子化節點不可刪除）
 * @param nodeKind    節點類型（VIRTUAL=嵌入式，HOST=外部綁定）
 * @param status      當前狀態（STOPPED / STARTING / RUNNING / FAILED）
 * @param address     地址（VIRTUAL 節點為虛擬 IP，HOST 節點為 "ip:port" 格式）
 */
public record ManagedNode(
        String nodeId,
        String displayName,
        String hostName,
        String role,
        int port,
        boolean managed,
        NodeKind nodeKind,
        NodeStatus status,
        String address
) {

    /**
     * 緊湊構造器——校驗必填字段。
     *
     * @throws IllegalArgumentException nodeId 或 role 為空時拋出
     */
    public ManagedNode {
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId must not be blank");
        }
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role must not be blank");
        }
    }

    /**
     * 判斷是否為 HOST 綁定節點。
     *
     * @return nodeKind 為 {@link NodeKind#HOST} 時返回 true
     */
    public boolean hostBound() {
        return nodeKind == NodeKind.HOST;
    }

    /**
     * 將 role 字串解析為 {@link NodeRole} 枚舉。
     *
     * @return 對應的節點角色枚舉
     * @throws IllegalArgumentException role 字串無法解析時拋出
     */
    public NodeRole nodeRole() {
        return NodeRole.fromLegacyString(role);
    }

    /**
     * 衍生一個僅修改 status 的新實例（不可變更新模式）。
     *
     * @param newStatus 新狀態
     * @return 新的 {@link ManagedNode} 實例
     */
    public ManagedNode withStatus(NodeStatus newStatus) {
        return new ManagedNode(nodeId, displayName, hostName, role, port, managed, nodeKind, newStatus, address);
    }

    /**
     * 衍生一個僅修改 address 的新實例（不可變更新模式）。
     *
     * @param newAddress 新地址
     * @return 新的 {@link ManagedNode} 實例
     */
    public ManagedNode withAddress(String newAddress) {
        return new ManagedNode(nodeId, displayName, hostName, role, port, managed, nodeKind, status, newAddress);
    }

    /**
     * 轉換為核心層 {@link ClusterNode} 模型，供拓撲展示使用。
     *
     * <p>將偽集群特有的元數據打包到 labels 中：
     * <ul>
     *   <li>role——節點角色</li>
     *   <li>port——邏輯端口</li>
     *   <li>networkIsolation——網絡隔離類型（"tap" 或 "host"）</li>
     *   <li>managed——是否可刪除</li>
     *   <li>nodeKind——節點類型</li>
     * </ul>
     *
     * @return 核心層 ClusterNode 實例
     */
    public ClusterNode toClusterNode() {
        return new ClusterNode(
                nodeId,
                displayName,
                hostName,
                address,
                status,
                Map.of(
                        "role", role,
                        "port", String.valueOf(port),
                        "networkIsolation", hostBound() ? "host" : "tap",
                        "managed", String.valueOf(managed),
                        "nodeKind", nodeKind.name()
                )
        );
    }
}
