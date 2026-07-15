package com.queuemate.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(max = 50, message = "用户名最多50个字符")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(max = 64, message = "密码最多64个字符")
        String password
) {
}
