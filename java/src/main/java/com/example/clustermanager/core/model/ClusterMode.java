package com.example.clustermanager.core.model;

/**
 * 集群模式枚举。
 *
 * <p>位于六边形架构的领域核心层（core），用于区分平台所管理的两类集群形态。
 * 该枚举是 {@code ClusterProviderRegistry} 选择适配器的关键维度之一
 * （另一个维度为 {@link MiddlewareType}），本身不携带任何外部依赖。
 * 作为 Java 枚举，其实例天然不可变且线程安全。
 */
public enum ClusterMode {
    /** 伪集群模式：在本地进程内编排模拟节点（如嵌入式 RocketMQ），用于学习与演示。 */
    PSEUDO,
    /** 真实集群模式：对接真实部署的中间件集群（如远端 RocketMQ NameServer）。 */
    REAL
}
