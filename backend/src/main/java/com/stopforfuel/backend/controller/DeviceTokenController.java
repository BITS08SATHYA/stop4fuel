package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.service.PushNotificationService;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/device-tokens")
@RequiredArgsConstructor
public class DeviceTokenController {

    private final PushNotificationService pushService;

    @PostMapping("/register")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterBody body) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        if (body.fcmToken() == null || body.fcmToken().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "fcmToken is required"));
        }
        var saved = pushService.registerToken(userId, body.fcmToken(), body.platform());
        return ResponseEntity.ok(Map.of("id", saved.getId(), "registered", true));
    }

    @PostMapping("/unregister")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> unregister(@RequestBody UnregisterBody body) {
        if (body.fcmToken() == null || body.fcmToken().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "fcmToken is required"));
        }
        pushService.deleteToken(body.fcmToken());
        return ResponseEntity.ok(Map.of("unregistered", true));
    }

    public record RegisterBody(String fcmToken, String platform) {}
    public record UnregisterBody(String fcmToken) {}
}
