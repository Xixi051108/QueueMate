package com.queuemate.auth;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.queuemate.user.User;
import com.queuemate.user.UserRole;
import com.queuemate.user.UserStatus;
import java.util.Set;

public record UserResponse(
        @JsonSerialize(using = ToStringSerializer.class)
        Long id,
        String username,
        String displayName,
        String phone,
        UserRole role,
        Set<UserRole> roles,
        UserStatus status
) {

    public static UserResponse from(User user) {
        return from(user, Set.of(user.getRole()));
    }

    public static UserResponse from(User user, Set<UserRole> roles) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getPhone(),
                user.getRole(),
                roles,
                user.getStatus()
        );
    }
}
