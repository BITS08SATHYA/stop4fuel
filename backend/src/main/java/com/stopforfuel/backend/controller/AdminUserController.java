package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.User;
import com.stopforfuel.backend.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'USER_VIEW')")
    public List<User> getAllUsers() {
        return adminUserService.getAllUsers();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'USER_VIEW')")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(adminUserService.getUserById(id));
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'USER_MANAGE')")
    public ResponseEntity<User> createUser(@RequestBody Map<String, String> request) {
        User user = adminUserService.createUser(
                request.get("username"),
                request.get("email"),
                request.get("name"),
                request.get("roleType"),
                request.get("tempPassword")
        );
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasPermission(null, 'USER_MANAGE')")
    public ResponseEntity<User> updateUserRole(@PathVariable Long id, @RequestBody Map<String, String> request) {
        User user = adminUserService.updateUserRole(id, request.get("roleType"));
        return ResponseEntity.ok(user);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'USER_MANAGE')")
    public ResponseEntity<Void> disableUser(@PathVariable Long id) {
        adminUserService.disableUser(id);
        return ResponseEntity.ok().build();
    }
}
