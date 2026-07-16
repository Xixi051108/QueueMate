package com.queuemate.venue;

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

@WebMvcTest(VenueController.class)
@Import({
        SecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class
})
class VenueControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VenueService venueService;

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
    void anonymousCanListVenues() throws Exception {
        when(venueService.list(any(), any(), any())).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/venues"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("4001"));
    }

    @Test
    void invalidCategoryQueryReturnsParameterError() throws Exception {
        mockMvc.perform(get("/api/v1/venues").queryParam("category", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAM_INVALID"));
    }

    @Test
    void anonymousCannotCreateVenue() throws Exception {
        mockMvc.perform(post("/api/v1/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    void userCannotCreateVenue() throws Exception {
        mockMvc.perform(post("/api/v1/venues")
                        .with(authentication(authenticationFor(3001L, UserRole.USER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    void merchantCanCreateVenue() throws Exception {
        VenueResponse response = sampleResponse();
        when(venueService.create(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/venues")
                        .with(authentication(authenticationFor(2001L, UserRole.MERCHANT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.merchantId").value("2001"));
    }

    @Test
    void invalidCreateBodyReturnsParameterError() throws Exception {
        mockMvc.perform(post("/api/v1/venues")
                        .with(authentication(authenticationFor(2001L, UserRole.MERCHANT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"category\":\"TEA_SHOP\"}"))
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

    private String validCreateBody() {
        return """
                {
                  "name": "QueueMate 新地点",
                  "category": "TEA_SHOP",
                  "description": "模拟地点",
                  "addressText": "模拟地址",
                  "queueEnabled": true,
                  "bookingEnabled": false,
                  "defaultPrice": 0.00
                }
                """;
    }

    private VenueResponse sampleResponse() {
        return new VenueResponse(
                4001L,
                "QueueMate 奶茶店 A",
                VenueCategory.TEA_SHOP,
                "模拟奶茶店",
                2001L,
                "模拟商业街 1 号",
                true,
                false,
                BigDecimal.ZERO,
                VenueStatus.ACTIVE
        );
    }
}
