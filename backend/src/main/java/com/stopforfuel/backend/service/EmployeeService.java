package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Designation;
import com.stopforfuel.backend.entity.Employee;
import com.stopforfuel.backend.entity.EmployeeAdvance;
import com.stopforfuel.backend.entity.Roles;
import com.stopforfuel.backend.entity.SalaryHistory;
import com.stopforfuel.backend.repository.DesignationRepository;
import com.stopforfuel.backend.repository.EmployeeAdvanceRepository;
import com.stopforfuel.backend.repository.EmployeeRepository;
import com.stopforfuel.backend.repository.RolesRepository;
import com.stopforfuel.backend.repository.SalaryHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class EmployeeService {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private SalaryHistoryRepository salaryHistoryRepository;

    @Autowired
    private EmployeeAdvanceRepository employeeAdvanceRepository;

    @Autowired
    private S3StorageService s3StorageService;

    @Autowired
    private DesignationRepository designationRepository;

    @Autowired
    private RolesRepository rolesRepository;

    public List<Employee> getAllEmployees() {
        return employeeRepository.findAll();
    }

    public Optional<Employee> getEmployeeById(Long id) {
        return employeeRepository.findById(id);
    }

    public List<Employee> getActiveEmployees() {
        return employeeRepository.findByStatus("Active");
    }

    public Employee createEmployee(Employee employee) {
        // Set PersonEntity fields
        if (employee.getPersonType() == null) {
            employee.setPersonType("Employee");
        }

        // Handle email/phone → Set conversion
        syncEmailAndPhone(employee);

        // Handle designation
        resolveDesignation(employee);

        // Set User fields if not present
        if (employee.getUsername() == null || employee.getUsername().isBlank()) {
            employee.setUsername(generateUsername(employee));
        }
        if (employee.getRole() == null) {
            String defaultRole = employee.getDesignationEntity() != null
                    ? employee.getDesignationEntity().getDefaultRole() : "EMPLOYEE";
            if (defaultRole == null) defaultRole = "EMPLOYEE";
            Roles role = rolesRepository.findByRoleType(defaultRole)
                    .orElseGet(() -> rolesRepository.findByRoleType("EMPLOYEE").orElseThrow());
            employee.setRole(role);
        }
        if (employee.getJoinDate() == null) {
            employee.setJoinDate(LocalDate.now());
        }
        if (employee.getStatus() == null) {
            employee.setStatus("Active");
        }

        return employeeRepository.save(employee);
    }

    public Employee updateEmployee(Long id, Employee details) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        // PersonEntity fields
        employee.setName(details.getName());
        employee.setAddress(details.getAddress());

        // Handle email/phone
        if (details.getEmail() != null) {
            employee.setEmail(details.getEmail());
        }
        if (details.getPhone() != null) {
            employee.setPhone(details.getPhone());
        }

        // Employee-specific fields
        employee.setAdditionalPhones(details.getAdditionalPhones());
        employee.setSalary(details.getSalary());
        employee.setJoinDate(details.getJoinDate());
        employee.setStatus(details.getStatus());
        employee.setAadharNumber(details.getAadharNumber());
        employee.setCity(details.getCity());
        employee.setState(details.getState());
        employee.setPincode(details.getPincode());
        employee.setPhotoUrl(details.getPhotoUrl());
        employee.setBankAccountNumber(details.getBankAccountNumber());
        employee.setBankName(details.getBankName());
        employee.setBankIfsc(details.getBankIfsc());
        employee.setBankBranch(details.getBankBranch());
        employee.setPanNumber(details.getPanNumber());
        employee.setDepartment(details.getDepartment());
        employee.setEmployeeCode(details.getEmployeeCode());
        employee.setEmergencyContact(details.getEmergencyContact());
        employee.setEmergencyPhone(details.getEmergencyPhone());
        employee.setBloodGroup(details.getBloodGroup());
        employee.setDateOfBirth(details.getDateOfBirth());
        employee.setGender(details.getGender());
        employee.setMaritalStatus(details.getMaritalStatus());
        employee.setAadharDocUrl(details.getAadharDocUrl());
        employee.setPanDocUrl(details.getPanDocUrl());
        employee.setSalaryDay(details.getSalaryDay());

        // Handle designation
        if (details.getDesignation() != null) {
            employee.setDesignation(details.getDesignation());
            resolveDesignation(employee);
        }

        return employeeRepository.save(employee);
    }

    public void deleteEmployee(Long id) {
        employeeRepository.deleteById(id);
    }

    // Salary History
    public List<SalaryHistory> getSalaryHistory(Long employeeId) {
        return salaryHistoryRepository.findByEmployeeIdOrderByEffectiveDateDesc(employeeId);
    }

    public SalaryHistory addSalaryRevision(SalaryHistory history) {
        Employee employee = employeeRepository.findById(history.getEmployee().getId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        history.setOldSalary(employee.getSalary());
        employee.setSalary(history.getNewSalary());
        employeeRepository.save(employee);

        history.setEmployee(employee);
        return salaryHistoryRepository.save(history);
    }

    // Advances
    public List<EmployeeAdvance> getAdvances(Long employeeId) {
        return employeeAdvanceRepository.findByEmployeeIdOrderByAdvanceDateDesc(employeeId);
    }

    public List<EmployeeAdvance> getPendingAdvances(Long employeeId) {
        return employeeAdvanceRepository.findByEmployeeIdAndStatus(employeeId, "PENDING");
    }

    public EmployeeAdvance addAdvance(EmployeeAdvance advance) {
        return employeeAdvanceRepository.save(advance);
    }

    public EmployeeAdvance updateAdvanceStatus(Long advanceId, String status) {
        EmployeeAdvance advance = employeeAdvanceRepository.findById(advanceId)
                .orElseThrow(() -> new RuntimeException("Advance not found"));
        advance.setStatus(status);
        return employeeAdvanceRepository.save(advance);
    }

    // ── File Uploads ────────────────────────────────────────────────

    public Employee uploadPhoto(Long id, MultipartFile file) throws IOException {
        validateFileType(file, new String[]{"image/jpeg", "image/png", "image/webp"}, 5 * 1024 * 1024);
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        String ext = getExtension(file.getOriginalFilename());
        String key = "employees/" + id + "/photo." + ext;
        if (employee.getPhotoUrl() != null && !employee.getPhotoUrl().isEmpty()) {
            s3StorageService.delete(employee.getPhotoUrl());
        }
        s3StorageService.upload(key, file);
        employee.setPhotoUrl(key);
        return employeeRepository.save(employee);
    }

    public Employee uploadAadharDoc(Long id, MultipartFile file) throws IOException {
        validateFileType(file, new String[]{"image/jpeg", "image/png", "image/webp", "application/pdf"}, 10 * 1024 * 1024);
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        String ext = getExtension(file.getOriginalFilename());
        String key = "employees/" + id + "/aadhar-doc." + ext;
        if (employee.getAadharDocUrl() != null && !employee.getAadharDocUrl().isEmpty()) {
            s3StorageService.delete(employee.getAadharDocUrl());
        }
        s3StorageService.upload(key, file);
        employee.setAadharDocUrl(key);
        return employeeRepository.save(employee);
    }

    public Employee uploadPanDoc(Long id, MultipartFile file) throws IOException {
        validateFileType(file, new String[]{"image/jpeg", "image/png", "image/webp", "application/pdf"}, 10 * 1024 * 1024);
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        String ext = getExtension(file.getOriginalFilename());
        String key = "employees/" + id + "/pan-doc." + ext;
        if (employee.getPanDocUrl() != null && !employee.getPanDocUrl().isEmpty()) {
            s3StorageService.delete(employee.getPanDocUrl());
        }
        s3StorageService.upload(key, file);
        employee.setPanDocUrl(key);
        return employeeRepository.save(employee);
    }

    public String getFilePresignedUrl(Long id, String type) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        String key;
        switch (type) {
            case "photo":
                key = employee.getPhotoUrl();
                break;
            case "aadhar-doc":
                key = employee.getAadharDocUrl();
                break;
            case "pan-doc":
                key = employee.getPanDocUrl();
                break;
            default:
                throw new IllegalArgumentException("Invalid file type: " + type);
        }
        if (key == null || key.isEmpty()) {
            throw new RuntimeException("No file uploaded for type: " + type);
        }
        return s3StorageService.getPresignedUrl(key);
    }

    // ── Private helpers ────────────────────────────────────────────

    private void syncEmailAndPhone(Employee employee) {
        if (employee.getEmail() != null && !employee.getEmail().isBlank()) {
            Set<String> emails = employee.getEmails();
            if (emails == null) emails = new HashSet<>();
            emails.add(employee.getEmail());
            employee.setEmails(emails);
        }
        if (employee.getPhone() != null && !employee.getPhone().isBlank()) {
            Set<String> phones = employee.getPhoneNumbers();
            if (phones == null) phones = new HashSet<>();
            phones.add(employee.getPhone());
            employee.setPhoneNumbers(phones);
        }
    }

    private void resolveDesignation(Employee employee) {
        String desigName = employee.getDesignation();
        if (desigName != null && !desigName.isBlank()) {
            designationRepository.findByName(desigName).ifPresent(employee::setDesignationEntity);
        }
    }

    private String generateUsername(Employee employee) {
        String base = employee.getName() != null
                ? employee.getName().toLowerCase().replaceAll("\\s+", ".") : "employee";
        String code = employee.getEmployeeCode();
        if (code != null && !code.isBlank()) {
            return code.toLowerCase();
        }
        return base + "." + System.currentTimeMillis() % 10000;
    }

    private void validateFileType(MultipartFile file, String[] allowedTypes, long maxSize) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("File size exceeds maximum allowed: " + (maxSize / (1024 * 1024)) + "MB");
        }
        String contentType = file.getContentType();
        boolean valid = false;
        for (String type : allowedTypes) {
            if (type.equals(contentType)) {
                valid = true;
                break;
            }
        }
        if (!valid) {
            throw new IllegalArgumentException("Unsupported file type: " + contentType);
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "bin";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
