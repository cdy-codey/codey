package com.codey.web.api;

import com.codey.web.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.server.ResponseStatusException;

/**
 * 统一 REST 异常响应，避免前端同时适配多种错误结构。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Object>> handleResponseStatusException(ResponseStatusException exception) {
        HttpStatus status = exception.getStatus();
        LOGGER.warn("REST 请求失败: status={}, reason={}", status.value(), exception.getReason(), exception);
        return ResponseEntity
                .status(status)
                .body(ApiResponse.failure(status.name(), exception.getReason(), null));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            BindException.class
    })
    public ResponseEntity<ApiResponse<Object>> handleBadRequest(Exception exception) {
        LOGGER.warn("REST 请求参数异常: {}", exception.getMessage(), exception);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure("BAD_REQUEST", "请求参数格式不正确", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception exception) {
        LOGGER.error("REST 请求发生未处理异常", exception);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure("INTERNAL_SERVER_ERROR", "服务执行失败", null));
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ApiResponse<Object>> handleThrowable(Throwable throwable) {
        LOGGER.error("REST 请求发生严重异常", throwable);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure("INTERNAL_SERVER_ERROR", "服务执行失败", null));
    }
}
