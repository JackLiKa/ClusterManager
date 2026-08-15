package com.example.clustermanager.core.model;

import java.util.Map;

/**
 * 服务登记信息，领域核心层的不可变值对象。
 *
 * <p>描述用户手工登记到集群的一个服务/节点条目，包含节点标识、展示名称、角色、
 * 主机名、地址、端口及标签。由 {@code IServiceRegistry#registerService} 接收，
 * 适配器将其合并进拓扑、指标、日志等视图。作为 record 不可变；
 * {@link Map} 字段应由调用方以不可变集合提供。
 */
public record ServiceRegistration(
        /** 节点唯一标识，登记后用于拓扑与指标关联。 */
        String nodeId,
        /** 服务展示名称，用于前端显示。 */
        String displayName,
        /** 服务角色，如 {@code nameserver}、{@code broker}、{@code proxy} 等。 */
        String role,
        /** 主机名，用于网络寻址。 */
        String hostName,
        /** 服务地址（IP 或主机名），与 {@link #port} 共同构成端点。 */
        String address,
        /** 服务端口；可为 {@code null} 表示无需端口（如纯主机名寻址）。 */
        Integer port,
        /** 服务标签集合，键值对形式承载额外元数据；不应为 {@code null}。 */
        Map<String, String> labels
) {
}
