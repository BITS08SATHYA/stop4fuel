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
    @Index(name = "idx_employees_scid", columnList = "scid"),
    @Index(name = "idx_employees_status", columnList = "status"),
    @Index(name = "idx_employees_employee_code", columnList = "employee_code")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_employees_employee_code_scid", columnNames = {"employee_code", "scid"}),
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

    private String additionalPhones;

    @PositiveOrZero(message = "Salary must be zero or positive")
    private Double salary;

    @Column(name = "salary_day")
    private Integer salaryDay;

    @Column(name = "aadhar_number")
    private String aadharNumber;

    private String city;
    private String state;
    private String pincode;
    private String photoUrl;

    private String bankAccountNumber;
    private String bankName;
    private String bankIfsc;
    private String bankBranch;

    private String panNumber;
    private String department;

    @Column(name = "employee_code")
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

    // Backward compatibility: single email/phone for API
    @Transient
    @JsonProperty("email")
    public String getEmail() {
        return getEmails() != null && !getEmails().isEmpty() ? getEmails().iterator().next() : null;
    }

    @JsonProperty("email")
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

    @JsonProperty("phone")
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

    @JsonProperty("designation")
    public void setDesignation(String designation) {
        this.designation = designation;
    }
}
