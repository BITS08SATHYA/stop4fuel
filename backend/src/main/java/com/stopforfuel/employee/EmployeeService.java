package com.stopforfuel.employee;

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

    public Employee createEmployee(Employee employee) {
        return employeeRepository.save(employee);
    }

    public Employee updateEmployee(Long id, Employee employeeDetails) {
        Employee employee = employeeRepository.findById(id).orElseThrow(() -> new RuntimeException("Employee not found"));

        employee.setName(employeeDetails.getName());
        employee.setDesignation(employeeDetails.getDesignation());
        employee.setEmail(employeeDetails.getEmail());
        employee.setPhone(employeeDetails.getPhone());
        employee.setAdditionalPhones(employeeDetails.getAdditionalPhones());
        employee.setSalary(employeeDetails.getSalary());
        employee.setJoinDate(employeeDetails.getJoinDate());
        employee.setStatus(employeeDetails.getStatus());
        employee.setAadharNumber(employeeDetails.getAadharNumber());
        employee.setAddress(employeeDetails.getAddress());
        employee.setCity(employeeDetails.getCity());
        employee.setState(employeeDetails.getState());
        employee.setPincode(employeeDetails.getPincode());
        employee.setPhotoUrl(employeeDetails.getPhotoUrl());
        employee.setBankAccountNumber(employeeDetails.getBankAccountNumber());
        employee.setBankName(employeeDetails.getBankName());
        employee.setBankIfsc(employeeDetails.getBankIfsc());
        employee.setBankBranch(employeeDetails.getBankBranch());

        return employeeRepository.save(employee);
    }

    public void deleteEmployee(Long id) {
        employeeRepository.deleteById(id);
    }

    public List<SalaryHistory> getSalaryHistory(Long employeeId) {
        return salaryHistoryRepository.findByEmployeeIdOrderByEffectiveDateDesc(employeeId);
    }

    public SalaryHistory addSalaryRevision(SalaryHistory history) {
        Employee employee = history.getEmployee();
        employee = employeeRepository.findById(employee.getId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        history.setOldSalary(employee.getSalary());
        employee.setSalary(history.getNewSalary());
        employeeRepository.save(employee);

        history.setEmployee(employee);
        return salaryHistoryRepository.save(history);
    }

    public List<EmployeeAdvance> getAdvances(Long employeeId) {
        return employeeAdvanceRepository.findByEmployeeIdOrderByAdvanceDateDesc(employeeId);
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
