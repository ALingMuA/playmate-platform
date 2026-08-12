package com.gameplay.common.exception;

import com.gameplay.common.enums.ErrorCode;
import lombok.Getter;

/**
 * 业务异常：由 Service 层抛出，由 {@link GlobalExceptionHandler} 统一转换为
 * {@code Result<T>} 响应，不向客户端暴露堆栈细节。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.message);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
