package com.gameplay.common.result;

import com.gameplay.common.enums.ErrorCode;
import lombok.Getter;

/**
 * 统一 API 响应包装。
 *
 * <p>所有 REST 接口均返回 {@code Result<T>}，前端通过 code 判断业务成败。</p>
 *
 * @param <T> 业务数据类型
 */
@Getter
public class Result<T> {

    /** HTTP 状态码（与 ErrorCode.httpStatus 一致） */
    private final int httpStatus;

    /** 业务错误码，成功时为 SUCCESS */
    private final String code;

    /** 提示信息 */
    private final String message;

    /** 业务数据 */
    private final T data;

    private Result(int httpStatus, String code, String message, T data) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "SUCCESS", "操作成功", data);
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.httpStatus, errorCode.code, errorCode.message, null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.httpStatus, errorCode.code, message, null);
    }

    public static <T> Result<T> fail(int httpStatus, String code, String message) {
        return new Result<>(httpStatus, code, message, null);
    }
}
