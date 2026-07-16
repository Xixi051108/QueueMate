package com.queuemate.booking;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

@WebMvcTest(BookingController.class)
@Import({
        SecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class
})
class BookingControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BookingService bookingService;

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
    void anonymousCannotCreateBooking() throws Exception {
        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":5001}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    void userCanCreateBooking() throws Exception {
        when(bookingService.create(any(), any())).thenReturn(sampleBooking());

        mockMvc.perform(post("/api/v1/bookings")
                        .with(authentication(authenticationFor(3001L, UserRole.USER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":5001}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("6001"))
                .andExpect(jsonPath("$.data.status").value("BOOKED"))
                .andExpect(jsonPath("$.data.payStatus").value("NOT_REQUIRED"));
    }

    @Test
    void merchantCannotCreateBooking() throws Exception {
        mockMvc.perform(post("/api/v1/bookings")
                        .with(authentication(authenticationFor(2001L, UserRole.MERCHANT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":5001}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    void invalidCreateBodyReturnsParameterError() throws Exception {
        mockMvc.perform(post("/api/v1/bookings")
                        .with(authentication(authenticationFor(3001L, UserRole.USER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAM_INVALID"));
    }

    @Test
    void userCanListOwnBookings() throws Exception {
        when(bookingService.listMine(eq(BookingStatus.BOOKED), any()))
                .thenReturn(List.of(sampleBooking()));

        mockMvc.perform(get("/api/v1/bookings/my")
                        .with(authentication(authenticationFor(3001L, UserRole.USER)))
                        .queryParam("status", "BOOKED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value("3001"));
    }

    @Test
    void invalidBookingStatusFilterReturnsParameterError() throws Exception {
        mockMvc.perform(get("/api/v1/bookings/my")
                        .with(authentication(authenticationFor(3001L, UserRole.USER)))
                        .queryParam("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAM_INVALID"));
    }

    @Test
    void userCanCancelOwnBooking() throws Exception {
        BookingResponse cancelled = new BookingResponse(
                6001L,
                "BKTEST0001",
                3001L,
                4002L,
                5001L,
                BookingStatus.CANCELLED,
                BookingPayStatus.NOT_REQUIRED,
                BigDecimal.ZERO,
                "plan changed",
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now()
        );
        when(bookingService.cancel(eq(6001L), any(), any())).thenReturn(cancelled);

        mockMvc.perform(patch("/api/v1/bookings/6001/cancel")
                        .with(authentication(authenticationFor(3001L, UserRole.USER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"plan changed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelReason").value("plan changed"));
    }

    @Test
    void merchantCannotCancelBooking() throws Exception {
        mockMvc.perform(patch("/api/v1/bookings/6001/cancel")
                        .with(authentication(authenticationFor(2001L, UserRole.MERCHANT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    void unknownResourceReturnsStableNotFoundResponse() throws Exception {
        mockMvc.perform(get("/api/v1/unknown-resource")
                        .with(authentication(authenticationFor(3001L, UserRole.USER))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void unsupportedMethodReturnsStableMethodNotAllowedResponse() throws Exception {
        mockMvc.perform(put("/api/v1/bookings")
                        .with(authentication(authenticationFor(3001L, UserRole.USER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    private UsernamePasswordAuthenticationToken authenticationFor(Long id, UserRole role) {
        AuthenticatedUser principal = new AuthenticatedUser(id, role.name().toLowerCase(), role);
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
    }

    private BookingResponse sampleBooking() {
        return new BookingResponse(
                6001L,
                "BKTEST0001",
                3001L,
                4002L,
                5001L,
                BookingStatus.BOOKED,
                BookingPayStatus.NOT_REQUIRED,
                BigDecimal.ZERO,
                null,
                LocalDateTime.now(),
                null
        );
    }
}
