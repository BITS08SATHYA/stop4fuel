package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.dto.CreateUserRequest;
import com.stopforfuel.backend.dto.UpdateUserRoleRequest;
import com.stopforfuel.backend.dto.UserListDTO;
import com.stopforfuel.backend.entity.PasscodeResetRequest;
import com.stopforfuel.backend.entity.User;
import com.stopforfuel.backend.repository.PasscodeResetRequestRepository;
import com.stopforfuel.backend.service.AdminUserService;
import com.stopforfuel.config.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final PasscodeResetRequestRepository resetRequestRepository;

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

    // --- Passcode Reset Requests ---

    @GetMapping("/passcode-reset-requests")
    @PreAuthorize("hasPermission(null, 'USER_MANAGE')")
    public List<PasscodeResetRequest> getResetRequests(
            @RequestParam(required = false) String status) {
        Long scid = SecurityUtils.getScid();
        if (status != null && !status.isBlank()) {
            return resetRequestRepository.findByScidAndStatusOrderByRequestedAtDesc(
                    scid, PasscodeResetRequest.Status.valueOf(status));
        }
        return resetRequestRepository.findByScidOrderByRequestedAtDesc(scid);
    }

    @GetMapping("/passcode-reset-requests/pending-count")
    @PreAuthorize("hasPermission(null, 'USER_MANAGE')")
    public Map<String, Long> getPendingResetCount() {
        long count = resetRequestRepository.countByScidAndStatus(
                SecurityUtils.getScid(), PasscodeResetRequest.Status.PENDING);
        return Map.of("count", count);
    }

    @PostMapping("/passcode-reset-requests/{id}/approve")
    @PreAuthorize("hasPermission(null, 'USER_MANAGE')")
    public ResponseEntity<Map<String, Object>> approveResetRequest(@PathVariable Long id) {
        PasscodeResetRequest request = resetRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reset request not found"));

        if (request.getStatus() != PasscodeResetRequest.Status.PENDING) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request already processed"));
        }

        // Reset the passcode
        String newPasscode = adminUserService.resetPasscode(request.getUserId());

        // Update request status
        request.setStatus(PasscodeResetRequest.Status.APPROVED);
        request.setProcessedAt(LocalDateTime.now());
        resetRequestRepository.save(request);

        Map<String, Object> response = new HashMap<>();
        response.put("passcode", newPasscode);
        response.put("userName", request.getUserName());
        response.put("phone", request.getPhone());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/passcode-reset-requests/{id}/reject")
    @PreAuthorize("hasPermission(null, 'USER_MANAGE')")
    public ResponseEntity<Void> rejectResetRequest(@PathVariable Long id) {
        PasscodeResetRequest request = resetRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reset request not found"));

        request.setStatus(PasscodeResetRequest.Status.REJECTED);
        request.setProcessedAt(LocalDateTime.now());
        resetRequestRepository.save(request);
        return ResponseEntity.ok().build();
    }
}
