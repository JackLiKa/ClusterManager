package com.example.clustermanager.infrastructure.pseudo.node;

import com.example.clustermanager.core.model.NodeStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 節點註冊表——管理偽集群中所有節點的元數據與狀態。
 *
 * <p>取代 PseudoClusterProvider 中的 {@code Map<String, ManagedPseudoNode>} 字段。
 * 作為 Spring 單例 {@code @Component}，被多個組件共享注入。
 *
 * <p><b>線程安全</b>：使用 {@link ConcurrentHashMap} 存儲節點，支持並發讀寫。
 * 狀態變更通過 {@link #transition(String, NodeStatus)} 使用
 * {@code computeIfPresent} 原子操作，返回新的不可變 {@link ManagedNode} 實例替換舊值。
 *
 * <p><b>生命週期</b>：隨應用啟動創建，節點由 {@link PseudoTopologySeeder} 種子化
 * 或由 {@link PseudoClusterProvider#registerService} 手動註冊。
 *
 * <p>被以下組件依賴：
 * <ul>
 *   <li>{@link PseudoClusterProvider}——節點 CRUD 與狀態流轉</li>
 *   <li>{@link com.example.clustermanager.infrastructure.pseudo.runtime.EmbeddedRocketMqRuntime}——查詢節點規格</li>
 *   <li>{@link com.example.clustermanager.infrastructure.pseudo.messaging.EmbeddedMessageWorkbench}——查詢 producer/consumer 節點</li>
 *   <li>{@link PseudoTopologySeeder}——種子化時註冊節點</li>
 * </ul>
 */
@Component
public class NodeRegistry {

    /** 節點存儲——nodeId → ManagedNode，線程安全 */
    private final Map<String, ManagedNode> nodes = new ConcurrentHashMap<>();

    /**
     * 註冊一個節點到註冊表。若 nodeId 已存在則覆蓋。
     *
     * @param node 待註冊的節點
     */
    public void register(ManagedNode node) {
        nodes.put(node.nodeId(), node);
    }

    /**
     * 從註冊表移除節點。
     *
     * @param nodeId 節點 ID
     * @return 被移除的節點（不存在時返回 null）
     */
    public ManagedNode remove(String nodeId) {
        return nodes.remove(nodeId);
    }

    /**
     * 獲取節點，不存在時返回 null。
     *
     * @param nodeId 節點 ID
     * @return 節點實例或 null
     */
    public ManagedNode get(String nodeId) {
        return nodes.get(nodeId);
    }

    /**
     * 獲取節點，不存在時拋出異常。
     *
     * @param nodeId 節點 ID
     * @return 節點實例
     * @throws IllegalArgumentException 節點不存在時拋出
     */
    public ManagedNode require(String nodeId) {
        ManagedNode node = nodes.get(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("Node not found: " + nodeId);
        }
        return node;
    }

    /**
     * 判斷節點是否已註冊。
     *
     * @param nodeId 節點 ID
     * @return 已註冊時返回 true
     */
    public boolean contains(String nodeId) {
        return nodes.containsKey(nodeId);
    }

    /**
     * 狀態流轉——返回新 ManagedNode 實例並替換註冊表中的舊值。
     * 使用 {@code computeIfPresent} 保證原子性。
     *
     * @param nodeId    節點 ID
     * @param newStatus 新狀態
     * @return 更新後的節點實例
     * @throws IllegalArgumentException 節點不存在時拋出
     */
    public ManagedNode transition(String nodeId, NodeStatus newStatus) {
        ManagedNode updated = nodes.computeIfPresent(nodeId, (id, node) -> node.withStatus(newStatus));
        if (updated == null) {
            throw new IllegalArgumentException("Node not found: " + nodeId);
        }
        return updated;
    }

    /**
     * 綁定地址——返回新 ManagedNode 實例並替換註冊表中的舊值。
     * 使用 {@code computeIfPresent} 保證原子性。
     *
     * @param nodeId  節點 ID
     * @param address 新地址
     * @return 更新後的節點實例
     * @throws IllegalArgumentException 節點不存在時拋出
     */
    public ManagedNode bindAddress(String nodeId, String address) {
        ManagedNode updated = nodes.computeIfPresent(nodeId, (id, node) -> node.withAddress(address));
        if (updated == null) {
            throw new IllegalArgumentException("Node not found: " + nodeId);
        }
        return updated;
    }

    /**
     * 返回所有節點，按 nodeId 字典序排列。
     *
     * @return 有序節點列表
     */
    public List<ManagedNode> ordered() {
        return nodes.values().stream()
                .sorted(Comparator.comparing(ManagedNode::nodeId))
                .toList();
    }

    /**
     * 返回所有 Broker 類型節點（主+從），按 nodeId 排列。
     *
     * @return Broker 節點列表
     */
    public List<ManagedNode> brokers() {
        return ordered().stream()
                .filter(node -> node.nodeRole().isBroker())
                .toList();
    }

    /**
     * 返回所有 HOST 類型節點，按 nodeId 排列。
     *
     * @return HOST 節點列表
     */
    public List<ManagedNode> hostNodes() {
        return ordered().stream()
                .filter(ManagedNode::hostBound)
                .toList();
    }

    /**
     * 清空註冊表中所有節點。
     */
    public void clear() {
        nodes.clear();
    }
}
