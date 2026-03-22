package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Roles;
import com.stopforfuel.backend.entity.User;
import com.stopforfuel.backend.repository.RolesRepository;
import com.stopforfuel.backend.repository.UserRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final RolesRepository rolesRepository;
    private final CognitoIdentityProviderClient cognitoClient;

    @Value("${app.cognito.user-pool-id:}")
    private String userPoolId;

    @Value("${app.auth.enabled:true}")
    private boolean authEnabled;

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
    }

    public User createUser(String username, String email, String name, String roleType, String tempPassword) {
        Roles role = rolesRepository.findByRoleType(roleType)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleType));

        String cognitoId = null;

        if (authEnabled && userPoolId != null && !userPoolId.isBlank()) {
            AdminCreateUserResponse cognitoResponse = cognitoClient.adminCreateUser(
                    AdminCreateUserRequest.builder()
                            .userPoolId(userPoolId)
                            .username(email)
                            .temporaryPassword(tempPassword)
                            .userAttributes(
                                    AttributeType.builder().name("email").value(email).build(),
                                    AttributeType.builder().name("email_verified").value("true").build(),
                                    AttributeType.builder().name("name").value(name).build(),
                                    AttributeType.builder().name("custom:role").value(roleType).build(),
                                    AttributeType.builder().name("custom:scid").value(String.valueOf(SecurityUtils.getScid())).build()
                            )
                            .desiredDeliveryMediumsWithStrings("EMAIL")
                            .build()
            );
            cognitoId = cognitoResponse.user().attributes().stream()
                    .filter(a -> "sub".equals(a.name()))
                    .map(AttributeType::value)
                    .findFirst()
                    .orElse(null);
        }

        User user = new User();
        user.setUsername(username != null ? username : email);
        user.setName(name);
        user.setCognitoId(cognitoId);
        user.setRole(role);
        user.setJoinDate(LocalDate.now());
        user.setStatus("ACTIVE");
        user.setScid(SecurityUtils.getScid());
        user.setPersonType("Employee");

        Set<String> emails = new HashSet<>();
        emails.add(email);
        user.setEmails(emails);

        return userRepository.save(user);
    }

    public User updateUserRole(Long userId, String newRoleType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Roles newRole = rolesRepository.findByRoleType(newRoleType)
                .orElseThrow(() -> new RuntimeException("Role not found: " + newRoleType));

        user.setRole(newRole);

        if (authEnabled && user.getCognitoId() != null && userPoolId != null && !userPoolId.isBlank()) {
            cognitoClient.adminUpdateUserAttributes(
                    AdminUpdateUserAttributesRequest.builder()
                            .userPoolId(userPoolId)
                            .username(user.getUsername())
                            .userAttributes(
                                    AttributeType.builder().name("custom:role").value(newRoleType).build()
                            )
                            .build()
            );
        }

        return userRepository.save(user);
    }

    public void disableUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setStatus("INACTIVE");
        userRepository.save(user);

        if (authEnabled && user.getCognitoId() != null && userPoolId != null && !userPoolId.isBlank()) {
            cognitoClient.adminDisableUser(
                    AdminDisableUserRequest.builder()
                            .userPoolId(userPoolId)
                            .username(user.getUsername())
                            .build()
            );
        }
    }
}
