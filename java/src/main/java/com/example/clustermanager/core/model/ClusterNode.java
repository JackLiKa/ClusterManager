package com.example.clustermanager.core.model;

import java.util.Map;

/**
 * 集群节点，领域核心层的不可变值对象。
 *
 * <p>描述单个节点在拓扑中的静态属性（标识、名称、网络地址、状态、标签），
 * 由 {@code ITopologyReader} 读取并聚合到 {@link ClusterTopology} 中。
 * 作为 record，其所有字段在构造后不可变；若传入可变集合（如 {@link Map}），
 * 调用方应自行保证不再修改，或在适配器层做防御性拷贝。
 */
public record ClusterNode(
        /** 节点唯一标识，全集群范围内不可重复，通常为稳定字符串（如 {@code rmq-ns-01}）。 */
        String nodeId,
        /** 节点展示名称，用于前端 UI 显示，可含中文或描述性文字。 */
        String displayName,
        /** 节点主机名，用于网络寻址或日志标识。 */
        String hostName,
        /** 节点虚拟 IP 地址，TAP 虚拟网络场景下由 {@code IVirtualNetwork} 分配；可为 {@code null}。 */
        String virtualIp,
        /** 节点当前运行状态，取值见 {@link NodeStatus}。 */
        NodeStatus status,
        /** 节点标签集合，键值对形式承载额外元数据（如角色、版本）；不应为 {@code null}。 */
        Map<String, String> labels
) {
}
