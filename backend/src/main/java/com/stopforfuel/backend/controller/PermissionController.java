package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.Permission;
import com.stopforfuel.backend.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'SETTINGS_VIEW')")
    public Map<String, List<Permission>> getAllPermissionsGrouped() {
        return permissionService.getAllPermissionsGrouped();
    }

    @GetMapping("/role/{roleType}")
    @PreAuthorize("hasPermission(null, 'SETTINGS_VIEW')")
    public List<String> getPermissionsForRole(@PathVariable String roleType) {
        return permissionService.getPermissionsForRole(roleType);
    }

    @PutMapping("/role/{roleType}")
    @PreAuthorize("hasPermission(null, 'SETTINGS_MANAGE')")
    public ResponseEntity<Void> updatePermissionsForRole(
            @PathVariable String roleType,
            @RequestBody Map<String, List<String>> request) {
        List<String> permissionCodes = request.get("permissionCodes");
        permissionService.updatePermissionsForRole(roleType, permissionCodes);
        return ResponseEntity.ok().build();
    }
}
