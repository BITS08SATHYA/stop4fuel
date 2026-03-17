package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.Employee;
import com.stopforfuel.backend.entity.EmployeeAdvance;
import com.stopforfuel.backend.entity.SalaryHistory;
import com.stopforfuel.backend.service.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;

    @GetMapping
    public List<Employee> getAllEmployees() {
        return employeeService.getAllEmployees();
    }

    @GetMapping("/active")
    public List<Employee> getActiveEmployees() {
        return employeeService.getActiveEmployees();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Employee> getEmployeeById(@PathVariable Long id) {
        return employeeService.getEmployeeById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Employee createEmployee(@RequestBody Employee employee) {
        return employeeService.createEmployee(employee);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Employee> updateEmployee(@PathVariable Long id, @RequestBody Employee employee) {
        try {
            return ResponseEntity.ok(employeeService.updateEmployee(id, employee));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmployee(@PathVariable Long id) {
        employeeService.deleteEmployee(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/salary-history")
    public List<SalaryHistory> getSalaryHistory(@PathVariable Long id) {
        return employeeService.getSalaryHistory(id);
    }

    @PostMapping("/{id}/salary-revision")
    public SalaryHistory addSalaryRevision(@PathVariable Long id, @RequestBody SalaryHistory history) {
        Employee employee = new Employee();
        employee.setId(id);
        history.setEmployee(employee);
        return employeeService.addSalaryRevision(history);
    }

    @GetMapping("/{id}/advances")
    public List<EmployeeAdvance> getAdvances(@PathVariable Long id) {
        return employeeService.getAdvances(id);
    }

    @PostMapping("/{id}/advances")
    public EmployeeAdvance addAdvance(@PathVariable Long id, @RequestBody EmployeeAdvance advance) {
        Employee employee = new Employee();
        employee.setId(id);
        advance.setEmployee(employee);
        return employeeService.addAdvance(advance);
    }

    @PatchMapping("/advances/{advanceId}/status")
    public ResponseEntity<EmployeeAdvance> updateAdvanceStatus(@PathVariable Long advanceId, @RequestParam String status) {
        try {
            return ResponseEntity.ok(employeeService.updateAdvanceStatus(advanceId, status));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
