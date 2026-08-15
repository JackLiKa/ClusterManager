package com.example.clustermanager.core.port;

import com.example.clustermanager.core.model.TapNodeAttachment;
import com.example.clustermanager.core.model.VirtualSegment;

/**
 * 虚拟网络端口（六边形架构的出站端口）。
 *
 * <p>定义领域核心层对 TAP 虚拟网络管理能力的抽象，包括网段创建、节点附加、
 * 隔离与脱离。infrastructure 层的适配器（如 {@code TapVirtualNetwork}）实现该端口，
 * 管理虚拟 IP 池的分配与网络隔离。该端口不引入任何外部依赖。
 */
public interface IVirtualNetwork {

    /**
     * 确保指定虚拟网络段存在，不存在则创建。
     *
     * @param segmentId     虚拟网络段唯一标识
     * @param tapDeviceName TAP 虚拟设备名称，如 {@code tap0}
     * @param cidr          该段的 CIDR 网段地址，如 {@code 10.77.0.0/24}
     * @return 虚拟网络段对象，包含已分配 IP 列表
     * @throws IllegalArgumentException 若 CIDR 格式非法或段标识已存在且参数冲突
     */
    VirtualSegment ensureSegment(String segmentId, String tapDeviceName, String cidr);

    /**
     * 将节点附加到指定虚拟网络段，自动分配虚拟 IP。
     *
     * @param segmentId 虚拟网络段标识
     * @param nodeId    待附加的节点标识
     * @return TAP 节点附件，包含段标识、节点标识与分配的虚拟 IP
     * @throws IllegalArgumentException 若段不存在或节点已附加到该段
     * @throws IllegalStateException    若 IP 池已耗尽无法分配
     */
    TapNodeAttachment attachNode(String segmentId, String nodeId);

    /**
     * 将节点附加到指定虚拟网络段，使用指定的虚拟 IP。
     *
     * @param segmentId         虚拟网络段标识
     * @param nodeId            待附加的节点标识
     * @param requestedVirtualIp 请求使用的虚拟 IP 地址
     * @return TAP 节点附件，包含段标识、节点标识与实际分配的虚拟 IP
     * @throws IllegalArgumentException 若段不存在、IP 不在段网段范围内或 IP 已被占用
     */
    TapNodeAttachment attachNode(String segmentId, String nodeId, String requestedVirtualIp);

    /**
     * 隔离指定虚拟网络段中的节点，阻断其网络通信但不脱离段。
     *
     * @param segmentId 虚拟网络段标识
     * @param nodeId    待隔离的节点标识
     * @throws IllegalArgumentException 若段或节点附件不存在
     */
    void isolateNode(String segmentId, String nodeId);

    /**
     * 将节点从指定虚拟网络段脱离，释放其占用的虚拟 IP。
     *
     * @param segmentId 虚拟网络段标识
     * @param nodeId    待脱离的节点标识
     * @throws IllegalArgumentException 若段或节点附件不存在
     */
    void detachNode(String segmentId, String nodeId);
}
