package com.gameplay.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户账号表实体（对应 DDL 1.1 节 `user` 表）。
 */
@Data
@TableName("`user`")
public class User {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号 */
    private String username;

    /** BCrypt 密码哈希 */
    private String passwordHash;

    /** 展示昵称 */
    private String nickname;

    /** 手机号 */
    private String mobile;

    /** 邮箱 */
    private String email;

    /** 头像文件地址 */
    private String avatarUrl;

    /** 性别：0未知，1男，2女 */
    private Integer gender;

    /** 个人简介 */
    private String introduction;

    /** 账号状态：ENABLED、DISABLED */
    private String accountStatus;

    /** JWT 令牌版本，变更后旧令牌失效 */
    private Integer tokenVersion;

    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;

    /** 逻辑删除：0否，1是 */
    @TableLogic
    private Integer deleted;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
