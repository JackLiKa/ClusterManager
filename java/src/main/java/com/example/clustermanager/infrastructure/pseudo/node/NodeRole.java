package com.example.clustermanager.infrastructure.pseudo.node;

/**
 * 嵌入式 RocketMQ 節點角色枚舉。
 *
 * <p>定義了進程內嵌入式集群支持的三種節點角色：
 * <ul>
 *   <li>{@link #NAMESERVER}——NameServer，服務發現與路由</li>
 *   <li>{@link #BROKER_MASTER}——Broker 主節點，負責消息存儲與讀寫</li>
 *   <li>{@link #BROKER_SLAVE}——Broker 從節點，從主節點同步數據</li>
 * </ul>
 *
 * <p>被 {@link com.example.clustermanager.infrastructure.pseudo.runtime.EmbeddedNodeSpec}、
 * {@link com.example.clustermanager.infrastructure.pseudo.runtime.EmbeddedRocketMqRuntime} 等組件用於
 * 區分節點類型並決定啟動順序和配置構建方式。
 */
public enum NodeRole {
    /** NameServer 角色——服務發現與路由中心 */
    NAMESERVER,
    /** Broker 主節點——負責消息存儲與讀寫，brokerId=0 */
    BROKER_MASTER,
    /** Broker 從節點——從主節點同步數據，brokerId=1 */
    BROKER_SLAVE;

    /**
     * 判斷是否為 Broker 類型（主或從）。
     *
     * @return 非 NameServer 時返回 true
     */
    public boolean isBroker() {
        return this != NAMESERVER;
    }

    /**
     * 判斷是否為 Broker 主節點。
     *
     * @return 僅 {@link #BROKER_MASTER} 時返回 true
     */
    public boolean isMaster() {
        return this == BROKER_MASTER;
    }

    /**
     * 從舊式 role 字串（如 "nameserver"、"broker-master"、"broker-slave"）解析。
     *
     * @param role role 字串，支持多種格式別名
     * @return 對應的 {@link NodeRole}
     * @throws IllegalArgumentException role 為 null 或未知值時拋出
     */
    public static NodeRole fromLegacyString(String role) {
        if (role == null) {
            throw new IllegalArgumentException("Role must not be null");
        }
        return switch (role.toLowerCase(java.util.Locale.ROOT)) {
            case "nameserver", "ns" -> NAMESERVER;
            case "broker-master", "broker_master", "master" -> BROKER_MASTER;
            case "broker-slave", "broker_slave", "slave" -> BROKER_SLAVE;
            default -> throw new IllegalArgumentException("Unknown role: " + role);
        };
    }

    /**
     * 返回與舊代碼兼容的 role 字串。
     *
     * @return 小寫連字串格式（如 "nameserver"、"broker-master"、"broker-slave"）
     */
    public String legacyString() {
        return switch (this) {
            case NAMESERVER -> "nameserver";
            case BROKER_MASTER -> "broker-master";
            case BROKER_SLAVE -> "broker-slave";
        };
    }
}
