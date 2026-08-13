package com.gameplay.common.exception;

import lombok.Getter;

/**
 * 全局错误码枚举。
 *
 * <p>主体来自《详细设计说明书》6.1 全局错误码枚举；
 * 末尾补充 auth 登录/注册流程必需的业务错误码（设计文档未列出）。</p>
 */
@Getter
public enum ErrorCode {

    // ===== 认证与令牌 =====
    AUTH_TOKEN_MISSING(401, "AUTH_TOKEN_MISSING", "未提供认证令牌"),
    AUTH_TOKEN_INVALID(401, "AUTH_TOKEN_INVALID", "认证令牌无效或过期"),
    AUTH_TOKEN_VERSION_MISMATCH(401, "AUTH_TOKEN_VERSION_MISMATCH", "账号登录状态已失效"),
    AUTH_ACCOUNT_DISABLED(403, "AUTH_ACCOUNT_DISABLED", "账号已被禁用"),
    AUTH_PASSWORD_CHANGE_REQUIRED(403, "AUTH_PASSWORD_CHANGE_REQUIRED", "首次登录或重置后必须修改密码"),

    // ===== 权限 =====
    PERMISSION_DENIED(403, "PERMISSION_DENIED", "无功能访问权限"),
    PERMISSION_DATA_SCOPE_DENIED(403, "PERMISSION_DATA_SCOPE_DENIED", "无数据访问权限"),
    PERMISSION_AI_WRITE_FORBIDDEN(403, "PERMISSION_AI_WRITE_FORBIDDEN", "AI不具备业务写权限"),

    // ===== 订单与档期 =====
    ORDER_NOT_FOUND(404, "ORDER_NOT_FOUND", "订单不存在"),
    ORDER_STATUS_INVALID(409, "ORDER_STATUS_INVALID", "订单当前状态不允许该操作"),
    ORDER_ALREADY_PAID(409, "ORDER_ALREADY_PAID", "订单已支付"),
    ORDER_PAYMENT_EXPIRED(409, "ORDER_PAYMENT_EXPIRED", "订单支付已超时"),
    ORDER_ACCEPT_EXPIRED(409, "ORDER_ACCEPT_EXPIRED", "订单接单已超时"),
    ORDER_SELF_BOOKING_FORBIDDEN(422, "ORDER_SELF_BOOKING_FORBIDDEN", "不能预约自己的服务"),
    SLOT_CONFLICT(409, "SLOT_CONFLICT", "预约时段冲突"),
    SERVICE_NOT_AVAILABLE(422, "SERVICE_NOT_AVAILABLE", "服务当前不可预约"),

    // ===== 钱包与幂等 =====
    WALLET_BALANCE_INSUFFICIENT(422, "WALLET_BALANCE_INSUFFICIENT", "虚拟余额不足"),
    WALLET_LEDGER_DUPLICATE(409, "WALLET_LEDGER_DUPLICATE", "资金流水重复"),
    WALLET_CONCURRENT_MODIFICATION(409, "WALLET_CONCURRENT_MODIFICATION", "钱包并发更新，请重试"),
    IDEMPOTENCY_KEY_CONFLICT(409, "IDEMPOTENCY_KEY_CONFLICT", "幂等键与请求不匹配"),

    // ===== 客服 =====
    CS_CONVERSATION_NOT_FOUND(404, "CS_CONVERSATION_NOT_FOUND", "客服会话不存在"),
    CS_CONVERSATION_CLOSED(422, "CS_CONVERSATION_CLOSED", "客服会话已关闭"),
    CS_CONVERSATION_ALREADY_CLAIMED(409, "CS_CONVERSATION_ALREADY_CLAIMED", "会话已被其他客服领取"),
    CS_AGENT_NOT_ONLINE(422, "CS_AGENT_NOT_ONLINE", "客服未在线或不可接待"),
    CS_AGENT_CAPACITY_EXCEEDED(422, "CS_AGENT_CAPACITY_EXCEEDED", "客服接待会话数已达上限"),
    CS_MESSAGE_DUPLICATE(409, "CS_MESSAGE_DUPLICATE", "客服消息重复"),
    CS_TRANSFER_REQUIRED(422, "CS_TRANSFER_REQUIRED", "当前问题需要转人工处理"),

    // ===== AI =====
    AI_SERVICE_UNAVAILABLE(503, "AI_SERVICE_UNAVAILABLE", "AI服务暂不可用"),
    AI_RESPONSE_UNTRUSTED(422, "AI_RESPONSE_UNTRUSTED", "AI回答置信度不足"),

    // ===== 通用 =====
    VALIDATION_FAILED(400, "VALIDATION_FAILED", "请求参数校验失败"),
    SYSTEM_ERROR(500, "SYSTEM_ERROR", "系统内部错误"),

    // ===== 以下为 auth 业务补充（详细设计 6.1 未列出，登录/注册流程必需） =====
    AUTH_CREDENTIAL_INVALID(401, "AUTH_CREDENTIAL_INVALID", "账号或密码错误"),
    ACCOUNT_NOT_FOUND(404, "ACCOUNT_NOT_FOUND", "账号不存在"),
    ACCOUNT_USERNAME_EXISTS(409, "ACCOUNT_USERNAME_EXISTS", "用户名已被占用"),
    ACCOUNT_MOBILE_EXISTS(409, "ACCOUNT_MOBILE_EXISTS", "手机号已被占用"),
    ACCOUNT_EMAIL_EXISTS(409, "ACCOUNT_EMAIL_EXISTS", "邮箱已被占用"),
    AUTH_PASSWORD_INCORRECT(401, "AUTH_PASSWORD_INCORRECT", "原密码不正确");

    private final int httpStatus;
    private final String code;
    private final String message;

    ErrorCode(int httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
