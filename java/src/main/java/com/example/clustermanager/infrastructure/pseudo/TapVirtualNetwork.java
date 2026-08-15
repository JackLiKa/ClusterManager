package com.example.clustermanager.infrastructure.pseudo;

import com.example.clustermanager.core.model.TapNodeAttachment;
import com.example.clustermanager.core.model.VirtualSegment;
import com.example.clustermanager.core.port.IVirtualNetwork;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * TAP 虛擬網絡實現——實現 {@link IVirtualNetwork} 端口。
 *
 * <p>為 PSEUDO 模式的虛擬節點提供網絡隔離層：
 * <ul>
 *   <li><b>CIDR 池管理</b>：每個網絡段（segment）維護獨立的 {@link CidrAddressPool}，
 *       從配置的 CIDR 網段中分配/回收虛擬 IP</li>
 *   <li><b>隔離規則</b>：節點附加後調用 {@link TapNativeBridge#applyIsolationRules}
 *       應用網絡隔離，限制節點間通信範圍</li>
 *   <li><b>原生橋接</b>：通過 {@link TapNativeBridge} 抽象底層 TAP 設備操作，
 *       默認使用 {@link NoOpTapNativeBridge}（僅日誌），可切換為 {@link CommandTapNativeBridge}</li>
 * </ul>
 *
 * <p><b>線程安全</b>：{@code segments} 使用 {@link ConcurrentHashMap}；
 * {@code SegmentState} 內部的 IP 分配/釋放使用 {@code synchronized} 保護原子性。
 *
 * <p><b>協作關係</b>：被 {@link PseudoClusterProvider} 和 {@link PseudoTopologySeeder}
 * 調用，用於種子化和手動註冊節點時的虛擬 IP 分配與隔離。
 */
@Component
public class TapVirtualNetwork implements IVirtualNetwork {

    /** 原生 TAP 橋接器——抽象底層 TAP 設備創建、IP 綁定、隔離規則應用等操作 */
    private final TapNativeBridge nativeBridge;
    /** 網絡段狀態表——segmentId → SegmentState，線程安全 */
    private final Map<String, SegmentState> segments = new ConcurrentHashMap<>();

    /**
     * 構造器注入原生橋接器。
     *
     * @param nativeBridge TAP 原生橋接實現（NoOp 或 Command）
     */
    public TapVirtualNetwork(TapNativeBridge nativeBridge) {
        this.nativeBridge = nativeBridge;
    }

    /**
     * 確保網絡段已建立。若段不存在則創建並初始化 CIDR 地址池。
     *
     * @param segmentId    段標識（通常為 clusterId）
     * @param tapDeviceName TAP 設備名稱
     * @param cidr         CIDR 網段
     * @return 段的快照（含設備名、CIDR、已分配 IP 列表）
     */
    @Override
    public VirtualSegment ensureSegment(String segmentId, String tapDeviceName, String cidr) {
        SegmentState state = segments.computeIfAbsent(segmentId, key -> {
            nativeBridge.createIsolatedSegment(tapDeviceName, cidr);
            return new SegmentState(segmentId, tapDeviceName, cidr, new CidrAddressPool(cidr));
        });
        return state.snapshot();
    }

    /**
     * 附加節點到網絡段（自動分配 IP）。等同於調用 {@link #attachNode(String, String, String)} 且 requestedVirtualIp 為 null。
     *
     * @param segmentId 段標識
     * @param nodeId    節點 ID
     * @return 節點附加信息（含分配的虛擬 IP）
     */
    @Override
    public TapNodeAttachment attachNode(String segmentId, String nodeId) {
        return attachNode(segmentId, nodeId, null);
    }

    /**
     * 附加節點到網絡段，可指定請求的虛擬 IP。
     *
     * <p>分配 IP 後調用原生橋接器綁定 IP 並應用隔離規則。
     *
     * @param segmentId       段標識
     * @param nodeId          節點 ID
     * @param requestedVirtualIp 請求的虛擬 IP（null 或空串時自動分配）
     * @return 節點附加信息（含最終分配的虛擬 IP）
     * @throws IllegalArgumentException 段未初始化或 IP 分配失敗時拋出
     */
    @Override
    public TapNodeAttachment attachNode(String segmentId, String nodeId, String requestedVirtualIp) {
        SegmentState state = requireState(segmentId);
        String virtualIp = state.allocate(nodeId, requestedVirtualIp);
        nativeBridge.assignNodeIp(state.tapDeviceName, nodeId, virtualIp);
        nativeBridge.applyIsolationRules(state.tapDeviceName, nodeId, state.attachedNodeIds());
        return new TapNodeAttachment(segmentId, nodeId, virtualIp);
    }

    /**
     * 對指定節點應用隔離規則。重新計算允許通信的節點列表並更新隔離策略。
     *
     * @param segmentId 段標識
     * @param nodeId    目標節點 ID
     * @throws IllegalArgumentException 段未初始化時拋出
     */
    @Override
    public void isolateNode(String segmentId, String nodeId) {
        SegmentState state = requireState(segmentId);
        nativeBridge.applyIsolationRules(state.tapDeviceName, nodeId, state.attachedNodeIds());
    }

    /**
     * 從網絡段分離節點，釋放其虛擬 IP 回地址池。
     *
     * @param segmentId 段標識
     * @param nodeId    待分離節點 ID
     * @throws IllegalArgumentException 段未初始化時拋出
     */
    @Override
    public void detachNode(String segmentId, String nodeId) {
        SegmentState state = requireState(segmentId);
        String ipAddress = state.release(nodeId);
        if (ipAddress != null) {
            nativeBridge.releaseNode(state.tapDeviceName, nodeId, ipAddress);
        }
    }

    /**
     * 獲取指定段的狀態，不存在時拋出異常。
     *
     * @param segmentId 段標識
     * @return 段狀態
     * @throws IllegalArgumentException 段未初始化時拋出
     */
    private SegmentState requireState(String segmentId) {
        SegmentState state = segments.get(segmentId);
        if (state == null) {
            throw new IllegalArgumentException("Segment not initialized: " + segmentId);
        }
        return state;
    }

    /**
     * 網絡段內部狀態——封裝 TAP 設備名、CIDR、地址池和節點附加表。
     *
     * <p>IP 分配/釋放使用 {@code synchronized} 保證原子性（地址池的「檢查-添加」操作）。
     * 節點附加表使用 {@link ConcurrentHashMap} 支持並發讀。
     */
    private static final class SegmentState {
        /** 段標識 */
        private final String segmentId;
        /** TAP 設備名稱 */
        private final String tapDeviceName;
        /** CIDR 網段 */
        private final String cidr;
        /** IP 地址池——管理此段內的虛擬 IP 分配/回收 */
        private final CidrAddressPool addressPool;
        /** 節點附加表——nodeId → 已分配的虛擬 IP，線程安全 */
        private final Map<String, String> attachments = new ConcurrentHashMap<>();

        private SegmentState(String segmentId, String tapDeviceName, String cidr, CidrAddressPool addressPool) {
            this.segmentId = segmentId;
            this.tapDeviceName = tapDeviceName;
            this.cidr = cidr;
            this.addressPool = addressPool;
        }

        /**
         * 為節點分配虛擬 IP。若節點已附加則返回已分配的 IP（冪等）。
         *
         * @param nodeId          節點 ID
         * @param requestedVirtualIp 請求的 IP（null 時自動分配）
         * @return 分配的虛擬 IP
         */
        private synchronized String allocate(String nodeId, String requestedVirtualIp) {
            return attachments.computeIfAbsent(nodeId, ignored ->
                    requestedVirtualIp == null || requestedVirtualIp.isBlank()
                            ? addressPool.allocate()
                            : addressPool.allocate(requestedVirtualIp));
        }

        /**
         * 釋放節點的虛擬 IP。從附加表移除並歸還到地址池。
         *
         * @param nodeId 節點 ID
         * @return 被釋放的 IP 地址（節點未附加時返回 null）
         */
        private synchronized String release(String nodeId) {
            String ipAddress = attachments.remove(nodeId);
            if (ipAddress != null) {
                addressPool.release(ipAddress);
            }
            return ipAddress;
        }

        /**
         * 返回當前已附加的所有節點 ID 集合。
         *
         * @return 節點 ID 集合
         */
        private Collection<String> attachedNodeIds() {
            return attachments.keySet();
        }

        /**
         * 生成段的不可變快照。
         *
         * @return 包含設備名、CIDR、已分配 IP 列表的 {@link VirtualSegment}
         */
        private VirtualSegment snapshot() {
            return new VirtualSegment(segmentId, tapDeviceName, cidr, new ArrayList<>(attachments.values()));
        }
    }
}
