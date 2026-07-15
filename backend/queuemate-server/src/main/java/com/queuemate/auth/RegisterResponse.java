package com.queuemate.auth;

import com.queuemate.user.UserRole;

public record RegisterResponse(Long userId, String username, UserRole role) {
}
