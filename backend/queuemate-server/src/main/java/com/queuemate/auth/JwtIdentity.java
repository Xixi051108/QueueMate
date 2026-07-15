package com.queuemate.auth;

import com.queuemate.user.UserRole;

public record JwtIdentity(Long userId, String username, UserRole role) {
}
