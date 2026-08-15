package com.example.clustermanager.core.port;

import com.example.clustermanager.core.model.ClusterRef;
import com.example.clustermanager.core.model.MessageScenario;
import com.example.clustermanager.core.model.MessageSimulationResult;

/**
 * 消息工作台端口（六边形架构的出站端口）。
 *
 * <p>定义领域核心层对消息模拟能力的抽象。infrastructure 层的适配器
 * （如 {@code EmbeddedMessageWorkbench}）实现该端口，执行真实的 produce/consume 模拟。
 * 该端口不引入任何外部依赖，仅依赖 core 层的值对象。
 */
public interface IMessageWorkbench {

    /**
     * 在指定集群上执行消息模拟。
     *
     * @param clusterRef 目标集群引用，用于解析到具体适配器
     * @param scenario   消息模拟场景，包含 topic、消费组、消息数、生产/消费者节点等参数
     * @return 模拟结果，包含执行时间与全部投递结果列表
     * @throws IllegalArgumentException 若场景参数非法（如 topic 为空、消息数为非正数）
     * @throws IllegalStateException    若集群不可达或节点状态不允许模拟
     */
    MessageSimulationResult simulate(ClusterRef clusterRef, MessageScenario scenario);
}
