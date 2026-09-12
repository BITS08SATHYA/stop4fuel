package com.stopforfuel.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;

public class SecurityUtils {

    private SecurityUtils() {}

    @SuppressWarnings("unchecked")
    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Map) {
            Object sub = ((Map<String, Object>) auth.getPrincipal()).get("sub");
            if (sub != null) {
                try {
                    return Long.valueOf(sub.toString().trim());
                } catch (NumberFormatException e) {
                    // Not every principal carries a numeric id — the dev filter uses a literal
                    // string, and a Cognito subject is a UUID. Callers treat null as "unknown
                    // actor"; throwing here would fail every write that passes through the
                    // audit aspect.
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Role of the caller, resolved the same way {@code DbPermissionEvaluator} does it:
     * Cognito JWT claim first, then the dev/passcode Map principal, then the granted
     * authority. Returns null when unauthenticated, which callers must treat as "no
     * authority" rather than as a wildcard.
     */
    @SuppressWarnings("unchecked")
    public static String getCurrentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;

        if (auth.getPrincipal() instanceof Jwt jwt) {
            String role = jwt.getClaimAsString("custom:role");
            if (role != null) return role.toUpperCase();
        }

        if (auth.getPrincipal() instanceof Map) {
            Object role = ((Map<String, Object>) auth.getPrincipal()).get("custom:role");
            if (role != null) return role.toString().toUpperCase();
        }

        for (GrantedAuthority authority : auth.getAuthorities()) {
            String name = authority.getAuthority();
            if (name != null && name.startsWith("ROLE_")) {
                return name.substring(5).toUpperCase();
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    public static Long getScid() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            // Cognito sessions carry the tenant in a JWT claim; passcode sessions carry it in
            // the Map principal. Reading only the Map silently defaulted every Cognito request
            // to tenant 1.
            if (auth.getPrincipal() instanceof Jwt jwt) {
                String scid = jwt.getClaimAsString("custom:scid");
                if (scid != null && !scid.isBlank()) {
                    try {
                        return Long.valueOf(scid.trim());
                    } catch (NumberFormatException ignored) {
                        // fall through to the default
                    }
                }
            }
            if (auth.getPrincipal() instanceof Map) {
                Object scid = ((Map<String, Object>) auth.getPrincipal()).get("custom:scid");
                if (scid != null) {
                    return Long.valueOf(scid.toString());
                }
            }
        }
        return 1L; // default fallback for dev/testing
    }

    /** Cognito subject (the pool's user UUID) for JWT sessions; null for passcode sessions. */
    public static String getCognitoId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        return null;
    }
}
