package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.Employee;
import com.stopforfuel.backend.entity.User;
import com.stopforfuel.backend.repository.UserRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserRepository userRepository;

    @GetMapping
    public ProfileResponse getProfile() {
        User user = getCurrentUser();
        ProfileResponse profile = new ProfileResponse();
        profile.setId(user.getId());
        profile.setName(user.getName());
        profile.setPhone(user.getPhoneNumbers() != null && !user.getPhoneNumbers().isEmpty()
                ? user.getPhoneNumbers().iterator().next() : null);
        profile.setEmail(user.getEmails() != null && !user.getEmails().isEmpty()
                ? user.getEmails().iterator().next() : null);
        profile.setRole(user.getRole() != null ? user.getRole().getRoleType() : null);
        profile.setJoinDate(user.getJoinDate() != null ? user.getJoinDate().toString() : null);
        profile.setStatus(user.getStatus() != null ? user.getStatus().name() : null);

        if (user instanceof Employee emp) {
            profile.setDesignation(emp.getDesignation());
            profile.setEmployeeCode(emp.getEmployeeCode());
            profile.setDepartment(emp.getDepartment());
        }

        return profile;
    }

    @PutMapping
    public ProfileResponse updateProfile(@jakarta.validation.Valid @RequestBody UpdateProfileRequest request) {
        User user = getCurrentUser();

        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName());
        }

        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            if (user.getPhoneNumbers() == null) {
                user.setPhoneNumbers(new java.util.HashSet<>());
            }
            user.getPhoneNumbers().clear();
            user.getPhoneNumbers().add(request.getPhone());
        }

        userRepository.save(user);
        return getProfile();
    }

    private User getCurrentUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    @Getter @Setter @NoArgsConstructor
    public static class ProfileResponse {
        private Long id;
        private String name;
        private String phone;
        private String email;
        private String role;
        private String designation;
        private String employeeCode;
        private String department;
        private String joinDate;
        private String status;
    }

    @Getter @Setter @NoArgsConstructor
    public static class UpdateProfileRequest {
        private String name;
        private String phone;
    }
}
