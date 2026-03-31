package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.dto.CreateUserRequest;
import com.stopforfuel.backend.dto.UpdateUserRoleRequest;
import com.stopforfuel.backend.dto.UserListDTO;
import com.stopforfuel.backend.entity.User;
import com.stopforfuel.backend.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'USER_VIEW')")
    public List<UserListDTO> getAllUsers(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        return adminUserService.getAllUsersFiltered(type, role, status, search).stream()
                .map(UserListDTO::from)
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'USER_VIEW')")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(adminUserService.getUserById(id));
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'USER_MANAGE')")
    public ResponseEntity<Map<String, Object>> createUser(@Valid @RequestBody CreateUserRequest request) {
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            // Mobile-first user creation
            Map<String, Object> result = adminUserService.createUserWithPhone(
                    request.getName(), request.getPhone(), request.getRoleType(),
                    request.getDesignation(), request.getUserType());
            Map<String, Object> response = new HashMap<>();
            response.put("user", UserListDTO.from((User) result.get("user")));
            response.put("passcode", result.get("passcode"));
            return ResponseEntity.ok(response);
        } else {
            // Legacy email-based creation
            User user = adminUserService.createUser(
                    request.getUsername(),
                    request.getEmail(),
                    request.getName(),
                    request.getRoleType(),
                    request.getTempPassword()
            );
            Map<String, Object> response = new HashMap<>();
            response.put("user", UserListDTO.from(user));
            return ResponseEntity.ok(response);
        }
    }

    @PostMapping("/{id}/reset-passcode")
    @PreAuthorize("hasPermission(null, 'USER_MANAGE')")
    public ResponseEntity<Map<String, String>> resetPasscode(@PathVariable Long id) {
        String newPasscode = adminUserService.resetPasscode(id);
        return ResponseEntity.ok(Map.of("passcode", newPasscode));
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasPermission(null, 'USER_MANAGE')")
    public ResponseEntity<User> updateUserRole(@PathVariable Long id, @Valid @RequestBody UpdateUserRoleRequest request) {
        User user = adminUserService.updateUserRole(id, request.getRoleType());
        return ResponseEntity.ok(user);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'USER_MANAGE')")
    public ResponseEntity<Void> disableUser(@PathVariable Long id) {
        adminUserService.disableUser(id);
        return ResponseEntity.ok().build();
    }
}
