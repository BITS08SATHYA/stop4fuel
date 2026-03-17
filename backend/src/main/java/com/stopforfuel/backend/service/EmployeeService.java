package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Employee;
import com.stopforfuel.backend.entity.EmployeeAdvance;
import com.stopforfuel.backend.entity.SalaryHistory;
import com.stopforfuel.backend.repository.EmployeeAdvanceRepository;
import com.stopforfuel.backend.repository.EmployeeRepository;
import com.stopforfuel.backend.repository.SalaryHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
}
