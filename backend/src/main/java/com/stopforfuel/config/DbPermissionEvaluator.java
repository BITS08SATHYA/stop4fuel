package com.stopforfuel.config;

import com.stopforfuel.backend.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.io.Serializable;
import java.util.Map;

@RequiredArgsConstructor
public class DbPermissionEvaluator implements PermissionEvaluator {

    private final PermissionService permissionService;

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (authentication == null || permission == null) {
            return false;
        }
        String roleType = extractRoleType(authentication);
        return permissionService.hasPermission(roleType, permission.toString());
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId,
                                 String targetType, Object permission) {
        return hasPermission(authentication, null, permission);
    }

    @SuppressWarnings("unchecked")
    private String extractRoleType(Authentication authentication) {
        // Try to extract from JWT claim first
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            String role = jwt.getClaimAsString("custom:role");
            if (role != null) return role.toUpperCase();
        }

        // Dev mode: principal is a Map
        if (authentication.getPrincipal() instanceof Map) {
            Object role = ((Map<String, Object>) authentication.getPrincipal()).get("custom:role");
            if (role != null) return role.toString().toUpperCase();
        }

        // Fallback: extract from granted authorities
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String auth = authority.getAuthority();
            if (auth.startsWith("ROLE_")) {
                return auth.substring(5);
            }
        }

        return "EMPLOYEE";
    }
}
