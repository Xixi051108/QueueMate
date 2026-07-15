package com.queuemate.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.queuemate.user.User;
import com.queuemate.user.UserRole;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setIssuer("QueueMateTest");
        properties.setExpireSeconds(7200);
        properties.setSecret("test-secret-that-is-at-least-32-bytes-long");
        jwtTokenService = new JwtTokenService(properties);
    }

    @Test
    void generatedTokenContainsExpectedIdentity() {
        User user = new User();
        user.setId(3001L);
        user.setUsername("alice");
        user.setRole(UserRole.USER);

        String token = jwtTokenService.generateToken(user);
        JwtIdentity identity = jwtTokenService.parseToken(token);

        assertThat(identity.userId()).isEqualTo(3001L);
        assertThat(identity.username()).isEqualTo("alice");
        assertThat(identity.role()).isEqualTo(UserRole.USER);
        assertThat(jwtTokenService.getExpireSeconds()).isEqualTo(7200L);
    }

    @Test
    void tamperedTokenIsRejected() {
        User user = new User();
        user.setId(3001L);
        user.setUsername("alice");
        user.setRole(UserRole.USER);

        String token = jwtTokenService.generateToken(user);
        int signatureStart = token.lastIndexOf('.') + 1;
        char signatureFirstChar = token.charAt(signatureStart);
        String tampered = token.substring(0, signatureStart)
                + (signatureFirstChar == 'a' ? 'b' : 'a')
                + token.substring(signatureStart + 1);

        assertThatThrownBy(() -> jwtTokenService.parseToken(tampered))
                .isInstanceOf(JwtException.class);
    }
}
