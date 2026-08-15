package com.example.clustermanager.infrastructure.pseudo;

import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 空操作 TAP 原生橋接——{@link TapNativeBridge} 的默認實現。
 *
 * <p>所有操作僅記錄 INFO 日誌，不執行任何真實的網絡操作。
 * 作為 Spring {@code @Component} 默認裝配，當未配置 {@code cluster.pseudo.tap.enabled=true} 時生效。
 *
 * <p>適用於學習/演示場景：不需要真實 TAP 設備，虛擬 IP 僅在邏輯層面分配和隔離。
 * 需要真實網絡隔離時切換為 {@link CommandTapNativeBridge}。
 */
@Component
public class NoOpTapNativeBridge implements TapNativeBridge {

    private static final Logger log = LoggerFactory.getLogger(NoOpTapNativeBridge.class);

    /**
     * 記錄創建隔離段的日誌，不執行真實操作。
     *
     * @param tapDeviceName TAP 設備名稱
     * @param cidr          CIDR 網段
     */
    @Override
    public void createIsolatedSegment(String tapDeviceName, String cidr) {
        log.info("Preparing TAP segment {} on {}", cidr, tapDeviceName);
    }

    /**
     * 記錄 IP 綁定日誌，不執行真實操作。
     *
     * @param tapDeviceName TAP 設備名稱
     * @param nodeId        節點 ID
     * @param ipAddress     虛擬 IP
     */
    @Override
    public void assignNodeIp(String tapDeviceName, String nodeId, String ipAddress) {
        log.info("Binding virtual IP {} to node {} via {}", ipAddress, nodeId, tapDeviceName);
    }

    /**
     * 記錄隔離規則應用日誌，不執行真實操作。
     *
     * @param tapDeviceName TAP 設備名稱
     * @param nodeId        目標節點 ID
     * @param allowedPeers  允許通信的節點 ID 集合
     */
    @Override
    public void applyIsolationRules(String tapDeviceName, String nodeId, Collection<String> allowedPeers) {
        log.info("Applying isolation for node {} on {}, peers={}", nodeId, tapDeviceName, allowedPeers);
    }

    /**
     * 記錄 IP 釋放日誌，不執行真實操作。
     *
     * @param tapDeviceName TAP 設備名稱
     * @param nodeId        節點 ID
     * @param ipAddress     待釋放的虛擬 IP
     */
    @Override
    public void releaseNode(String tapDeviceName, String nodeId, String ipAddress) {
        log.info("Releasing virtual IP {} from node {} via {}", ipAddress, nodeId, tapDeviceName);
    }
}
