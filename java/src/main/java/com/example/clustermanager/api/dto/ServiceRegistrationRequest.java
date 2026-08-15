package com.example.clustermanager.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * 服务登记请求 DTO，对应 POST /api/clusters/{...}/services 端点的请求体。
 *
 * <p>API 层角色：承载前端 OperationsPanel 提交的手工服务节点登记信息，
 * 经 {@link com.example.clustermanager.api.controller.ClusterController} 转换为
 * {@link com.example.clustermanager.core.model.ServiceRegistration} 后
 * 委托 {@link com.example.clustermanager.application.service.ClusterFacadeService} 登记。
 *
 * @param nodeId      服务节点唯一标识，不能为空白
 * @param displayName 展示名称，不能为空白
 * @param role        节点角色（如 NAMESERVER/BROKER），不能为空白
 * @param hostName    主机名，不能为空白
 * @param address     网络地址，不能为空白
 * @param port        端口号，不能为 null
 * @param labels      自定义标签键值对，可为 null（控制器会转为空 Map）
 */
public record ServiceRegistrationRequest(
        @NotBlank String nodeId,
        @NotBlank String displayName,
        @NotBlank String role,
        @NotBlank String hostName,
        @NotBlank String address,
        @NotNull Integer port,
        Map<String, String> labels
) {
}
