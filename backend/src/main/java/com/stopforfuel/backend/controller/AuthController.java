package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.User;
import com.stopforfuel.backend.repository.UserRepository;
import com.stopforfuel.backend.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PermissionService permissionService;

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }

        String cognitoId = extractCognitoId(authentication);
        User user = userRepository.findByCognitoId(cognitoId).orElse(null);

        Map<String, Object> response = new HashMap<>();
        if (user != null) {
            response.put("id", user.getId());
            response.put("cognitoId", user.getCognitoId());
            response.put("username", user.getUsername());
            response.put("name", user.getName());
            response.put("email", user.getEmails() != null && !user.getEmails().isEmpty()
                    ? user.getEmails().iterator().next() : null);
            response.put("role", user.getRole().getRoleType());
            response.put("status", user.getStatus());

            List<String> permissions = permissionService.getPermissionsForRole(user.getRole().getRoleType());
            response.put("permissions", permissions);
        } else {
            // Dev mode fallback — extract from auth principal
            response.put("cognitoId", cognitoId);
            response.put("role", extractRole(authentication));
            response.put("name", extractClaim(authentication, "name"));
            response.put("email", extractClaim(authentication, "email"));
            response.put("permissions", permissionService.getPermissionsForRole(extractRole(authentication)));
        }

        return ResponseEntity.ok(response);
    }

    @SuppressWarnings("unchecked")
    private String extractCognitoId(Authentication auth) {
        if (auth.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        if (auth.getPrincipal() instanceof Map) {
            return ((Map<String, Object>) auth.getPrincipal()).get("sub").toString();
        }
        return auth.getName();
    }

    @SuppressWarnings("unchecked")
    private String extractRole(Authentication auth) {
        if (auth.getPrincipal() instanceof Jwt jwt) {
            String role = jwt.getClaimAsString("custom:role");
            return role != null ? role : "EMPLOYEE";
        }
        if (auth.getPrincipal() instanceof Map) {
            Object role = ((Map<String, Object>) auth.getPrincipal()).get("custom:role");
            return role != null ? role.toString() : "OWNER";
        }
        return "EMPLOYEE";
    }

    @SuppressWarnings("unchecked")
    private String extractClaim(Authentication auth, String claim) {
        if (auth.getPrincipal() instanceof Jwt jwt) {
            return jwt.getClaimAsString(claim);
        }
        if (auth.getPrincipal() instanceof Map) {
            Object val = ((Map<String, Object>) auth.getPrincipal()).get(claim);
            return val != null ? val.toString() : null;
        }
        return null;
    }
}
