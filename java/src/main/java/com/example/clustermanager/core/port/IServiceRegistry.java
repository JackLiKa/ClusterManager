package com.example.clustermanager.core.port;

import com.example.clustermanager.core.model.ClusterRef;
import com.example.clustermanager.core.model.OperationResult;
import com.example.clustermanager.core.model.ServiceRegistration;

/**
 * 服务登记端口（六边形架构的出站端口）。
 *
 * <p>定义领域核心层对手工服务登记与删除能力的抽象。
 * infrastructure 层的适配器实现该端口，将登记的服务合并进拓扑、指标、日志等视图。
 * 该端口不引入任何外部依赖。
 */
public interface IServiceRegistry {

    /**
     * 向指定集群登记一个服务/节点。
     *
     * @param clusterRef   目标集群引用
     * @param registration 服务登记信息，包含节点标识、角色、地址、端口等
     * @return 操作结果，包含目标标识、成败与提示信息
     * @throws IllegalArgumentException 若登记信息字段非法（如 nodeId 为空）
     * @throws IllegalStateException    若节点 ID 已存在且不允许重复登记
     */
    OperationResult registerService(ClusterRef clusterRef, ServiceRegistration registration);

    /**
     * 从指定集群删除已登记的服务/节点。
     *
     * @param clusterRef 目标集群引用
     * @param nodeId     待删除的节点标识
     * @return 操作结果，包含目标标识、成败与提示信息
     * @throws IllegalArgumentException 若 nodeId 为空
     * @throws IllegalStateException    若节点不存在或正在运行无法删除
     */
    OperationResult deleteService(ClusterRef clusterRef, String nodeId);
}
