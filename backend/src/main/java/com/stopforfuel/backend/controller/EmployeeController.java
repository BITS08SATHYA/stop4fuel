package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.EmployeeDetailDTO;
import com.stopforfuel.backend.dto.EmployeeListDTO;
import com.stopforfuel.backend.entity.Employee;
import com.stopforfuel.backend.entity.EmployeeAdvance;
import com.stopforfuel.backend.entity.SalaryHistory;
import com.stopforfuel.backend.service.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    public Page<EmployeeListDTO> getEmployees(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return employeeService.getEmployees(search, status, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")))
                .map(EmployeeListDTO::from);
    }

    @GetMapping("/all")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_VIEW')")
    public List<EmployeeListDTO> getAllEmployees() {
        return employeeService.getAllEmployees().stream()
                .map(EmployeeListDTO::from).toList();
    }

    @GetMapping("/active")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_VIEW')")
    public List<EmployeeListDTO> getActiveEmployees() {
        return employeeService.getActiveEmployees().stream()
                .map(EmployeeListDTO::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_VIEW')")
    public ResponseEntity<EmployeeDetailDTO> getEmployeeById(@PathVariable Long id) {
        return employeeService.getEmployeeById(id)
                .map(EmployeeDetailDTO::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_MANAGE')")
    public EmployeeDetailDTO createEmployee(@RequestBody Employee employee) {
        return EmployeeDetailDTO.from(employeeService.createEmployee(employee));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_MANAGE')")
    public ResponseEntity<EmployeeDetailDTO> updateEmployee(@PathVariable Long id, @RequestBody Employee employee) {
        return ResponseEntity.ok(EmployeeDetailDTO.from(employeeService.updateEmployee(id, employee)));
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
