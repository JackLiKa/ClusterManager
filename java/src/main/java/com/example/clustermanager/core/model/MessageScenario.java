package com.example.clustermanager.core.model;

import java.util.List;
import java.util.Map;

/**
 * 消息模拟场景，领域核心层的不可变值对象。
 *
 * <p>描述一次消息模拟的全部输入参数：目标 topic、消费组、消息数量、负载模板、
 * 生产者节点、消费者节点列表及附加消息头。由 {@code IMessageWorkbench#simulate}
 * 接收并执行。作为 record 不可变；集合字段应由调用方以不可变集合提供。
 */
public record MessageScenario(
        /** 目标 Topic 名称。 */
        String topic,
        /** 消费者组名称，用于标识一组共同消费同一 Topic 的消费者。 */
        String consumerGroup,
        /** 本次模拟要发送的消息总条数，应为正整数。 */
        int messageCount,
        /** 消息负载模板，用于生成消息体；可含占位符由适配器替换。 */
        String payloadTemplate,
        /** 生产者节点标识，对应 {@link ClusterNode#nodeId()}。 */
        String producerNodeId,
        /** 消费者节点标识列表，元素对应 {@link ClusterNode#nodeId()}；不应为 {@code null}。 */
        List<String> consumerNodeIds,
        /** 附加消息头，键值对形式；不应为 {@code null}。 */
        Map<String, String> headers
) {
}
