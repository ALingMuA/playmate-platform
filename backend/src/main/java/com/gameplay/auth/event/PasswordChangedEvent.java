package com.gameplay.auth.event;

/**
 * 密码变更事件：改密成功后发布，供客服模块清除"强制改密"标志（FR-C04）。
 *
 * @param userId 变更密码的用户ID
 */
public record PasswordChangedEvent(Long userId) {
}
