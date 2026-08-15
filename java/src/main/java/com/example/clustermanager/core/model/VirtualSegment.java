package com.example.clustermanager.core.model;

import java.util.List;

/**
 * 虚拟网络段，领域核心层的不可变值对象。
 *
 * <p>描述一个 TAP 虚拟网络段的配置与已分配 IP 列表，由 {@code IVirtualNetwork#ensureSegment}
 * 创建或返回。适配器据此管理虚拟 IP 池的分配与隔离。作为 record 不可变；
 * {@link List} 字段应由适配器以不可变集合提供，以保证整体不可变性。
 */
public record VirtualSegment(
        /** 虚拟网络段唯一标识。 */
        String segmentId,
        /** TAP 虚拟设备名称，如 {@code tap0}。 */
        String tapDeviceName,
        /** 该段的 CIDR 网段地址，如 {@code 10.77.0.0/24}。 */
        String cidr,
        /** 已从该段分配出的 IP 地址列表；不应为 {@code null}。 */
        List<String> allocatedIps
) {
}
