package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.NotificationConfig;
import com.stopforfuel.backend.entity.Roles;
import com.stopforfuel.backend.repository.RolesRepository;
import com.stopforfuel.backend.service.NotificationConfigService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notification-config")
public class NotificationConfigController {

    @Autowired
    private NotificationConfigService configService;

    @Autowired
    private RolesRepository rolesRepository;

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
}
