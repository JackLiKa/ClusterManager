package com.example.clustermanager.core.model;

import java.util.List;

/**
 * 集群拓扑，领域核心层的不可变值对象。
 *
 * <p>聚合一个集群的完整拓扑视图：集群引用、节点列表、节点间网络链路列表，
 * 以及当前活跃的虚拟 IP。由 {@code ITopologyReader#loadTopology} 读取，
 * 供前端拓扑图渲染与后端编排决策使用。
 * 作为 record 不可变；{@link List} 字段应由适配器以不可变集合
 * （如 {@code List.of} 或 {@code List.copyOf}）提供，以保证整体不可变性。
 */
public record ClusterTopology(
        /** 集群引用，标识该拓扑所属的集群。 */
        ClusterRef cluster,
        /** 集群中的全部节点列表，元素为 {@link ClusterNode}；不应为 {@code null}。 */
        List<ClusterNode> nodes,
        /** 节点间的网络链路列表，元素为 {@link NetworkLink}；不应为 {@code null}。 */
        List<NetworkLink> links,
        /** 当前活跃的虚拟 IP（如 NameServer 对外暴露的 VIP）；无则可为 {@code null}。 */
        String activeVip
) {
}
