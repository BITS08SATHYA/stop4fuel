package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.Designation;
import com.stopforfuel.backend.entity.Employee;
import com.stopforfuel.backend.entity.Roles;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Detail DTO for Employee — includes all fields needed for viewing and editing,
 * except cognitoId (internal auth) and password (already WRITE_ONLY on entity).
 * Adds masked versions of sensitive IDs for safe display.
 */
@Getter
@Builder
public class EmployeeDetailDTO {
    private Long id;
    private String name;
    private String username;
    private String employeeCode;
    private String designation;
    private Designation designationEntity;
    private String department;
    private String email;
    private String phone;
    private Set<String> emails;
    private Set<String> phoneNumbers;
    private String additionalPhones;
    private String address;
    private String city;
    private String state;
    private String pincode;
    private String status;
    private LocalDate joinDate;
    private LocalDate terminationDate;
    private String photoUrl;
    private String gender;
    private String maritalStatus;
    private String bloodGroup;
    private LocalDate dateOfBirth;
    private String emergencyContact;
    private String emergencyPhone;
    private Double salary;
    private Integer salaryDay;
    private String personType;
    private Roles role;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Sensitive fields — included for editing, masked versions for display
    private String aadharNumber;
    private String panNumber;
    private String bankAccountNumber;
    private String bankName;
    private String bankIfsc;
    private String bankBranch;

    // Masked versions for safe display
    private String maskedAadhar;
    private String maskedPan;
    private String maskedBankAccount;

    // Document URLs (needed for upload status checks)
    private String aadharDocUrl;
    private String panDocUrl;

    public static EmployeeDetailDTO from(Employee e) {
        return EmployeeDetailDTO.builder()
                .id(e.getId())
                .name(e.getName())
                .username(e.getUsername())
                .employeeCode(e.getEmployeeCode())
                .designation(e.getDesignation())
                .designationEntity(e.getDesignationEntity())
                .department(e.getDepartment())
                .email(e.getEmail())
                .phone(e.getPhone())
                .emails(e.getEmails())
                .phoneNumbers(e.getPhoneNumbers())
                .additionalPhones(e.getAdditionalPhones())
                .address(e.getAddress())
                .city(e.getCity())
                .state(e.getState())
                .pincode(e.getPincode())
                .status(e.getStatus())
                .joinDate(e.getJoinDate())
                .terminationDate(e.getTerminationDate())
                .photoUrl(e.getPhotoUrl())
                .gender(e.getGender())
                .maritalStatus(e.getMaritalStatus())
                .bloodGroup(e.getBloodGroup())
                .dateOfBirth(e.getDateOfBirth())
                .emergencyContact(e.getEmergencyContact())
                .emergencyPhone(e.getEmergencyPhone())
                .salary(e.getSalary())
                .salaryDay(e.getSalaryDay())
                .personType(e.getPersonType())
                .role(e.getRole())
                .scid(e.getScid())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .aadharNumber(e.getAadharNumber())
                .panNumber(e.getPanNumber())
                .bankAccountNumber(e.getBankAccountNumber())
                .bankName(e.getBankName())
                .bankIfsc(e.getBankIfsc())
                .bankBranch(e.getBankBranch())
                .maskedAadhar(maskAadhar(e.getAadharNumber()))
                .maskedPan(maskPan(e.getPanNumber()))
                .maskedBankAccount(maskBankAccount(e.getBankAccountNumber()))
                .aadharDocUrl(e.getAadharDocUrl())
                .panDocUrl(e.getPanDocUrl())
                .build();
    }

    private static String maskAadhar(String aadhar) {
        if (aadhar == null || aadhar.length() < 4) return null;
        return "XXXX-XXXX-" + aadhar.substring(aadhar.length() - 4);
    }

    private static String maskPan(String pan) {
        if (pan == null || pan.length() < 4) return null;
        return "XXXXXX" + pan.substring(pan.length() - 4);
    }

    private static String maskBankAccount(String account) {
        if (account == null || account.length() < 4) return null;
        return "X".repeat(account.length() - 4) + account.substring(account.length() - 4);
    }
}
