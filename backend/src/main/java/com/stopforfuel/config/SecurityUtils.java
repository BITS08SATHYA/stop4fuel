package com.stopforfuel.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

public class SecurityUtils {

    private SecurityUtils() {}

    @SuppressWarnings("unchecked")
    public static Long getScid() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Map) {
            Object scid = ((Map<String, Object>) auth.getPrincipal()).get("custom:scid");
            if (scid != null) {
                return Long.valueOf(scid.toString());
            }
        }
        return 1L; // default fallback for dev/testing
    }
}
