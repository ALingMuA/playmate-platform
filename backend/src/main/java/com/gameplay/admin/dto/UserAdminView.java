package com.gameplay.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端用户视图（FR-M03：用户查询）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAdminView {

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
    /** 角色编码列表 */
    private List<String> roles;
    /** 陪玩师资格状态（非陪玩师为 null） */
    private String companionCertificationStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
