package com.queuemate.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.queuemate.common.exception.BusinessException;
import com.queuemate.user.User;
import com.queuemate.user.UserMapper;
import com.queuemate.user.UserRole;
import com.queuemate.user.UserStatus;
import com.queuemate.wallet.Wallet;
import com.queuemate.wallet.WalletMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private WalletMapper walletMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userMapper,
                walletMapper,
                passwordEncoder,
                jwtTokenService
        );
    }

    @Test
    void registerCreatesUserAndEmptyWallet() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode("Password123")).thenReturn("{bcrypt}encoded");
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(101L);
            return 1;
        });

        RegisterResponse response = authService.register(
                new RegisterRequest("test_user", "Password123", "Test User", "13800009999")
        );

        assertThat(response.userId()).isEqualTo(101L);
        assertThat(response.role()).isEqualTo(UserRole.USER);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("{bcrypt}encoded");
        assertThat(userCaptor.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);

        ArgumentCaptor<Wallet> walletCaptor = ArgumentCaptor.forClass(Wallet.class);
        verify(walletMapper).insert(walletCaptor.capture());
        assertThat(walletCaptor.getValue().getUserId()).isEqualTo(101L);
        assertThat(walletCaptor.getValue().getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userMapper.selectOne(any())).thenReturn(activeUser());

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("alice", "Password123", "Alice", null)
        ))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("USERNAME_EXISTS");
                });

        verify(userMapper, never()).insert(any(User.class));
        verify(walletMapper, never()).insert(any(Wallet.class));
    }

    @Test
    void loginReturnsTokenForActiveUser() {
        User user = activeUser();
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("User123456", user.getPasswordHash())).thenReturn(true);
        when(jwtTokenService.generateToken(user)).thenReturn("signed.jwt.token");
        when(jwtTokenService.getExpireSeconds()).thenReturn(7200L);

        LoginResponse response = authService.login(new LoginRequest("alice", "User123456"));

        assertThat(response.token()).isEqualTo("signed.jwt.token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(7200L);
        assertThat(response.user().username()).isEqualTo("alice");
    }

    @Test
    void loginRejectsWrongPasswordWithoutRevealingWhichCredentialFailed() {
        User user = activeUser();
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("wrong-password", user.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong-password")))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getCode()).isEqualTo("AUTH_CREDENTIALS_INVALID");
                });
    }

    @Test
    void loginRejectsDisabledUser() {
        User user = activeUser();
        user.setStatus(UserStatus.DISABLED);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("User123456", user.getPasswordHash())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "User123456")))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getCode()).isEqualTo("USER_DISABLED");
                });
    }

    @Test
    void currentUserReturnsLatestDatabaseUser() {
        User user = activeUser();
        when(userMapper.selectById(3001L)).thenReturn(user);

        UserResponse response = authService.currentUser(
                new AuthenticatedUser(3001L, "alice", UserRole.USER)
        );

        assertThat(response.id()).isEqualTo(3001L);
        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
    }

    private User activeUser() {
        User user = new User();
        user.setId(3001L);
        user.setUsername("alice");
        user.setPasswordHash("{bcrypt}encoded");
        user.setDisplayName("Alice");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
