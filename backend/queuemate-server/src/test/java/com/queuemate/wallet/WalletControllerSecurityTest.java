package com.queuemate.wallet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.queuemate.auth.AuthenticatedUser;
import com.queuemate.auth.JwtAuthenticationFilter;
import com.queuemate.common.exception.GlobalExceptionHandler;
import com.queuemate.config.RestAccessDeniedHandler;
import com.queuemate.config.RestAuthenticationEntryPoint;
import com.queuemate.config.SecurityConfig;
import com.queuemate.user.UserRole;
import jakarta.servlet.FilterChain;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WalletController.class)
@Import({
        SecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class
})
class WalletControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WalletService walletService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void passThroughJwtFilter() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    void anonymousCannotViewWallet() throws Exception {
        mockMvc.perform(get("/api/v1/wallets/my"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    void userCanViewWallet() throws Exception {
        when(walletService.getMine(any()))
                .thenReturn(new WalletResponse(9001L, 3001L, new BigDecimal("80.00"), WalletStatus.ACTIVE));

        mockMvc.perform(get("/api/v1/wallets/my")
                        .with(authentication(authenticationFor(3001L, UserRole.USER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("9001"))
                .andExpect(jsonPath("$.data.balance").value(80.00));
    }

    @Test
    void merchantCannotRechargeWallet() throws Exception {
        mockMvc.perform(post("/api/v1/wallets/my/recharge")
                        .with(authentication(authenticationFor(2001L, UserRole.MERCHANT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":20.00}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    void invalidRechargeAmountReturnsParameterError() throws Exception {
        mockMvc.perform(post("/api/v1/wallets/my/recharge")
                        .with(authentication(authenticationFor(3001L, UserRole.USER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAM_INVALID"));
    }

    private UsernamePasswordAuthenticationToken authenticationFor(Long id, UserRole role) {
        AuthenticatedUser principal = new AuthenticatedUser(id, role.name().toLowerCase(), role);
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
    }
}
