package com.queuemate.stats;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BusyHoursController.class)
@Import({
        SecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class
})
class BusyHoursControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BusyHoursService busyHoursService;

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
    void anonymousCannotViewStats() throws Exception {
        mockMvc.perform(get("/api/v1/stats/venues/4001/busy-hours")
                        .param("dateFrom", "2026-07-01")
                        .param("dateTo", "2026-07-16"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotViewStats() throws Exception {
        mockMvc.perform(get("/api/v1/stats/venues/4001/busy-hours")
                        .param("dateFrom", "2026-07-01")
                        .param("dateTo", "2026-07-16")
                        .with(authentication(authenticationFor(3001L, UserRole.USER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void merchantCanViewStats() throws Exception {
        when(busyHoursService.getBusyHours(any(), any(), any(), any()))
                .thenReturn(List.of(new BusyHourResponse("10:00", 14, 9, 23)));

        mockMvc.perform(get("/api/v1/stats/venues/4001/busy-hours")
                        .param("dateFrom", "2026-07-01")
                        .param("dateTo", "2026-07-16")
                        .with(authentication(authenticationFor(2001L, UserRole.MERCHANT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].heatScore").value(23));
    }

    @Test
    void missingDateRangeReturnsParameterError() throws Exception {
        mockMvc.perform(get("/api/v1/stats/venues/4001/busy-hours")
                        .with(authentication(authenticationFor(2001L, UserRole.MERCHANT))))
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
