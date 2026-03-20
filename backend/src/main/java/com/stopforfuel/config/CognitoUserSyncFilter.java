package com.stopforfuel.config;

import com.stopforfuel.backend.entity.Roles;
import com.stopforfuel.backend.entity.User;
import com.stopforfuel.backend.repository.RolesRepository;
import com.stopforfuel.backend.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CognitoUserSyncFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final RolesRepository rolesRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Jwt jwt) {
            String cognitoId = jwt.getSubject();
            if (cognitoId != null && !userRepository.existsByCognitoId(cognitoId)) {
                syncUser(jwt, cognitoId);
            }
        }

        filterChain.doFilter(request, response);
    }

    private void syncUser(Jwt jwt, String cognitoId) {
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");
        String roleType = jwt.getClaimAsString("custom:role");

        if (roleType == null || roleType.isBlank()) {
            roleType = "EMPLOYEE";
        }

        Roles role = rolesRepository.findByRoleType(roleType.toUpperCase())
                .orElseGet(() -> rolesRepository.findByRoleType("EMPLOYEE").orElseThrow());

        User user = new User();
        user.setCognitoId(cognitoId);
        user.setUsername(email != null ? email : cognitoId);
        user.setName(name != null ? name : "Unknown");
        user.setRole(role);
        user.setJoinDate(LocalDate.now());
        user.setStatus("ACTIVE");
        user.setScid(1L);
        user.setPersonType("Employee");

        if (email != null) {
            Set<String> emails = new HashSet<>();
            emails.add(email);
            user.setEmails(emails);
        }

        userRepository.save(user);
    }
}
