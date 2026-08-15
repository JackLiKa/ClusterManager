package com.example.clustermanager.core.port;

import com.example.clustermanager.core.model.ClusterRef;
import com.example.clustermanager.core.model.ClusterTopology;

/**
 * 拓扑读取端口（六边形架构的出站端口）。
 *
 * <p>定义领域核心层对集群拓扑读取能力的抽象，是最基础的出站端口之一。
 * infrastructure 层的适配器实现该端口，从真实或伪集群中拉取节点与链路信息。
 * 该端口不引入任何外部依赖。
 */
public interface ITopologyReader {

    /**
     * 加载指定集群的完整拓扑视图。
     *
     * @param clusterRef 目标集群引用
     * @return 集群拓扑，包含集群引用、节点列表、网络链路列表与活跃虚拟 IP
     * @throws IllegalStateException 若集群不可达或拓扑拉取失败
     */
    ClusterTopology loadTopology(ClusterRef clusterRef);
}
