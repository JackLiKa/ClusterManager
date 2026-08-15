package com.example.clustermanager.core.model;

/**
 * TAP 节点附件，领域核心层的不可变值对象。
 *
 * <p>记录某节点附加到虚拟网络段后获得的虚拟 IP 绑定关系。
 * 由 {@code IVirtualNetwork#attachNode} 返回，供适配器维护节点与虚拟 IP 的映射。
 * 作为 record 不可变，三个字段均为不可变字符串引用。
 */
public record TapNodeAttachment(
        /** 虚拟网络段标识，对应 {@link VirtualSegment#segmentId()}。 */
        String segmentId,
        /** 附加到该段的节点标识，对应 {@link ClusterNode#nodeId()}。 */
        String nodeId,
        /** 分配给该节点的虚拟 IP 地址。 */
        String virtualIp
) {
}
