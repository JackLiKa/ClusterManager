package com.example.clustermanager.api.controller;

import com.example.clustermanager.api.dto.ErrorResponse;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局 REST API 异常处理器，统一将控制器抛出的异常转换为 {@link ErrorResponse} 响应体。
 *
 * <p>API 层角色：作为所有 {@code @RestController} 的横切异常处理点，保证错误响应结构一致，
 * 便于前端（clusterApi.ts 响应拦截器）统一解包与提示。
 *
 * <p>异常到 HTTP 状态码映射规则：
 * <ul>
 *   <li>{@link IllegalArgumentException} → 400 BAD_REQUEST（code=BAD_REQUEST）</li>
 *   <li>{@link IllegalStateException} → 409 CONFLICT（code=CONFLICT）——P0 修复，避免未捕获导致 500</li>
 *   <li>{@link MethodArgumentNotValidException} → 400 BAD_REQUEST（code=VALIDATION_FAILED，
 *       message 聚合所有字段校验错误）</li>
 *   <li>其他 {@link Exception} → 500 INTERNAL_SERVER_ERROR（code=INTERNAL_SERVER_ERROR）</li>
 * </ul>
 *
 * <p>correlationId 生成：每个响应都带一个 UUID correlationId，用于服务端日志与前端排错联动。
 * 对于 4xx 错误，correlationId 由 {@link ErrorResponse#of} 生成并记录 WARN 日志；
 * 对于 500 错误，correlationId 单独生成并写入响应 message，同时记录完整堆栈的 ERROR 日志，
 * 避免向前端泄露内部细节。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * 处理非法参数异常，返回 400。
     *
     * @param exception 业务层抛出的非法参数异常
     * @return 包含 BAD_REQUEST code 与异常消息的统一错误响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException exception) {
        ErrorResponse response = ErrorResponse.of("BAD_REQUEST", exception.getMessage());
        log.warn("Bad request [{}]: {}", response.correlationId(), exception.getMessage());
        return response;
    }

    // P0 修复: 兜底处理 IllegalStateException，避免未捕获异常导致 HTTP 500 且前端无提示
    /**
     * 处理非法状态异常，返回 409。对应 P0 修复，避免状态冲突被兜底为 500。
     *
     * @param exception 业务层抛出的非法状态异常（如节点状态不允许该操作）
     * @return 包含 CONFLICT code 与异常消息的统一错误响应
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleIllegalState(IllegalStateException exception) {
        ErrorResponse response = ErrorResponse.of("CONFLICT", exception.getMessage());
        log.warn("Conflict [{}]: {}", response.correlationId(), exception.getMessage());
        return response;
    }

    /**
     * 处理请求体校验失败异常，返回 400。将所有字段错误聚合为一条 message。
     *
     * @param exception @Valid 校验失败时抛出
     * @return 包含 VALIDATION_FAILED code 与聚合校验消息的统一错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        ErrorResponse response = ErrorResponse.of("VALIDATION_FAILED", message);
        log.warn("Validation failed [{}]: {}", response.correlationId(), message);
        return response;
    }

    /**
     * 兜底处理所有未捕获异常，返回 500。correlationId 写入响应 message 供前端反馈，
     * 服务端记录完整堆栈 ERROR 日志以便排查。
     *
     * @param exception 未被其他 handler 匹配的异常
     * @return 包含 INTERNAL_SERVER_ERROR code 与带 correlationId 提示的统一错误响应
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneric(Exception exception) {
        String correlationId = UUID.randomUUID().toString();
        ErrorResponse response = new ErrorResponse(
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred (correlationId: " + correlationId + ")",
                correlationId,
                java.time.Instant.now()
        );
        log.error("Unexpected error [{}]", correlationId, exception);
        return response;
    }
}
