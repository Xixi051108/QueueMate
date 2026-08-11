package com.queuemate.testsupport;

import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("e2e")
@TestPropertySource(properties = "queuemate.test-support.enabled=true")
@WebMvcTest(PostmanTestDataCleanupController.class)
@Import({
        SecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class
})
class PostmanTestDataCleanupControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PostmanTestDataCleanupService cleanupService;

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
    void anonymousCannotCleanupTestData() throws Exception {
        mockMvc.perform(post("/api/v1/test-support/postman-runs/cleanup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void merchantCannotCleanupTestData() throws Exception {
        mockMvc.perform(post("/api/v1/test-support/postman-runs/cleanup")
                        .with(authentication(authenticationFor(2001L, UserRole.MERCHANT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCleanupMarkedPostmanData() throws Exception {
        when(cleanupService.cleanup(any())).thenReturn(
                new PostmanCleanupResponse("mabc1234", 1, 1, 3, 3, 1, 1, 4, 2, 0)
        );

        mockMvc.perform(post("/api/v1/test-support/postman-runs/cleanup")
                        .with(authentication(authenticationFor(1001L, UserRole.ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runId").value("mabc1234"))
                .andExpect(jsonPath("$.data.usersDeleted").value(1))
                .andExpect(jsonPath("$.data.remainingArtifacts").value(0));
    }

    @Test
    void invalidRunMarkerIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/test-support/postman-runs/cleanup")
                        .with(authentication(authenticationFor(1001L, UserRole.ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"runId\":\"unsafe marker\",\"venueId\":\"\",\"slotIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAM_INVALID"));
    }

    private String validRequest() {
        return "{\"runId\":\"mabc_1234\",\"venueId\":\"\",\"slotIds\":[]}";
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
