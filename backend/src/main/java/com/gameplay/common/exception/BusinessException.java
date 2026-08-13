package com.gameplay.common.exception;

import lombok.Getter;

/**
 * 业务异常：携带 {@link ErrorCode}，由 {@link GlobalExceptionHandler} 统一转换为错误响应。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 使用自定义提示信息（错误码不变） */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
