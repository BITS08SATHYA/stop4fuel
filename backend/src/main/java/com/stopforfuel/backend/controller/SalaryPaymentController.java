package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.SalaryPaymentDTO;
import com.stopforfuel.backend.entity.SalaryPayment;
import com.stopforfuel.backend.service.SalaryPaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/salary")
public class SalaryPaymentController {

    @Autowired
    private SalaryPaymentService salaryPaymentService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_VIEW')")
    public List<SalaryPaymentDTO> getMonthlyPayments(
            @RequestParam Integer month,
            @RequestParam Integer year) {
        return salaryPaymentService.getMonthlyPayments(month, year).stream().map(SalaryPaymentDTO::from).toList();
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_VIEW')")
    public List<SalaryPaymentDTO> getEmployeePayments(@PathVariable Long employeeId) {
        return salaryPaymentService.getEmployeePayments(employeeId).stream().map(SalaryPaymentDTO::from).toList();
    }

    @PostMapping("/process")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_CREATE')")
    public List<SalaryPaymentDTO> processMonthlyPayroll(
            @RequestParam Integer month,
            @RequestParam Integer year) {
        return salaryPaymentService.processMonthlyPayroll(month, year).stream().map(SalaryPaymentDTO::from).toList();
    }

    @PatchMapping("/{id}/pay")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_UPDATE')")
    public ResponseEntity<SalaryPaymentDTO> markAsPaid(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            String modeStr = body != null && body.get("paymentMode") != null ? body.get("paymentMode") : "CASH";
            com.stopforfuel.backend.enums.PaymentMode paymentMode = com.stopforfuel.backend.enums.PaymentMode.valueOf(modeStr);
            return ResponseEntity.ok(SalaryPaymentDTO.from(salaryPaymentService.markAsPaid(id, paymentMode)));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_UPDATE')")
    public ResponseEntity<SalaryPaymentDTO> updatePayment(@PathVariable Long id, @Valid @RequestBody SalaryPayment payment) {
        try {
            return ResponseEntity.ok(SalaryPaymentDTO.from(salaryPaymentService.updatePayment(id, payment)));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
