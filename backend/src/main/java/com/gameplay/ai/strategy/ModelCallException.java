package com.gameplay.ai.strategy;

/** 仅暴露稳定错误类别，禁止把提供商正文或鉴权信息放进异常消息。 */
public class ModelCallException extends RuntimeException {
    private final String errorCode;
    private final Integer httpStatus;

    public ModelCallException(String errorCode) {
        this(errorCode, null);
    }

    public ModelCallException(String errorCode, Integer httpStatus) {
        super(errorCode);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String errorCode() {
        return errorCode;
    }
    public Integer httpStatus() { return httpStatus; }
}
