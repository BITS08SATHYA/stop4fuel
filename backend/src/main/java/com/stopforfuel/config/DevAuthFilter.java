package com.stopforfuel.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
@Profile("dev")
public class DevAuthFilter extends OncePerRequestFilter {

    /**
     * Must be a real users.id. The subject is what {@code SecurityUtils.getCurrentUserId()}
     * parses, so a non-numeric placeholder made every dev request an unattributed actor —
     * audit rows with no author, and the delete throttle skipped entirely.
     */
    @Value("${app.dev.user-id:1}")
    private String devUserId;

    @Value("${app.dev.role:OWNER}")
    private String devRole;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            Map<String, Object> principal = Map.of(
                "sub", devUserId,
                "email", "owner@stopforfuel.com",
                "name", "Dev Owner",
                "custom:role", devRole,
                "custom:scid", "1"
            );

            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + devRole))
            );

            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        filterChain.doFilter(request, response);
    }
}
