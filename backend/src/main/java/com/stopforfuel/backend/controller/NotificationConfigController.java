package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.NotificationConfig;
import com.stopforfuel.backend.entity.Roles;
import com.stopforfuel.backend.repository.RolesRepository;
import com.stopforfuel.backend.service.NotificationConfigService;
import com.stopforfuel.backend.service.PushNotificationService;
import com.stopforfuel.config.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notification-config")
public class NotificationConfigController {

    @Autowired
    private NotificationConfigService configService;

    @Autowired
    private RolesRepository rolesRepository;

    @Autowired
    private PushNotificationService pushService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'SETTINGS_VIEW')")
    public List<NotificationConfig> getAll() {
        return configService.getAll();
    }

    @GetMapping("/{alertType}")
    @PreAuthorize("hasPermission(null, 'SETTINGS_VIEW')")
    public ResponseEntity<NotificationConfig> getByAlertType(@PathVariable String alertType) {
        return configService.getByAlertType(alertType)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/roles")
    @PreAuthorize("hasPermission(null, 'SETTINGS_VIEW')")
    public List<Roles> getAvailableRoles() {
        return rolesRepository.findAll();
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'SETTINGS_CREATE')")
    public NotificationConfig save(@Valid @RequestBody NotificationConfig config) {
        return configService.save(config);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'SETTINGS_DELETE')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        configService.delete(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Send a test push to the current user's own registered devices for the given alert type.
     * Lets an admin verify the SNS → FCM → device path from the settings screen without
     * closing a shift. Only reaches the caller's devices, so SETTINGS_VIEW is sufficient.
     */
    @PostMapping("/{alertType}/test")
    @PreAuthorize("hasPermission(null, 'SETTINGS_VIEW')")
    public ResponseEntity<Map<String, Object>> sendTest(@PathVariable String alertType) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        String label = alertType.replace('_', ' ').toLowerCase();
        PushNotificationService.TestPushResult result = pushService.sendTestPush(userId,
                "StopForFuel test push",
                "Test for \"" + label + "\". If you see this, push delivery works.");
        return ResponseEntity.ok(Map.of(
                "pushEnabled", result.pushEnabled(),
                "devices", result.devices(),
                "sent", result.sent()
        ));
    }
}
