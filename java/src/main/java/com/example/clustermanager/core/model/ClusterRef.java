package com.example.clustermanager.core.model;

/**
 * 集群引用，领域核心层的不可变值对象。
 *
 * <p>作为定位某个集群的三元组：集群 ID + 集群模式 + 中间件类型。
 * 该对象是所有端口方法的首参，用于在 {@code ClusterProviderRegistry} 中
 * 解析到对应的 {@code IClusterProvider} 适配器实现。
 * 作为 record 天然不可变，三个字段均为不可变引用，适合作为 Map 键或在多线程间传递。
 */
public record ClusterRef(
        /** 集群唯一标识，如 {@code local-lab}（伪集群）或 {@code rocketmq-demo}（真实集群）。 */
        String clusterId,
        /** 集群模式，区分伪集群与真实集群，取值见 {@link ClusterMode}。 */
        ClusterMode mode,
        /** 中间件类型，标识集群所承载的中间件品类，取值见 {@link MiddlewareType}。 */
        MiddlewareType middleware
) {
}
