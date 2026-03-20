package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.entity.Employee;
import com.stopforfuel.backend.entity.EmployeeAdvance;
import com.stopforfuel.backend.entity.SalaryHistory;
import com.stopforfuel.backend.service.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_VIEW')")
    public List<Employee> getAllEmployees() {
        return employeeService.getAllEmployees();
    }

    @GetMapping("/active")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_VIEW')")
    public List<Employee> getActiveEmployees() {
        return employeeService.getActiveEmployees();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_VIEW')")
    public ResponseEntity<Employee> getEmployeeById(@PathVariable Long id) {
        return employeeService.getEmployeeById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_MANAGE')")
    public Employee createEmployee(@Valid @RequestBody Employee employee) {
        return employeeService.createEmployee(employee);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_MANAGE')")
    public ResponseEntity<Employee> updateEmployee(@PathVariable Long id, @Valid @RequestBody Employee employee) {
        try {
            return ResponseEntity.ok(employeeService.updateEmployee(id, employee));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_MANAGE')")
    public ResponseEntity<Void> deleteEmployee(@PathVariable Long id) {
        employeeService.deleteEmployee(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/salary-history")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_VIEW')")
    public List<SalaryHistory> getSalaryHistory(@PathVariable Long id) {
        return employeeService.getSalaryHistory(id);
    }

    @PostMapping("/{id}/salary-revision")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_MANAGE')")
    public SalaryHistory addSalaryRevision(@PathVariable Long id, @Valid @RequestBody SalaryHistory history) {
        Employee employee = new Employee();
        employee.setId(id);
        history.setEmployee(employee);
        return employeeService.addSalaryRevision(history);
    }

    @GetMapping("/{id}/advances")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_VIEW')")
    public List<EmployeeAdvance> getAdvances(@PathVariable Long id) {
        return employeeService.getAdvances(id);
    }

    @PostMapping("/{id}/advances")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_MANAGE')")
    public EmployeeAdvance addAdvance(@PathVariable Long id, @Valid @RequestBody EmployeeAdvance advance) {
        Employee employee = new Employee();
        employee.setId(id);
        advance.setEmployee(employee);
        return employeeService.addAdvance(advance);
    }

    @PatchMapping("/advances/{advanceId}/status")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_MANAGE')")
    public ResponseEntity<EmployeeAdvance> updateAdvanceStatus(@PathVariable Long advanceId, @RequestParam String status) {
        try {
            return ResponseEntity.ok(employeeService.updateAdvanceStatus(advanceId, status));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── File Upload Endpoints ───────────────────────────────────

    @PostMapping("/{id}/upload-photo")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_MANAGE')")
    public ResponseEntity<?> uploadPhoto(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(employeeService.uploadPhoto(id, file));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/upload-aadhar-doc")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_MANAGE')")
    public ResponseEntity<?> uploadAadharDoc(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(employeeService.uploadAadharDoc(id, file));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/upload-pan-doc")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_MANAGE')")
    public ResponseEntity<?> uploadPanDoc(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(employeeService.uploadPanDoc(id, file));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}/file-url")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_VIEW')")
    public ResponseEntity<?> getFileUrl(@PathVariable Long id, @RequestParam String type) {
        try {
            String url = employeeService.getFilePresignedUrl(id, type);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
