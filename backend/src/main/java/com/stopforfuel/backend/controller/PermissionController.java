package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.dto.CreateModulePermissionsRequest;
import com.stopforfuel.backend.entity.Permission;
import com.stopforfuel.backend.service.PermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    @PreAuthorize("hasPermission(null, 'SETTINGS_UPDATE')")
    public ResponseEntity<Void> updatePermissionsForRole(
            @PathVariable String roleType,
            @RequestBody Map<String, List<String>> request) {
        List<String> permissionCodes = request.get("permissionCodes");
        permissionService.updatePermissionsForRole(roleType, permissionCodes);
        return ResponseEntity.ok().build();
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'SETTINGS_CREATE')")
    public ResponseEntity<List<Permission>> createModulePermissions(
            @Valid @RequestBody CreateModulePermissionsRequest request) {
        List<Permission> created = permissionService.createModulePermissions(
                request.getModule(), request.getDescription());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/module/{module}")
    @PreAuthorize("hasPermission(null, 'SETTINGS_DELETE')")
    public ResponseEntity<Void> deleteModulePermissions(@PathVariable String module) {
        permissionService.deleteModulePermissions(module);
        return ResponseEntity.ok().build();
    }
}
