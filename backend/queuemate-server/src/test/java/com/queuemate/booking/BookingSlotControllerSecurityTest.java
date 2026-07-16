package com.queuemate.booking;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import java.time.LocalDate;
import java.time.LocalTime;
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

@WebMvcTest(BookingSlotController.class)
@Import({
        SecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class
})
class BookingSlotControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BookingSlotService bookingSlotService;

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
    void anonymousCanListSlots() throws Exception {
        when(bookingSlotService.list(eq(4002L), any(), any(), any()))
                .thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/venues/4002/slots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("5001"))
                .andExpect(jsonPath("$.data[0].availableCapacity").value(10));
    }

    @Test
    void invalidStatusQueryReturnsParameterError() throws Exception {
        mockMvc.perform(get("/api/v1/venues/4002/slots").queryParam("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAM_INVALID"));
    }

    @Test
    void anonymousCannotCreateSlot() throws Exception {
        mockMvc.perform(post("/api/v1/venues/4002/slots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    void userCannotCreateSlot() throws Exception {
        mockMvc.perform(post("/api/v1/venues/4002/slots")
                        .with(authentication(authenticationFor(3001L, UserRole.USER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    void merchantCanCreateSlot() throws Exception {
        when(bookingSlotService.create(eq(4002L), any(), any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/venues/4002/slots")
                        .with(authentication(authenticationFor(2001L, UserRole.MERCHANT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.venueId").value("4002"))
                .andExpect(jsonPath("$.data.status").value("OPEN"));
    }

    @Test
    void invalidCreateBodyReturnsParameterError() throws Exception {
        mockMvc.perform(post("/api/v1/venues/4002/slots")
                        .with(authentication(authenticationFor(2001L, UserRole.MERCHANT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotDate\":\"" + LocalDate.now().plusDays(1) + "\",\"capacity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAM_INVALID"));
    }

    @Test
    void merchantCanCloseSlot() throws Exception {
        BookingSlotResponse closed = new BookingSlotResponse(
                5001L,
                4002L,
                LocalDate.now().plusDays(1),
                LocalTime.of(19, 0),
                LocalTime.of(20, 0),
                12,
                2,
                10,
                new BigDecimal("20.00"),
                BookingSlotStatus.CLOSED,
                2001L
        );
        when(bookingSlotService.updateStatus(
                eq(4002L),
                eq(5001L),
                eq(BookingSlotStatus.CLOSED),
                any()
        )).thenReturn(closed);

        mockMvc.perform(patch("/api/v1/venues/4002/slots/5001/status")
                        .with(authentication(authenticationFor(2001L, UserRole.MERCHANT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
    }

    private UsernamePasswordAuthenticationToken authenticationFor(Long id, UserRole role) {
        AuthenticatedUser principal = new AuthenticatedUser(id, role.name().toLowerCase(), role);
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
    }

    private String validCreateBody() {
        return """
                {
                  "slotDate": "%s",
                  "startTime": "19:00:00",
                  "endTime": "20:00:00",
                  "capacity": 12,
                  "price": 20.00
                }
                """.formatted(LocalDate.now().plusDays(1));
    }

    private BookingSlotResponse sampleResponse() {
        return new BookingSlotResponse(
                5001L,
                4002L,
                LocalDate.now().plusDays(1),
                LocalTime.of(19, 0),
                LocalTime.of(20, 0),
                12,
                2,
                10,
                new BigDecimal("20.00"),
                BookingSlotStatus.OPEN,
                2001L
        );
    }
}
