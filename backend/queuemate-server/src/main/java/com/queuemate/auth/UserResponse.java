package com.queuemate.auth;

import com.queuemate.user.User;
import com.queuemate.user.UserRole;
import com.queuemate.user.UserStatus;

public record UserResponse(
        Long id,
        String username,
        String displayName,
        String phone,
        UserRole role,
        UserStatus status
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getPhone(),
                user.getRole(),
                user.getStatus()
        );
    }
}
