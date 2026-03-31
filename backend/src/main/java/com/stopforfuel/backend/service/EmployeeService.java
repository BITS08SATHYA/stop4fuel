package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Designation;
import com.stopforfuel.backend.entity.Employee;
import com.stopforfuel.backend.entity.OperationalAdvance;
import com.stopforfuel.backend.entity.Roles;
import com.stopforfuel.backend.entity.SalaryHistory;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.exception.ResourceNotFoundException;
import com.stopforfuel.backend.repository.DesignationRepository;
import com.stopforfuel.backend.repository.OperationalAdvanceRepository;
import com.stopforfuel.backend.repository.EmployeeRepository;
import com.stopforfuel.backend.repository.RolesRepository;
import com.stopforfuel.backend.repository.SalaryHistoryRepository;
import com.stopforfuel.config.SecurityUtils;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.time.LocalDate;
import java.time.Period;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;

    private final SalaryHistoryRepository salaryHistoryRepository;

    private final OperationalAdvanceRepository operationalAdvanceRepository;

    private final S3StorageService s3StorageService;

    private final DesignationRepository designationRepository;

    private final RolesRepository rolesRepository;

    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<Employee> getAllEmployees() {
        return employeeRepository.findAllByScid(SecurityUtils.getScid());
    }

    @Transactional(readOnly = true)
    public Page<Employee> getEmployees(String search, String status, Pageable pageable) {
        return employeeRepository.findBySearchAndStatus(
                search != null && !search.isEmpty() ? search : null,
                status != null && !status.isEmpty() ? status : null,
                pageable);
    }

    @Transactional(readOnly = true)
    public Optional<Employee> getEmployeeById(Long id) {
        return employeeRepository.findByIdAndScid(id, SecurityUtils.getScid());
    }

    @Transactional(readOnly = true)
    public List<Employee> getActiveEmployees() {
        return employeeRepository.findByStatus("ACTIVE");
    }

    @Transactional
    public Employee createEmployee(Employee employee) {
        // Business validations
        validateEmployeeBusinessRules(employee);
        validateAadharUniqueness(employee.getAadharNumber(), null);

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

        Employee saved = employeeRepository.save(employee);
        entityManager.flush();
        entityManager.refresh(saved);
        return saved;
    }

    @Transactional
    public Employee updateEmployee(Long id, Employee details) {
        Employee employee = employeeRepository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        // Business validations
        validateEmployeeBusinessRules(details);
        validateAadharUniqueness(details.getAadharNumber(), id);

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
    @Transactional(readOnly = true)
    public List<SalaryHistory> getSalaryHistory(Long employeeId) {
        return salaryHistoryRepository.findByEmployeeIdOrderByEffectiveDateDesc(employeeId);
    }

    public SalaryHistory addSalaryRevision(SalaryHistory history) {
        Employee employee = employeeRepository.findByIdAndScid(history.getEmployee().getId(), SecurityUtils.getScid())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        history.setOldSalary(employee.getSalary());
        employee.setSalary(history.getNewSalary());
        employeeRepository.save(employee);

        history.setEmployee(employee);
        return salaryHistoryRepository.save(history);
    }

    // Advances
    @Transactional(readOnly = true)
    public List<OperationalAdvance> getAdvances(Long employeeId) {
        return operationalAdvanceRepository.findByEmployeeIdOrderByAdvanceDateDesc(employeeId);
    }

    @Transactional(readOnly = true)
    public List<OperationalAdvance> getPendingAdvances(Long employeeId) {
        return operationalAdvanceRepository.findByEmployeeIdAndStatus(employeeId, "PENDING");
    }

    public OperationalAdvance addAdvance(OperationalAdvance advance) {
        return operationalAdvanceRepository.save(advance);
    }

    public OperationalAdvance updateAdvanceStatus(Long advanceId, String status) {
        OperationalAdvance advance = operationalAdvanceRepository.findById(advanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Advance not found"));
        advance.setStatus(status);
        return operationalAdvanceRepository.save(advance);
    }

    // ── File Uploads ────────────────────────────────────────────────

    public Employee uploadPhoto(Long id, MultipartFile file) throws IOException {
        validateFileType(file, new String[]{"image/jpeg", "image/png", "image/webp"}, 5 * 1024 * 1024);
        Employee employee = employeeRepository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
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
        Employee employee = employeeRepository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
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
        Employee employee = employeeRepository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        String ext = getExtension(file.getOriginalFilename());
        String key = "employees/" + id + "/pan-doc." + ext;
        if (employee.getPanDocUrl() != null && !employee.getPanDocUrl().isEmpty()) {
            s3StorageService.delete(employee.getPanDocUrl());
        }
        s3StorageService.upload(key, file);
        employee.setPanDocUrl(key);
        return employeeRepository.save(employee);
    }

    @Transactional(readOnly = true)
    public String getFilePresignedUrl(Long id, String type) {
        Employee employee = employeeRepository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
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
            throw new ResourceNotFoundException("No file uploaded for type: " + type);
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

    private void validateEmployeeBusinessRules(Employee employee) {
        // DOB: must be in the past, at least 15 years old
        if (employee.getDateOfBirth() != null) {
            if (employee.getDateOfBirth().isAfter(LocalDate.now())) {
                throw new BusinessException("Date of birth cannot be in the future");
            }
            if (Period.between(employee.getDateOfBirth(), LocalDate.now()).getYears() < 15) {
                throw new BusinessException("Employee must be at least 15 years old");
            }
        }
        // Join date vs DOB
        if (employee.getJoinDate() != null && employee.getDateOfBirth() != null) {
            if (employee.getJoinDate().isBefore(employee.getDateOfBirth())) {
                throw new BusinessException("Join date cannot be before date of birth");
            }
        }
        // Termination date must be after join date
        if (employee.getTerminationDate() != null && employee.getJoinDate() != null) {
            if (employee.getTerminationDate().isBefore(employee.getJoinDate())) {
                throw new BusinessException("Termination date cannot be before join date");
            }
        }
    }

    private void validateAadharUniqueness(String aadharNumber, Long excludeId) {
        if (aadharNumber != null && !aadharNumber.isBlank()) {
            Optional<Employee> existing = employeeRepository.findByAadharNumber(aadharNumber);
            if (existing.isPresent() && (excludeId == null || !existing.get().getId().equals(excludeId))) {
                throw new BusinessException("An employee with this Aadhar number already exists");
            }
        }
    }
}
