package com.queuemate.auth;

import com.queuemate.config.RestAuthenticationEntryPoint;
import com.queuemate.user.User;
import com.queuemate.user.UserMapper;
import com.queuemate.user.UserStatus;
import com.queuemate.user.UserRoleService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final UserMapper userMapper;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final UserRoleService userRoleService;

    public JwtAuthenticationFilter(
            JwtTokenService jwtTokenService,
            UserMapper userMapper,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            UserRoleService userRoleService
    ) {
        this.jwtTokenService = jwtTokenService;
        this.userMapper = userMapper;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.userRoleService = userRoleService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            JwtIdentity identity = jwtTokenService.parseToken(authorization.substring(BEARER_PREFIX.length()));
            User user = userMapper.selectById(identity.userId());
            if (user == null || user.getStatus() != UserStatus.ACTIVE) {
                throw new BadCredentialsException("User is unavailable");
            }

            var roles = userRoleService.rolesFor(user);
            AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getUsername(), user.getRole(), roles);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    roles.stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                            .toList()
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException | BadCredentialsException ex) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(
                    request,
                    response,
                    new BadCredentialsException("Invalid JWT", ex)
            );
        }
    }
}
