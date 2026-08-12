package com.gameplay.common.exception;

import com.gameplay.common.enums.ErrorCode;
import com.gameplay.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器：将业务异常、参数校验异常、权限异常和未知异常统一转换为
 * 标准 {@link Result} 响应。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.getErrorCode();
        return ResponseEntity.status(code.httpStatus)
            .body(Result.fail(code, ex.getMessage()));
    }

    /** 参数校验异常（@RequestBody + @Valid） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = firstFieldError(ex);
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.httpStatus)
            .body(Result.fail(ErrorCode.VALIDATION_FAILED, message));
    }

    /** 参数绑定异常（表单 / query 参数） */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBind(BindException ex) {
        String message = firstFieldError(ex);
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.httpStatus)
            .body(Result.fail(ErrorCode.VALIDATION_FAILED, message));
    }

    /** Spring Security 授权失败 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(Result.fail(ErrorCode.PERMISSION_DENIED));
    }

    /** 未知异常兜底 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnknown(Exception ex) {
        log.error("系统内部错误", ex);
        return ResponseEntity.status(ErrorCode.SYSTEM_ERROR.httpStatus)
            .body(Result.fail(ErrorCode.SYSTEM_ERROR));
    }

    private String firstFieldError(BindException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        return fieldError == null
            ? ErrorCode.VALIDATION_FAILED.message
            : fieldError.getField() + " " + fieldError.getDefaultMessage();
    }
}
