package com.example.clustermanager.infrastructure.pseudo;

import java.util.Collection;

/**
 * TAP 原生橋接接口——抽象底層 TAP 設備的網絡操作。
 *
 * <p>定義了虛擬網絡隔離所需的四個核心操作，由兩個實現提供不同策略：
 * <ul>
 *   <li>{@link NoOpTapNativeBridge}——默認實現，僅記錄日誌不執行真實操作（學習/演示場景）</li>
 *   <li>{@link CommandTapNativeBridge}——通過外部命令行工具執行真實 TAP 操作（需 {@code cluster.pseudo.tap.enabled=true}）</li>
 * </ul>
 *
 * <p>被 {@link TapVirtualNetwork} 持有並委託調用，實現網絡操作與業務邏輯解耦。
 */
public interface TapNativeBridge {

    /**
     * 創建隔離的網絡段（TAP 設備 + CIDR 網段）。
     *
     * @param tapDeviceName TAP 設備名稱
     * @param cidr          CIDR 網段
     */
    void createIsolatedSegment(String tapDeviceName, String cidr);

    /**
     * 為節點綁定虛擬 IP 到 TAP 設備。
     *
     * @param tapDeviceName TAP 設備名稱
     * @param nodeId        節點 ID
     * @param ipAddress     待綁定的虛擬 IP
     */
    void assignNodeIp(String tapDeviceName, String nodeId, String ipAddress);

    /**
     * 應用隔離規則——限制節點只能與指定的 peer 節點通信。
     *
     * @param tapDeviceName TAP 設備名稱
     * @param nodeId        目標節點 ID
     * @param allowedPeers  允許通信的節點 ID 集合
     */
    void applyIsolationRules(String tapDeviceName, String nodeId, Collection<String> allowedPeers);

    /**
     * 釋放節點的虛擬 IP 並從 TAP 設備移除綁定。
     *
     * @param tapDeviceName TAP 設備名稱
     * @param nodeId        節點 ID
     * @param ipAddress     待釋放的虛擬 IP
     */
    void releaseNode(String tapDeviceName, String nodeId, String ipAddress);
}
