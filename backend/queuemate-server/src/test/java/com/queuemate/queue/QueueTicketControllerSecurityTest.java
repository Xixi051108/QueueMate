package com.queuemate.queue;

import static org.mockito.ArgumentMatchers.any;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
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

@WebMvcTest(QueueTicketController.class)
@Import({
        SecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class
})
class QueueTicketControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QueueTicketService ticketService;

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
    void anonymousCannotTakeTicket() throws Exception {
        mockMvc.perform(post("/api/v1/venues/4001/queue/tickets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    void userCanTakeTicket() throws Exception {
        when(ticketService.take(any(), any())).thenReturn(ticketResponse(QueueTicketStatus.WAITING));

        mockMvc.perform(post("/api/v1/venues/4001/queue/tickets")
                        .with(authentication(authenticationFor(3001L, UserRole.USER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("7001"))
                .andExpect(jsonPath("$.data.status").value("WAITING"));
    }

    @Test
    void currentQueueIsPublic() throws Exception {
        when(ticketService.current(any(), any())).thenReturn(
                new QueueCurrentResponse(4001L, LocalDate.now(), 1, 2, 1, 1, List.of())
        );

        mockMvc.perform(get("/api/v1/venues/4001/queue/tickets/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.venueId").value("4001"));
    }

    @Test
    void userCannotCallTicket() throws Exception {
        mockMvc.perform(patch("/api/v1/queue/tickets/7001/call")
                        .with(authentication(authenticationFor(3001L, UserRole.USER))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    void merchantCanCallTicket() throws Exception {
        when(ticketService.call(any(), any())).thenReturn(ticketResponse(QueueTicketStatus.CALLED));

        mockMvc.perform(patch("/api/v1/queue/tickets/7001/call")
                        .with(authentication(authenticationFor(2001L, UserRole.MERCHANT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CALLED"));
    }

    @Test
    void myTicketsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/queue/tickets/my"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTicketIdReturnsParameterError() throws Exception {
        mockMvc.perform(patch("/api/v1/queue/tickets/0/call")
                        .with(authentication(authenticationFor(2001L, UserRole.MERCHANT))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAM_INVALID"));
    }

    private QueueTicketResponse ticketResponse(QueueTicketStatus status) {
        return new QueueTicketResponse(
                7001L,
                "QTTEST",
                4001L,
                1,
                LocalDate.now(),
                status,
                LocalDateTime.now(),
                status == QueueTicketStatus.CALLED ? LocalDateTime.now() : null,
                null,
                null
        );
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
