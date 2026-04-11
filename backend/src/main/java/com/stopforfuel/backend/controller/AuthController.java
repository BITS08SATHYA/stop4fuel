package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.Employee;
import com.stopforfuel.backend.entity.PasscodeResetRequest;
import com.stopforfuel.backend.entity.User;
import com.stopforfuel.backend.repository.PasscodeResetRequestRepository;
import com.stopforfuel.backend.repository.UserRepository;
import com.stopforfuel.backend.service.AuditLogService;
import com.stopforfuel.backend.service.PermissionService;
import com.stopforfuel.config.SecurityUtils;
import com.stopforfuel.config.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminUserGlobalSignOutRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuditLogService auditLogService;
    private final PasscodeResetRequestRepository resetRequestRepository;
    private final CognitoIdentityProviderClient cognitoClient;

    @Value("${app.cognito.user-pool-id:}")
    private String userPoolId;

    public AuthController(UserRepository userRepository, PermissionService permissionService,
                          @Autowired(required = false) JwtTokenProvider jwtTokenProvider,
                          AuditLogService auditLogService,
                          PasscodeResetRequestRepository resetRequestRepository,
                          @Autowired(required = false) CognitoIdentityProviderClient cognitoClient) {
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.auditLogService = auditLogService;
        this.resetRequestRepository = resetRequestRepository;
        this.cognitoClient = cognitoClient;
    }

    private static final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private static final String AUTH_COOKIE_NAME = "sff-auth-session";

    @Value("${app.auth.enabled:true}")
    private boolean authEnabled;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> request,
                                                      HttpServletRequest httpRequest,
                                                      HttpServletResponse httpResponse) {
        if (jwtTokenProvider == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Passcode login is not available in this environment"));
        }

        String phone = request.get("phone");
        String passcode = request.get("passcode");

        if (phone == null || phone.isBlank() || phone.length() > 20) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid phone number"));
        }
        if (passcode == null || passcode.isBlank() || passcode.length() > 10) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid passcode"));
        }

        // Normalize phone: strip +91 prefix if present
        String normalizedPhone = phone.replaceAll("^\\+91", "");

        User user = userRepository.findByPhoneNumber(normalizedPhone)
                .or(() -> userRepository.findByPhoneNumber("+91" + normalizedPhone))
                .orElse(null);

        String clientIp = httpRequest.getRemoteAddr();

        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid phone number or passcode"));
        }

        if (user.getPasscode() == null || !passwordEncoder.matches(passcode, user.getPasscode())) {
            auditLogService.logLogin("LOGIN_FAILED", user.getId(), user.getName(), clientIp,
                    "Failed login attempt for " + normalizedPhone);
            return ResponseEntity.status(401).body(Map.of("error", "Invalid phone number or passcode"));
        }

        if (user.getStatus() != com.stopforfuel.backend.enums.EntityStatus.ACTIVE) {
            auditLogService.logLogin("LOGIN_FAILED", user.getId(), user.getName(), clientIp,
                    "Inactive account login attempt");
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

        // Update last login timestamp
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // Audit log
        auditLogService.logLogin("LOGIN_SUCCESS", user.getId(), user.getName(), clientIp,
                "Successful login via passcode");

        // Set httpOnly auth cookie
        ResponseCookie cookie = ResponseCookie.from(AUTH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(authEnabled) // true in production (HTTPS), false in dev
                .path("/")
                .sameSite("Lax")
                .maxAge(8 * 60 * 60) // 8 hours
                .build();
        httpResponse.addHeader("Set-Cookie", cookie.toString());

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("user", buildUserResponse(user, permissions, designation));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-passcode")
    public ResponseEntity<Map<String, Object>> forgotPasscode(@RequestBody Map<String, String> request) {
        String phone = request.get("phone");
        if (phone == null || phone.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Phone number is required"));
        }

        String normalizedPhone = phone.replaceAll("^\\+91", "");
        User user = userRepository.findByPhoneNumber(normalizedPhone)
                .or(() -> userRepository.findByPhoneNumber("+91" + normalizedPhone))
                .orElse(null);

        if (user == null) {
            // Don't reveal whether user exists
            return ResponseEntity.ok(Map.of("success", true, "message", "If the number is registered, admin will be notified"));
        }

        // Create a reset request for admin to approve
        PasscodeResetRequest resetRequest = new PasscodeResetRequest();
        resetRequest.setUserId(user.getId());
        resetRequest.setUserName(user.getName());
        resetRequest.setPhone(normalizedPhone);
        resetRequest.setStatus(PasscodeResetRequest.Status.PENDING);
        resetRequest.setRequestedAt(LocalDateTime.now());
        resetRequest.setScid(user.getScid());
        resetRequestRepository.save(resetRequest);

        return ResponseEntity.ok(Map.of("success", true, "message", "Admin has been notified. Please contact the station for your new passcode."));
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

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletResponse httpResponse) {
        ResponseCookie cookie = ResponseCookie.from(AUTH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(authEnabled)
                .path("/")
                .sameSite("Lax")
                .maxAge(0)
                .build();
        httpResponse.addHeader("Set-Cookie", cookie.toString());
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Map<String, String>> logoutAllDevices(Authentication authentication,
                                                                 HttpServletResponse httpResponse) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        // Sign out from Cognito (invalidates all tokens)
        if (cognitoClient != null && userPoolId != null && !userPoolId.isBlank()) {
            String cognitoId = extractCognitoId(authentication);
            User user = userRepository.findByCognitoId(cognitoId).orElse(null);
            if (user == null) {
                try {
                    Long userId = Long.parseLong(cognitoId);
                    user = userRepository.findById(userId).orElse(null);
                } catch (NumberFormatException ignored) {
                }
            }

            if (user != null && user.getCognitoId() != null && !user.getCognitoId().isBlank()) {
                try {
                    cognitoClient.adminUserGlobalSignOut(AdminUserGlobalSignOutRequest.builder()
                            .userPoolId(userPoolId)
                            .username(user.getCognitoId())
                            .build());
                } catch (Exception e) {
                    return ResponseEntity.status(500).body(Map.of("error", "Failed to sign out from all devices"));
                }
            }
        }

        // Clear current device cookie
        ResponseCookie cookie = ResponseCookie.from(AUTH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(authEnabled)
                .path("/")
                .sameSite("Lax")
                .maxAge(0)
                .build();
        httpResponse.addHeader("Set-Cookie", cookie.toString());
        return ResponseEntity.ok(Map.of("message", "Signed out from all devices"));
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
