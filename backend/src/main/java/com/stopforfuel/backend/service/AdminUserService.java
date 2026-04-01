package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.*;
import com.stopforfuel.backend.enums.EntityStatus;
import com.stopforfuel.backend.repository.DesignationRepository;
import com.stopforfuel.backend.repository.RolesRepository;
import com.stopforfuel.backend.repository.UserRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final RolesRepository rolesRepository;
    private final DesignationRepository designationRepository;
    private final CognitoIdentityProviderClient cognitoClient;

    private static final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private static final Random random = new Random();

    @Value("${app.cognito.user-pool-id:}")
    private String userPoolId;

    @Value("${app.auth.enabled:true}")
    private boolean authEnabled;

    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAllByScid(SecurityUtils.getScid());
    }

    @Transactional(readOnly = true)
    public List<User> getAllUsersFiltered(String type, String role, String status, String search) {
        List<User> users = userRepository.findAllByScid(SecurityUtils.getScid());

        return users.stream()
                .filter(u -> {
                    if (type != null && !type.isBlank()) {
                        if ("EMPLOYEE".equalsIgnoreCase(type) && !(u instanceof Employee)) return false;
                        if ("CUSTOMER".equalsIgnoreCase(type) && !(u instanceof Customer)) return false;
                    }
                    return true;
                })
                .filter(u -> {
                    if (role != null && !role.isBlank() && u.getRole() != null) {
                        return role.equalsIgnoreCase(u.getRole().getRoleType());
                    }
                    return true;
                })
                .filter(u -> {
                    if (status != null && !status.isBlank()) {
                        return u.getStatus() != null && status.equalsIgnoreCase(u.getStatus().name());
                    }
                    return true;
                })
                .filter(u -> {
                    if (search != null && !search.isBlank()) {
                        String q = search.toLowerCase();
                        boolean nameMatch = u.getName() != null && u.getName().toLowerCase().contains(q);
                        boolean phoneMatch = u.getPhoneNumbers() != null && u.getPhoneNumbers().stream()
                                .anyMatch(p -> p.contains(q));
                        boolean usernameMatch = u.getUsername() != null && u.getUsername().toLowerCase().contains(q);
                        return nameMatch || phoneMatch || usernameMatch;
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public User getUserById(Long id) {
        return userRepository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
    }

    /**
     * Create a user with phone number and auto-generated 4-digit passcode.
     * Returns a map with "user" and "passcode" (plain text, shown once).
     */
    public Map<String, Object> createUserWithPhone(String name, String phoneNumber, String roleType,
                                                    String designationName, String userType) {
        Roles role = rolesRepository.findByRoleType(roleType)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleType));

        // Generate 4-digit passcode
        String plainPasscode = String.format("%04d", random.nextInt(10000));
        String hashedPasscode = passwordEncoder.encode(plainPasscode);

        User user;
        if ("EMPLOYEE".equalsIgnoreCase(userType)) {
            Employee employee = new Employee();
            if (designationName != null && !designationName.isBlank()) {
                Designation designation = designationRepository.findByName(designationName)
                        .orElseThrow(() -> new RuntimeException("Designation not found: " + designationName));
                employee.setDesignationEntity(designation);
            }
            employee.setPersonType("Employee");
            user = employee;
        } else {
            Customer customer = new Customer();
            customer.setPersonType("Individual");
            user = customer;
        }

        user.setName(name);
        user.setUsername(phoneNumber); // Use phone as username
        user.setRole(role);
        user.setPasscode(hashedPasscode);
        user.setJoinDate(LocalDate.now());
        user.setStatus(EntityStatus.ACTIVE);
        user.setScid(SecurityUtils.getScid());

        Set<String> phones = new HashSet<>();
        phones.add(phoneNumber);
        user.setPhoneNumbers(phones);

        User savedUser = userRepository.save(user);

        Map<String, Object> result = new HashMap<>();
        result.put("user", savedUser);
        result.put("passcode", plainPasscode);
        return result;
    }

    /**
     * Reset passcode for a user. Returns the new plain-text passcode.
     */
    public String resetPasscode(Long userId) {
        User user = userRepository.findByIdAndScid(userId, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String plainPasscode = String.format("%04d", random.nextInt(10000));
        user.setPasscode(passwordEncoder.encode(plainPasscode));
        userRepository.save(user);

        return plainPasscode;
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
        user.setStatus(EntityStatus.ACTIVE);
        user.setScid(SecurityUtils.getScid());
        user.setPersonType("Employee");

        Set<String> emails = new HashSet<>();
        emails.add(email);
        user.setEmails(emails);

        return userRepository.save(user);
    }

    public User updateUserRole(Long userId, String newRoleType) {
        return updateUserRole(userId, newRoleType, null);
    }

    public User updateUserRole(Long userId, String newRoleType, String designationName) {
        User user = userRepository.findByIdAndScid(userId, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Roles newRole = rolesRepository.findByRoleType(newRoleType)
                .orElseThrow(() -> new RuntimeException("Role not found: " + newRoleType));

        user.setRole(newRole);

        // Update designation if user is an Employee
        if (designationName != null && user instanceof com.stopforfuel.backend.entity.Employee employee) {
            if (designationName.isBlank()) {
                employee.setDesignationEntity(null);
            } else {
                designationRepository.findByName(designationName)
                        .ifPresent(employee::setDesignationEntity);
            }
        }

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
        User user = userRepository.findByIdAndScid(userId, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setStatus(EntityStatus.INACTIVE);
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
