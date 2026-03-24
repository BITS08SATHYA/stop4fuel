package com.stopforfuel.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "employees", indexes = {
    @Index(name = "idx_employees_employee_code", columnList = "employee_code")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_employees_aadhar", columnNames = {"aadhar_number"})
})
@PrimaryKeyJoinColumn(name = "id")
@Getter
@Setter
public class Employee extends User {

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "designation_id")
    private Designation designationEntity;

    @Transient
    private String designation; // Kept for backward compatibility in API

    @Size(max = 500)
    private String additionalPhones;

    @PositiveOrZero(message = "Salary must be zero or positive")
    @Max(value = 9999999, message = "Salary cannot exceed 99,99,999")
    private Double salary;

    @Column(name = "salary_day")
    @Min(value = 1, message = "Salary day must be between 1 and 31")
    @Max(value = 31, message = "Salary day must be between 1 and 31")
    private Integer salaryDay;

    @Column(name = "monthly_leave_threshold")
    @Min(value = 0, message = "Monthly leave threshold must be zero or positive")
    @Max(value = 31, message = "Monthly leave threshold cannot exceed 31")
    private Integer monthlyLeaveThreshold = 4;

    @Column(name = "aadhar_number")
    @Pattern(regexp = "^[2-9]\\d{11}$", message = "Aadhar must be 12 digits starting with 2-9")
    private String aadharNumber;

    @Size(max = 100)
    private String city;
    @Size(max = 100)
    private String state;
    @Pattern(regexp = "^[1-9]\\d{5}$", message = "Pincode must be 6 digits, not starting with 0")
    private String pincode;
    private String photoUrl;

    @Pattern(regexp = "^\\d{9,18}$", message = "Account number must be 9-18 digits")
    private String bankAccountNumber;
    @Size(max = 100)
    private String bankName;
    private String bankIfsc;
    @Size(max = 100)
    private String bankBranch;

    private String panNumber;
    @Size(max = 100)
    private String department;

    @Column(name = "employee_code")
    @Size(max = 20, message = "Employee code must not exceed 20 characters")
    private String employeeCode;
    @Size(max = 255)
    private String emergencyContact;
    @Pattern(regexp = "^(\\+91)?[6-9]\\d{9}$", message = "Emergency phone must be a valid Indian mobile number")
    private String emergencyPhone;
    @Pattern(regexp = "^(A|B|AB|O)[+-]$", message = "Invalid blood group")
    private String bloodGroup;
    private LocalDate dateOfBirth;
    @Pattern(regexp = "^(Male|Female|Other)$", message = "Gender must be Male, Female, or Other")
    private String gender;
    @Pattern(regexp = "^(Single|Married|Divorced|Widowed)$", message = "Invalid marital status")
    private String maritalStatus;
    private String aadharDocUrl;
    private String panDocUrl;

    @Column(name = "termination_date")
    private LocalDate terminationDate;

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnoreProperties({"employee", "hibernateLazyInitializer", "handler"})
    private List<SalaryHistory> salaryHistories;

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnoreProperties({"employee", "hibernateLazyInitializer", "handler"})
    private List<EmployeeAdvance> advances;

    // Backward compatibility: single email/phone for API
    @Transient
    @JsonProperty("email")
    public String getEmail() {
        return getEmails() != null && !getEmails().isEmpty() ? getEmails().iterator().next() : null;
    }

    public void setEmail(String email) {
        if (email != null && !email.isBlank()) {
            if (getEmails() == null) {
                setEmails(new java.util.HashSet<>());
            }
            getEmails().clear();
            getEmails().add(email);
        }
    }

    @Transient
    @JsonProperty("phone")
    public String getPhone() {
        return getPhoneNumbers() != null && !getPhoneNumbers().isEmpty() ? getPhoneNumbers().iterator().next() : null;
    }

    public void setPhone(String phone) {
        if (phone != null && !phone.isBlank()) {
            if (getPhoneNumbers() == null) {
                setPhoneNumbers(new java.util.HashSet<>());
            }
            getPhoneNumbers().clear();
            getPhoneNumbers().add(phone);
        }
    }

    // Designation string getter/setter for backward compatibility
    @JsonProperty("designation")
    public String getDesignation() {
        if (designationEntity != null) {
            return designationEntity.getName();
        }
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }
}
