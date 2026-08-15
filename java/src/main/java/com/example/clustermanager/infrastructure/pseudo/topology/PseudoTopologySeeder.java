package com.example.clustermanager.infrastructure.pseudo.topology;

import com.example.clustermanager.core.model.NodeStatus;
import com.example.clustermanager.infrastructure.pseudo.PseudoClusterProperties;
import com.example.clustermanager.infrastructure.pseudo.node.ManagedNode;
import com.example.clustermanager.infrastructure.pseudo.node.NodeKind;
import com.example.clustermanager.infrastructure.pseudo.node.NodeRegistry;
import com.example.clustermanager.infrastructure.pseudo.node.NodeRole;
import com.example.clustermanager.core.port.IVirtualNetwork;
import org.springframework.stereotype.Component;

/**
 * 偽集群拓撲種子化——從 PseudoClusterProvider 的 initializeIfNecessary 提取。
 *
 * <p>作為 Spring 單例 {@code @Component}，被 {@link com.example.clustermanager.infrastructure.pseudo.PseudoClusterProvider}
 * 在首次初始化時委託調用 {@link #seedIfNeeded()}。
 *
 * <p>種子化 3 個默認節點：
 * <ul>
 *   <li>{@code rmq-ns-01}——NameServer（端口 9876）</li>
 *   <li>{@code rmq-broker-m-01}——Broker Master（端口 10911）</li>
 *   <li>{@code rmq-broker-s-01}——Broker Slave（端口 10921）</li>
 * </ul>
 *
 * <p>節點初始狀態為 STOPPED（與 auto-start=false 語義一致，P2 修復）。
 * 每個節點通過 {@link IVirtualNetwork} 分配虛擬 IP 並應用隔離規則。
 *
 * <p><b>線程安全</b>：無實例可變狀態，依賴均為線程安全的組件。
 * {@link #seedIfNeeded} 通過檢查 {@code rmq-ns-01} 是否已存在保證冪等。
 */
@Component
public class PseudoTopologySeeder {

    /** 偽集群配置屬性——讀取 clusterId、tapDeviceName、cidr */
    private final PseudoClusterProperties properties;
    /** 虛擬網絡適配器——種子化時創建網絡段並分配虛擬 IP */
    private final IVirtualNetwork virtualNetwork;
    /** 節點註冊表——種子化後的節點存入此註冊表 */
    private final NodeRegistry nodeRegistry;

    /**
     * 構造器注入配置屬性、虛擬網絡和節點註冊表。
     *
     * @param properties     偽集群配置屬性
     * @param virtualNetwork 虛擬網絡適配器
     * @param nodeRegistry   節點註冊表
     */
    public PseudoTopologySeeder(
            PseudoClusterProperties properties,
            IVirtualNetwork virtualNetwork,
            NodeRegistry nodeRegistry
    ) {
        this.properties = properties;
        this.virtualNetwork = virtualNetwork;
        this.nodeRegistry = nodeRegistry;
    }

    /**
     * 確保網絡段已建立並種子化默認節點。冪等——已種子化時直接返回。
     *
     * <p>流程：
     * <ol>
     *   <li>檢查 {@code rmq-ns-01} 是否已存在（冪等判斷）</li>
     *   <li>調用 {@link IVirtualNetwork#ensureSegment} 創建網絡段</li>
     *   <li>依次種子化 NameServer、Broker Master、Broker Slave</li>
     * </ol>
     */
    public void seedIfNeeded() {
        if (nodeRegistry.contains("rmq-ns-01")) {
            return;
        }
        virtualNetwork.ensureSegment(properties.clusterId(), properties.tapDeviceName(), properties.cidr());
        seedNode("rmq-ns-01", "NameServer-01", "ns01.local", NodeRole.NAMESERVER.legacyString(), 9876);
        seedNode("rmq-broker-m-01", "Broker-Master-01", "broker-master-01.local",
                NodeRole.BROKER_MASTER.legacyString(), 10911);
        seedNode("rmq-broker-s-01", "Broker-Slave-01", "broker-slave-01.local",
                NodeRole.BROKER_SLAVE.legacyString(), 10921);
    }

    /**
     * 種子化單個節點——創建 ManagedNode、分配虛擬 IP、應用隔離、註冊到註冊表。
     *
     * <p>P2 修復：種子節點初始狀態改為 STOPPED，與默認 auto-start=false 語義一致。
     * managed=false 標記為種子化節點，不可通過 deleteService 刪除。
     *
     * @param nodeId      節點 ID
     * @param displayName 顯示名稱
     * @param hostName    主機名
     * @param role        角色字串（舊式格式）
     * @param port        邏輯端口（嵌入式運行時會動態分配實際端口）
     */
    // P2 修復: 種子節點初始狀態改為 STOPPED，與默認 auto-start=false 語義一致
    private void seedNode(String nodeId, String displayName, String hostName, String role, int port) {
        ManagedNode node = new ManagedNode(
                nodeId,
                displayName,
                hostName,
                role,
                port,
                false,
                NodeKind.VIRTUAL,
                NodeStatus.STOPPED,
                null
        );
        String virtualIp = virtualNetwork.attachNode(properties.clusterId(), nodeId).virtualIp();
        virtualNetwork.isolateNode(properties.clusterId(), nodeId);
        nodeRegistry.register(node.withAddress(virtualIp));
    }
}
