package com.example.clustermanager.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 统一错误响应体，所有 {@link com.example.clustermanager.api.controller.ApiExceptionHandler} handler 都返回此类型。
 * 前端可通过 code 做差异化提示，correlationId 用于日志排错。
 *
 * <p>API 层角色：作为 REST API 错误响应的标准契约，保证前后端错误结构一致。
 * 前端 clusterApi.ts 的响应拦截器据此解包 message 并抛出 Error。
 *
 * @param code          错误码（BAD_REQUEST / CONFLICT / VALIDATION_FAILED / INTERNAL_SERVER_ERROR）
 * @param message       人类可读的错误描述
 * @param correlationId 关联 ID（UUID），用于服务端日志与前端排错联动
 * @param timestamp     错误发生时间戳
 */
public record ErrorResponse(
        String code,
        String message,
        String correlationId,
        Instant timestamp
) {

    /**
     * 工厂方法：用当前时间与新生成的 UUID 创建 ErrorResponse。
     *
     * @param code    错误码
     * @param message 错误描述
     * @return 带自动生成 correlationId 与 timestamp 的错误响应
     */
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, UUID.randomUUID().toString(), Instant.now());
    }
}
