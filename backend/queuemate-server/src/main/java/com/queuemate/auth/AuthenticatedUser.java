package com.queuemate.auth;

import com.queuemate.user.UserRole;
import java.util.Set;

public record AuthenticatedUser(Long id, String username, UserRole role, Set<UserRole> roles) {

    public AuthenticatedUser(Long id, String username, UserRole role) {
        this(id, username, role, Set.of(role));
    }

    public boolean hasRole(UserRole requiredRole) {
        return roles.contains(requiredRole);
    }
}
