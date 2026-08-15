package com.example.clustermanager.infrastructure.pseudo.node;

/**
 * 節點類型枚舉——區分節點的運行方式。
 *
 * <p>定義了偽集群中兩種節點類型：
 * <ul>
 *   <li>{@link #VIRTUAL}——本地 JVM 嵌入式 RocketMQ，由 {@link com.example.clustermanager.infrastructure.pseudo.runtime.EmbeddedRocketMqRuntime} 管理生命週期</li>
 *   <li>{@link #HOST}——綁定真實外部地址，不啟動嵌入式運行時，僅在拓撲中標記存在</li>
 * </ul>
 *
 * <p>被 {@link ManagedNode} 持有，用於決定節點操作路徑（嵌入式啟停 vs 僅狀態標記）
 * 和消息模擬路徑（嵌入式 NameServer vs 外部 NameServer）。
 */
public enum NodeKind {
    /** 虛擬節點——本地 JVM 嵌入式 RocketMQ，有完整生命週期管理 */
    VIRTUAL,
    /** HOST 節點——綁定真實外部地址，無嵌入式運行時，初始即 RUNNING */
    HOST;

    /**
     * 從 label 字串解析節點類型。null 或非 "HOST" 時默認為 VIRTUAL。
     *
     * @param label 標籤字串（如 "HOST" 或 "VIRTUAL"），null 時返回 VIRTUAL
     * @return 對應的 {@link NodeKind}
     */
    public static NodeKind fromLabel(String label) {
        if (label == null) {
            return VIRTUAL;
        }
        return "HOST".equalsIgnoreCase(label) ? HOST : VIRTUAL;
    }
}
