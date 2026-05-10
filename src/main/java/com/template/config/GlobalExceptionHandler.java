package com.template.config;

import com.template.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ApiResponse<Void> handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        log.error("请求 {} {} 发生异常: {}", request.getMethod(), request.getRequestURI(), e.getMessage(), e);
        return ApiResponse.fail(500, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("请求 {} {} 发生未预期异常: {}", request.getMethod(), request.getRequestURI(), e.getMessage(), e);
        return ApiResponse.fail(500, "服务器内部错误");
    }
}
