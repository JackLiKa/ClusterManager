package com.example.clustermanager.core.model;

import java.time.Instant;
import java.util.List;

/**
 * 消息模拟结果，领域核心层的不可变值对象。
 *
 * <p>由 {@code IMessageWorkbench#simulate} 返回，封装模拟执行时间与全部投递结果。
 * 作为 record 不可变；{@link Instant} 与 {@link List} 应以不可变形式提供，
 * 适合在流式推送与前端展示场景下安全传递。
 */
public record MessageSimulationResult(
        /** 模拟执行的时间戳（UTC 瞬时）。 */
        Instant executedAt,
        /** 全部投递结果列表，元素为 {@link MessageDeliveryResult}；不应为 {@code null}。 */
        List<MessageDeliveryResult> deliveries
) {
}
