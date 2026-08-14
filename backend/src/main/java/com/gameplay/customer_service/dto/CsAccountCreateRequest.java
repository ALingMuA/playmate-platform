package com.gameplay.customer_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建客服账号请求（FR-C01：管理员创建，客服账号不得从前台注册）。
 */
@Data
public class CsAccountCreateRequest {

    /** 客服账号名 */
    @NotBlank(message = "客服账号不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{4,32}$", message = "客服账号须为4~32位字母、数字或下划线")
    private String csAccount;

    /** 客服姓名 */
    @NotBlank(message = "客服姓名不能为空")
    @Size(max = 32, message = "客服姓名过长")
    private String csName;

    /** 联系方式 */
    @NotBlank(message = "联系方式不能为空")
    @Size(max = 20, message = "联系方式过长")
    private String contactMobile;

    /** 初始密码（客服首次登录须修改，FR-C04） */
    @NotBlank(message = "初始密码不能为空")
    @Size(min = 8, max = 32, message = "密码长度须为8~32位")
    private String initialPassword;

    /** 最大并行处理会话数 */
    private Integer maxActiveConversations;
}
