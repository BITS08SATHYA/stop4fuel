package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.AuditLog;
import com.stopforfuel.backend.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'SETTINGS_VIEW')")
    public Page<AuditLog> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long userId) {
        return auditLogService.getAuditLogs(action, userId, page, size);
    }

    @GetMapping("/recent-logins")
    @PreAuthorize("hasPermission(null, 'SETTINGS_VIEW')")
    public List<AuditLog> getRecentLogins(@RequestParam(defaultValue = "10") int limit) {
        return auditLogService.getRecentLogins(limit);
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasPermission(null, 'USER_VIEW')")
    public List<AuditLog> getUserActivity(@PathVariable Long userId) {
        return auditLogService.getActivityForUser(userId);
    }
}
