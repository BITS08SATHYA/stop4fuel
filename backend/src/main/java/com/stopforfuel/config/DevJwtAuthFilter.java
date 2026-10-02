package com.stopforfuel.config;

import com.nimbusds.jwt.JWTClaimsSet;
import com.stopforfuel.backend.enums.EntityStatus;
import com.stopforfuel.backend.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

@Component
public class DevJwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public DevJwtAuthFilter(JwtTokenProvider jwtTokenProvider, UserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
    }

    private static final String AUTH_COOKIE_NAME = "sff-auth-session";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = null;

        // 1. Check Authorization header first
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        // 2. Fallback: check httpOnly cookie
        if (token == null && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (AUTH_COOKIE_NAME.equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }

        if (token != null) {
            JWTClaimsSet claims = jwtTokenProvider.validateToken(token);

            if (claims != null) {
                try {
                    // The half-finished login token from /api/auth/login is signed with the same
                    // key and would otherwise pass as a full session, letting a correct passcode
                    // alone bypass TOTP for its 5-minute lifetime. Only /api/auth/mfa/verify may
                    // accept it, and that endpoint checks the purpose claim itself.
                    if (JwtTokenProvider.MFA_PURPOSE.equals(claims.getStringClaim("purpose"))) {
                        filterChain.doFilter(request, response);
                        return;
                    }

                    String role = claims.getStringClaim("custom:role");

                    // The token lives 8h, so status and role in it go stale. Re-check the user on
                    // every request: a deactivated user, or one whose role changed since login,
                    // is signed out now rather than when the token expires.
                    if (!sessionStillValid(claims.getSubject(), role)) {
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Session ended. Please sign in again.");
                        return;
                    }

                    Map<String, Object> principal = Map.of(
                            "sub", claims.getSubject(),
                            "name", claims.getStringClaim("name") != null ? claims.getStringClaim("name") : "",
                            "custom:role", role != null ? role : "EMPLOYEE",
                            "custom:scid", claims.getStringClaim("custom:scid") != null ? claims.getStringClaim("custom:scid") : "1"
                    );

                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + (role != null ? role : "EMPLOYEE")))
                    );

                    SecurityContextHolder.getContext().setAuthentication(auth);

                    // Strip Authorization header so Cognito's BearerTokenAuthenticationFilter
                    // doesn't try to validate this passcode JWT as a Cognito token
                    filterChain.doFilter(new HttpServletRequestWrapper(request) {
                        @Override
                        public String getHeader(String name) {
                            if ("Authorization".equalsIgnoreCase(name)) return null;
                            return super.getHeader(name);
                        }
                        @Override
                        public Enumeration<String> getHeaders(String name) {
                            if ("Authorization".equalsIgnoreCase(name)) return Collections.emptyEnumeration();
                            return super.getHeaders(name);
                        }
                    }, response);
                    return;
                } catch (java.text.ParseException ignored) {
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean sessionStillValid(String subject, String tokenRole) {
        long userId;
        try {
            userId = Long.parseLong(subject);
        } catch (NumberFormatException e) {
            return false;
        }
        return userRepository.findSessionStateById(userId)
                .map(state -> state.getStatus() == EntityStatus.ACTIVE
                        && state.getRoleType() != null
                        && state.getRoleType().equalsIgnoreCase(tokenRole))
                .orElse(false);
    }
}
