package com.queuemate.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 50, message = "用户名长度必须为3到50个字符")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度必须为8到64个字符")
        String password,

        @NotBlank(message = "显示名称不能为空")
        @Size(max = 100, message = "显示名称最多100个字符")
        String displayName,

        @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确")
        String phone
) {
}
