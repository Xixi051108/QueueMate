package com.queuemate.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.queuemate.common.exception.BusinessException;
import com.queuemate.user.User;
import com.queuemate.user.UserMapper;
import com.queuemate.user.UserRole;
import com.queuemate.user.UserStatus;
import com.queuemate.wallet.Wallet;
import com.queuemate.wallet.WalletMapper;
import com.queuemate.wallet.WalletStatus;
import java.math.BigDecimal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final WalletMapper walletMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(
            UserMapper userMapper,
            WalletMapper walletMapper,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService
    ) {
        this.userMapper = userMapper;
        this.walletMapper = walletMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String username = request.username().trim();
        if (findByUsername(username) != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "用户名已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName().trim());
        user.setPhone(normalizePhone(request.phone()));
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "用户名已存在");
        }

        Wallet wallet = new Wallet();
        wallet.setUserId(user.getId());
        wallet.setBalance(BigDecimal.ZERO);
        wallet.setStatus(WalletStatus.ACTIVE);
        walletMapper.insert(wallet);

        return new RegisterResponse(user.getId(), user.getUsername(), user.getRole());
    }

    public LoginResponse login(LoginRequest request) {
        String username = request.username().trim();
        User user = findByUsername(username);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    "AUTH_CREDENTIALS_INVALID",
                    "用户名或密码错误"
            );
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "USER_DISABLED", "用户已被禁用");
        }

        String token = jwtTokenService.generateToken(user);
        return new LoginResponse(
                token,
                "Bearer",
                jwtTokenService.getExpireSeconds(),
                UserResponse.from(user)
        );
    }

    public UserResponse currentUser(AuthenticatedUser principal) {
        User user = userMapper.selectById(principal.id());
        if (user == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED", "登录状态无效");
        }
        return UserResponse.from(user);
    }

    private User findByUsername(String username) {
        return userMapper.selectOne(
                Wrappers.<User>lambdaQuery()
                        .eq(User::getUsername, username)
                        .last("limit 1")
        );
    }

    private String normalizePhone(String phone) {
        return phone == null || phone.isBlank() ? null : phone;
    }
}
