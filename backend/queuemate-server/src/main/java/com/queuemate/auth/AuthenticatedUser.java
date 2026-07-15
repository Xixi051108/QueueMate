package com.queuemate.auth;

import com.queuemate.user.UserRole;

public record AuthenticatedUser(Long id, String username, UserRole role) {
}
