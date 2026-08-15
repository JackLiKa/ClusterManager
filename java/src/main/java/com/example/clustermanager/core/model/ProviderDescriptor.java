package com.example.clustermanager.core.model;

/**
 * Provider 描述符，领域核心层的不可变值对象。
 *
 * <p>描述一个 {@code IClusterProvider} 适配器的元信息：标识、展示名称、
 * 支持的集群模式与中间件类型。由 {@code IClusterProvider#descriptor} 返回，
 * 供 {@code ClusterProviderRegistry} 注册与按 {@link ClusterRef} 解析适配器使用。
 * 作为 record 不可变，三个维度字段均为不可变引用。
 */
public record ProviderDescriptor(
        /** Provider 唯一标识，如 {@code pseudo-rocketmq}。 */
        String providerId,
        /** Provider 展示名称，用于前端选择列表显示。 */
        String displayName,
        /** 该 Provider 支持的集群模式，取值见 {@link ClusterMode}。 */
        ClusterMode mode,
        /** 该 Provider 支持的中间件类型，取值见 {@link MiddlewareType}。 */
        MiddlewareType middleware
) {
}
