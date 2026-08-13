package com.gameplay.common.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一响应体。
 *
 * <p>详细设计 2.1 通用约定：成功响应 {@code {"code": "SUCCESS", "message": "操作成功", "data": {}}}。</p>
 */
@Getter
@AllArgsConstructor
public class ApiResponse<T> {

    private final String code;
    private final String message;
    private final T data;

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("SUCCESS", "操作成功", data);
    }

    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
