package com.stopforfuel.config;

import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class DevJwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    public DevJwtAuthFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            JWTClaimsSet claims = jwtTokenProvider.validateToken(token);

            if (claims != null) {
                try {
                    String role = claims.getStringClaim("custom:role");
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
                } catch (java.text.ParseException ignored) {
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
