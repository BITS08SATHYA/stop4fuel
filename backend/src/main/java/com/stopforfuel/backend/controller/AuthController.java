package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.Employee;
import com.stopforfuel.backend.entity.User;
import com.stopforfuel.backend.repository.UserRepository;
import com.stopforfuel.backend.service.PermissionService;
import com.stopforfuel.config.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(UserRepository userRepository, PermissionService permissionService,
                          @Autowired(required = false) JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    private static final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        if (jwtTokenProvider == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Passcode login is not available in this environment"));
        }

        String phone = request.get("phone");
        String passcode = request.get("passcode");

        if (phone == null || passcode == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Phone and passcode are required"));
        }

        // Normalize phone: strip +91 prefix if present
        String normalizedPhone = phone.replaceAll("^\\+91", "");

        User user = userRepository.findByPhoneNumber(normalizedPhone)
                .or(() -> userRepository.findByPhoneNumber("+91" + normalizedPhone))
                .orElse(null);

        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid phone number or passcode"));
        }

        if (user.getPasscode() == null || !passwordEncoder.matches(passcode, user.getPasscode())) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid phone number or passcode"));
        }

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            return ResponseEntity.status(403).body(Map.of("error", "Account is inactive"));
        }

        String designation = null;
        if (user instanceof Employee employee && employee.getDesignationEntity() != null) {
            designation = employee.getDesignationEntity().getName();
        }

        String token = jwtTokenProvider.generateToken(
                user.getId(),
                user.getRole().getRoleType(),
                user.getScid(),
                user.getName(),
                normalizedPhone,
                designation
        );

        List<String> permissions = permissionService.getPermissionsForRole(user.getRole().getRoleType());

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("user", buildUserResponse(user, permissions, designation));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }

        String cognitoId = extractCognitoId(authentication);
        User user = userRepository.findByCognitoId(cognitoId).orElse(null);

        // In dev mode with self-issued JWT, sub is the user ID
        if (user == null) {
            try {
                Long userId = Long.parseLong(cognitoId);
                user = userRepository.findById(userId).orElse(null);
            } catch (NumberFormatException ignored) {
            }
        }

        Map<String, Object> response = new HashMap<>();
        if (user != null) {
            String designation = null;
            if (user instanceof Employee employee && employee.getDesignationEntity() != null) {
                designation = employee.getDesignationEntity().getName();
            }

            List<String> permissions = permissionService.getPermissionsForRole(user.getRole().getRoleType());
            response = buildUserResponse(user, permissions, designation);
        } else {
            // Dev mode fallback
            response.put("cognitoId", cognitoId);
            response.put("role", extractRole(authentication));
            response.put("name", extractClaim(authentication, "name"));
            response.put("email", extractClaim(authentication, "email"));
            response.put("permissions", permissionService.getPermissionsForRole(extractRole(authentication)));
        }

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> buildUserResponse(User user, List<String> permissions, String designation) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", user.getId());
        response.put("cognitoId", user.getCognitoId());
        response.put("username", user.getUsername());
        response.put("name", user.getName());
        response.put("email", user.getEmails() != null && !user.getEmails().isEmpty()
                ? user.getEmails().iterator().next() : null);
        response.put("phone", user.getPhoneNumbers() != null && !user.getPhoneNumbers().isEmpty()
                ? user.getPhoneNumbers().iterator().next() : null);
        response.put("role", user.getRole().getRoleType());
        response.put("status", user.getStatus());
        response.put("permissions", permissions);
        if (designation != null) {
            response.put("designation", designation);
        }
        return response;
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
