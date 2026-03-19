package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Employee;
import com.stopforfuel.backend.entity.EmployeeAdvance;
import com.stopforfuel.backend.entity.SalaryHistory;
import com.stopforfuel.backend.repository.EmployeeAdvanceRepository;
import com.stopforfuel.backend.repository.EmployeeRepository;
import com.stopforfuel.backend.repository.SalaryHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

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
        return employeeRepository.save(employee);
    }

    public Employee updateEmployee(Long id, Employee details) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        employee.setName(details.getName());
        employee.setDesignation(details.getDesignation());
        employee.setEmail(details.getEmail());
        employee.setPhone(details.getPhone());
        employee.setAdditionalPhones(details.getAdditionalPhones());
        employee.setSalary(details.getSalary());
        employee.setJoinDate(details.getJoinDate());
        employee.setStatus(details.getStatus());
        employee.setAadharNumber(details.getAadharNumber());
        employee.setAddress(details.getAddress());
        employee.setCity(details.getCity());
        employee.setState(details.getState());
        employee.setPincode(details.getPincode());
        employee.setPhotoUrl(details.getPhotoUrl());
        employee.setBankAccountNumber(details.getBankAccountNumber());
        employee.setBankName(details.getBankName());
        employee.setBankIfsc(details.getBankIfsc());
        employee.setBankBranch(details.getBankBranch());
        // New fields
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
