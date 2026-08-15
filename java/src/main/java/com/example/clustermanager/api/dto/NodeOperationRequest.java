package com.example.clustermanager.api.dto;

import com.example.clustermanager.core.model.OperationType;
import jakarta.validation.constraints.NotNull;

/**
 * 节点操作请求 DTO，对应 POST /api/clusters/{...}/nodes/{nodeId}/operations 端点的请求体。
 *
 * <p>API 层角色：承载前端 OperationsPanel 提交的节点操作（启动/停止/重启等），
 * 经 {@link com.example.clustermanager.api.controller.ClusterController} 转换为
 * {@link com.example.clustermanager.application.model.NodeOperationCommand} 后
 * 委托 {@link com.example.clustermanager.application.service.ClusterFacadeService} 执行。
 *
 * @param operationType 操作类型（START/STOP/RESTART 等），不能为 null
 */
public record NodeOperationRequest(
        @NotNull OperationType operationType
) {
}
