package com.gameplay.auth.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户信息视图（不含密码哈希等敏感字段）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileView {

    private Long id;
    private String username;
    private String nickname;
    private String mobile;
    private String email;
    private String avatarUrl;
    /** 性别：0未知，1男，2女 */
    private Integer gender;
    private String introduction;
    private String accountStatus;
    /** 角色编码列表：USER、COMPANION、CUSTOMER_SERVICE、ADMIN */
    private List<String> roles;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginAt;
}
