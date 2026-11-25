package com.gameplay.common.exception;

import com.gameplay.common.api.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：统一捕获业务异常、参数校验异常、常见 MVC/上传异常与未知异常，
 * 返回 {@code code/message/data} 结构，不暴露堆栈、SQL 或密钥信息。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode ec = e.getErrorCode();
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String path = (attrs != null && attrs.getRequest() != null) ? attrs.getRequest().getRequestURI() : "";
        log.warn("业务异常 code={} message={} path={}", ec.getCode(), e.getMessage(), path);
        return ResponseEntity.status(ec.getHttpStatus())
                .body(ApiResponse.error(ec.getCode(), e.getMessage()));
    }

    /** @RequestBody DTO 校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(this::formatFieldError)
                .orElse(ErrorCode.VALIDATION_FAILED.getMessage());
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED.getCode(), detail));
    }

    /** 请求参数（@RequestParam/@PathVariable）校验失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException e) {
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED.getCode(), e.getMessage()));
    }

    /** 请求体 JSON 解析失败 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED.getCode(), "请求体格式错误"));
    }

    /** 路径或查询参数类型转换失败 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED.getCode(),
                        "参数类型不正确: " + e.getName()));
    }

    /** 缺少必填请求参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED.getCode(),
                        "缺少必填参数: " + e.getParameterName()));
    }

    /** 上传文件超出 Multipart 限制 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(ErrorCode.FILE_TOO_LARGE.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.FILE_TOO_LARGE.getCode(),
                        ErrorCode.FILE_TOO_LARGE.getMessage()));
    }

    /** 不支持的 HTTP 请求方法 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED.getCode(),
                        "不支持的请求方法: " + e.getMethod()));
    }

    /** 静态资源或未知端点不存在 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NOT_FOUND", "请求的资源不存在"));
    }

    /** 方法级权限不足（@PreAuthorize） */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(ErrorCode.PERMISSION_DENIED.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.PERMISSION_DENIED.getCode(), ErrorCode.PERMISSION_DENIED.getMessage()));
    }

    /** 未知异常兜底 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception e) {
        log.error("系统内部错误", e);
        return ResponseEntity.status(ErrorCode.SYSTEM_ERROR.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMessage()));
    }

    private String formatFieldError(FieldError fe) {
        return fe.getDefaultMessage() != null ? fe.getDefaultMessage()
                : fe.getField() + " 校验失败";
    }
}
