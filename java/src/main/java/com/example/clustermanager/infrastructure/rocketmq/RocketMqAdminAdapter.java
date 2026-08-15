package com.example.clustermanager.infrastructure.rocketmq;

import com.example.clustermanager.core.model.ClusterNode;
import com.example.clustermanager.core.model.MessageDeliveryResult;
import com.example.clustermanager.core.model.NetworkLink;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * RocketMQ Admin API 适配器 —— 将底层 Admin 客户端操作转换为领域模型。
 *
 * <p>本类属于 infrastructure/rocketmq 层，是 REAL 模式适配器
 * {@link RocketMqClusterProvider} 的核心依赖。它封装了 {@link RocketMqAdminClient}
 * 的底层调用，将返回的快照数据（{@link RocketMqNodeSnapshot}、{@link RocketMqLinkSnapshot}）
 * 转换为核心领域模型（{@link ClusterNode}、{@link NetworkLink}、{@link MessageDeliveryResult}），
 * 使 Provider 层无需关心 Admin SDK 的数据结构细节。
 *
 * <p>本类还负责补充手工登记节点的网络链路：当存在手工登记的节点时，自动为其
 * 创建与主 NameServer 的虚拟链路，使前端拓扑图能正确展示手工节点的连接关系。
 *
 * <p><b>当前状态</b>：REAL 模式暂时搁置，专注 PSEUDO 模式。底层 Admin 客户端
 * 当前使用 {@link MockRocketMqAdminClient} 返回静态演示数据。
 *
 * @see RocketMqClusterProvider
 * @see RocketMqAdminClient
 * @see MockRocketMqAdminClient
 */
@Component
public class RocketMqAdminAdapter {

    /** 底层 Admin 客户端，负责与真实（或 Mock）RocketMQ 集群交互 */
    private final RocketMqAdminClient adminClient;

    /**
     * 构造适配器，注入底层 Admin 客户端。
     *
     * @param adminClient RocketMQ Admin 客户端实现（当前为 MockRocketMqAdminClient）
     */
    public RocketMqAdminAdapter(RocketMqAdminClient adminClient) {
        this.adminClient = adminClient;
    }

    /**
     * 从 Admin API 拉取指定集群的节点列表，并转换为领域模型 {@link ClusterNode}。
     *
     * @param clusterName 集群名称（对应 RocketMQ Dashboard 中的集群标识）
     * @return 转换后的领域节点列表
     */
    public List<ClusterNode> loadNodes(String clusterName) {
        return adminClient.fetchNodes(clusterName).stream()
                .map(node -> new ClusterNode(
                        node.nodeId(),
                        node.displayName(),
                        node.hostName(),
                        node.exposedAddress(),
                        node.status(),
                        node.labels()
                ))
                .toList();
    }

    /**
     * 从 Admin API 拉取指定集群的链路列表（内部会先加载节点列表）。
     *
     * @param clusterName 集群名称
     * @return 转换后的领域网络链路列表
     */
    public List<NetworkLink> loadLinks(String clusterName) {
        return loadLinks(clusterName, loadNodes(clusterName));
    }

    /**
     * 从 Admin API 拉取链路列表，并补充手工登记节点与主 NameServer 之间的虚拟链路。
     *
     * <p>处理逻辑：
     * <ol>
     *   <li>从 Admin API 获取原始物理链路并转换为 {@link NetworkLink}</li>
     *   <li>在节点列表中查找第一个 role=nameserver 的节点作为主 NameServer</li>
     *   <li>若存在主 NameServer，为所有 source=manual 的手工节点创建一条
     *       指向主 NameServer 的虚拟链路（类型 manual-registration，延迟 2.5ms）</li>
     * </ol>
     *
     * @param clusterName 集群名称
     * @param nodes       当前集群的完整节点列表（含手工登记节点）
     * @return 合并后的网络链路列表（物理链路 + 手工登记虚拟链路）
     */
    public List<NetworkLink> loadLinks(String clusterName, List<ClusterNode> nodes) {
        List<NetworkLink> links = new ArrayList<>(adminClient.fetchLinks(clusterName).stream()
                .map(link -> new NetworkLink(
                        link.sourceNodeId(),
                        link.targetNodeId(),
                        link.healthy(),
                        "physical-network",
                        link.latencyMs()
                ))
                .toList());
        String primaryNameServer = nodes.stream()
                .filter(node -> "nameserver".equals(node.labels().get("role")))
                .map(ClusterNode::nodeId)
                .findFirst()
                .orElse(null);
        if (primaryNameServer != null) {
            nodes.stream()
                    .filter(node -> !node.nodeId().equals(primaryNameServer))
                    .filter(node -> node.labels().containsKey("source") && "manual".equals(node.labels().get("source")))
                    .forEach(node -> links.add(new NetworkLink(primaryNameServer, node.nodeId(), true, "manual-registration", 2.5)));
        }
        return links;
    }

    /**
     * 探测消息流 —— 模拟从生产者节点向消费者节点发送指定数量的消息。
     *
     * <p>处理逻辑：
     * <ol>
     *   <li>若未指定消费者列表，回退使用手工登记节点列表作为消费者</li>
     *   <li>从 Admin API 拉取指定 topic 的最近消息</li>
     *   <li>基于消息哈希值将每条消息分配给一个消费者节点</li>
     *   <li>生成 {@link MessageDeliveryResult}，标记为通过 Admin API 探测观察到</li>
     * </ol>
     *
     * @param topic          目标 topic 名称
     * @param producerNodeId 生产者节点 ID
     * @param consumerNodes  消费者节点 ID 列表（为空时回退到手工节点）
     * @param count          探测的消息数量
     * @param manualNodes    手工登记节点列表，用于消费者回退
     * @return 消息投递结果列表
     */
    public List<MessageDeliveryResult> probeMessageFlow(
            String topic,
            String producerNodeId,
            List<String> consumerNodes,
            int count,
            List<ClusterNode> manualNodes
    ) {
        List<String> normalizedConsumers = consumerNodes == null || consumerNodes.isEmpty()
                ? fallbackConsumers(manualNodes)
                : consumerNodes;
        return adminClient.fetchMessages(topic, count).stream()
                .map(payload -> {
                    int keyIndex = Math.abs(payload.hashCode()) % normalizedConsumers.size();
                    return new MessageDeliveryResult(
                            String.valueOf(payload.get("messageKey")),
                            producerNodeId,
                            normalizedConsumers.get(keyIndex),
                            true,
                            "Observed via RocketMQ Admin API probe"
                    );
                })
                .toList();
    }

    /**
     * 探测消息流的简化重载，不传入手工节点列表（消费者回退将使用默认值）。
     *
     * @param topic          目标 topic 名称
     * @param producerNodeId 生产者节点 ID
     * @param consumerNodes  消费者节点 ID 列表
     * @param count          探测的消息数量
     * @return 消息投递结果列表
     */
    public List<MessageDeliveryResult> probeMessageFlow(String topic, String producerNodeId, List<String> consumerNodes, int count) {
        return probeMessageFlow(topic, producerNodeId, consumerNodes, count, List.of());
    }

    /**
     * 生成消费者回退列表 —— 当未指定消费者时，使用手工登记节点。
     *
     * @param manualNodes 手工登记节点列表
     * @return 消费者节点 ID 列表；若手工节点为空则回退到默认值 {@code "prod-broker-01"}
     */
    private List<String> fallbackConsumers(List<ClusterNode> manualNodes) {
        List<String> consumers = manualNodes.stream()
                .map(ClusterNode::nodeId)
                .toList();
        return consumers.isEmpty() ? List.of("prod-broker-01") : consumers;
    }

    /**
     * 对指定节点执行生命周期操作（无返回值版本）。
     *
     * <p>调用底层 Admin 客户端执行操作。若操作失败，异常会直接抛出。
     * 推荐使用 {@link #tryInvokeNodeOperation} 获取布尔返回值。
     *
     * @param nodeId    目标节点 ID
     * @param operation 操作类型名称（如 "START"、"STOP"、"RESTART"）
     * @throws RuntimeException 当 Admin API 调用失败时抛出
     */
    public void invokeNodeOperation(String nodeId, String operation) {
        adminClient.invokeBrokerLifecycle(nodeId, operation);
    }

    // P1 修复: 新增带返回值的操作入口，避免 operateNode 在 admin 调用失败时仍返回 success=true
    /**
     * 对指定节点执行生命周期操作，返回操作是否成功。
     *
     * <p>P1 修复：新增此带返回值的入口，包装 {@link #invokeNodeOperation}，
     * 捕获异常并返回 {@code false}，避免 {@link RocketMqClusterProvider#operateNode}
     * 在 Admin API 调用失败时仍向调用方返回 {@code success=true} 的错误结果。
     *
     * @param nodeId    目标节点 ID
     * @param operation 操作类型名称（如 "START"、"STOP"、"RESTART"）
     * @return {@code true} 表示操作成功；{@code false} 表示操作失败（异常被捕获）
     */
    public boolean tryInvokeNodeOperation(String nodeId, String operation) {
        try {
            adminClient.invokeBrokerLifecycle(nodeId, operation);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }
}
