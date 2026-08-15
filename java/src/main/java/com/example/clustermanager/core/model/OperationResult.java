package com.example.clustermanager.core.model;

/**
 * 节点操作结果，领域核心层的不可变值对象。
 *
 * <p>由 {@code INodeManager#operateNode} 与 {@code IServiceRegistry} 的注册/删除方法返回，
 * 封装操作的目标、类型、成败与提示信息。作为 record 不可变，
 * 适合在异步推送与前端反馈场景下安全传递。
 */
public record OperationResult(
        /** 操作目标标识，通常为节点 ID 或服务 ID。 */
        String targetId,
        /** 操作类型，取值见 {@link OperationType}；服务登记场景可为 {@code null}。 */
        OperationType operationType,
        /** 操作是否成功：{@code true} 表示操作已成功完成。 */
        boolean success,
        /** 操作结果描述信息；成功时为摘要，失败时为错误原因。 */
        String message
) {
}
