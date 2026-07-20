package com.queuemate.user;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class UserRoleService {

    private final UserRoleMapper userRoleMapper;

    public UserRoleService(UserRoleMapper userRoleMapper) {
        this.userRoleMapper = userRoleMapper;
    }

    public Set<UserRole> rolesFor(User user) {
        LinkedHashSet<UserRole> roles = new LinkedHashSet<>(userRoleMapper.selectRolesByUserId(user.getId()));
        roles.add(user.getRole());
        return Set.copyOf(roles);
    }

    public void grantRole(Long userId, UserRole role, Long grantedBy) {
        userRoleMapper.insertRole(userId, role, grantedBy);
    }
}
