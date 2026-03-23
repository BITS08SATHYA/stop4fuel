package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.Employee;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Builder
public class EmployeeListDTO {
    private Long id;
    private String name;
    private String employeeCode;
    private String designation;
    private String department;
    private String email;
    private String phone;
    private Set<String> emails;
    private Set<String> phoneNumbers;
    private String status;
    private LocalDate joinDate;
    private String photoUrl;
    private String gender;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static EmployeeListDTO from(Employee e) {
        return EmployeeListDTO.builder()
                .id(e.getId())
                .name(e.getName())
                .employeeCode(e.getEmployeeCode())
                .designation(e.getDesignation())
                .department(e.getDepartment())
                .email(e.getEmail())
                .phone(e.getPhone())
                .emails(e.getEmails())
                .phoneNumbers(e.getPhoneNumbers())
                .status(e.getStatus())
                .joinDate(e.getJoinDate())
                .photoUrl(e.getPhotoUrl())
                .gender(e.getGender())
                .scid(e.getScid())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
