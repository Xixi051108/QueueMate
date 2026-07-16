package com.queuemate.booking;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
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
import java.time.LocalDateTime;
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

@WebMvcTest(BookingVoucherController.class)
@Import({
        SecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class
})
class BookingVoucherControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BookingVoucherService voucherService;

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
    void anonymousCannotRedeemVoucher() throws Exception {
        mockMvc.perform(post("/api/v1/venues/4002/booking-vouchers/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consumptionCode\":\"QMTESTCODE01\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotRedeemVoucher() throws Exception {
        mockMvc.perform(post("/api/v1/venues/4002/booking-vouchers/redeem")
                        .with(authentication(authenticationFor(3001L, UserRole.USER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consumptionCode\":\"QMTESTCODE01\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    void merchantCanRedeemVoucher() throws Exception {
        when(voucherService.redeem(eq(4002L), any(), any())).thenReturn(
                new BookingVoucherRedeemResponse(
                        6001L,
                        "BKTEST",
                        BookingStatus.FULFILLED,
                        BookingPayStatus.PAID,
                        new BigDecimal("20.00"),
                        BookingVoucherStatus.REDEEMED,
                        2001L,
                        LocalDateTime.now()
                )
        );

        mockMvc.perform(post("/api/v1/venues/4002/booking-vouchers/redeem")
                        .with(authentication(authenticationFor(2001L, UserRole.MERCHANT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consumptionCode\":\"QMTESTCODE01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FULFILLED"))
                .andExpect(jsonPath("$.data.voucherStatus").value("REDEEMED"));
    }

    @Test
    void invalidVoucherCodeReturnsParameterError() throws Exception {
        mockMvc.perform(post("/api/v1/venues/4002/booking-vouchers/redeem")
                        .with(authentication(authenticationFor(2001L, UserRole.MERCHANT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consumptionCode\":\"bad!\"}"))
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
