package com.gameplay.ai.strategy;

/** 仅暴露稳定错误类别，禁止把提供商正文或鉴权信息放进异常消息。 */
public class ModelCallException extends RuntimeException {
    private final String errorCode;

    public ModelCallException(String errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
