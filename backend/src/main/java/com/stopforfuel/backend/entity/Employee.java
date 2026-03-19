package com.stopforfuel.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "employees")
@Getter
@Setter
public class Employee extends BaseEntity {

    @NotBlank(message = "Employee name is required")
    @Size(max = 255, message = "Employee name must not exceed 255 characters")
    private String name;

    private String designation;

    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Phone number is required")
    @Size(max = 15, message = "Phone number must not exceed 15 characters")
    private String phone;

    private String additionalPhones;

    @PositiveOrZero(message = "Salary must be zero or positive")
    private Double salary;
    private LocalDate joinDate;
    private String status = "Active";

    private String aadharNumber;

    @Column(columnDefinition = "TEXT")
    private String address;

    private String city;
    private String state;
    private String pincode;
    private String photoUrl;

    private String bankAccountNumber;
    private String bankName;
    private String bankIfsc;
    private String bankBranch;

    // New fields
    private String panNumber;
    private String department;
    private String employeeCode;
    private String emergencyContact;
    private String emergencyPhone;
    private String bloodGroup;
    private LocalDate dateOfBirth;
    private String gender;
    private String maritalStatus;
    private String aadharDocUrl;
    private String panDocUrl;

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnoreProperties({"employee", "hibernateLazyInitializer", "handler"})
    private List<SalaryHistory> salaryHistories;

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnoreProperties({"employee", "hibernateLazyInitializer", "handler"})
    private List<EmployeeAdvance> advances;
}
