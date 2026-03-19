package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.entity.SalaryPayment;
import com.stopforfuel.backend.service.SalaryPaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/salary")
public class SalaryPaymentController {

    @Autowired
    private SalaryPaymentService salaryPaymentService;

    @GetMapping
    public List<SalaryPayment> getMonthlyPayments(
            @RequestParam Integer month,
            @RequestParam Integer year) {
        return salaryPaymentService.getMonthlyPayments(month, year);
    }

    @GetMapping("/employee/{employeeId}")
    public List<SalaryPayment> getEmployeePayments(@PathVariable Long employeeId) {
        return salaryPaymentService.getEmployeePayments(employeeId);
    }

    @PostMapping("/process")
    public List<SalaryPayment> processMonthlyPayroll(
            @RequestParam Integer month,
            @RequestParam Integer year) {
        return salaryPaymentService.processMonthlyPayroll(month, year);
    }

    @PatchMapping("/{id}/pay")
    public ResponseEntity<SalaryPayment> markAsPaid(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            String paymentMode = body != null ? body.get("paymentMode") : "CASH";
            return ResponseEntity.ok(salaryPaymentService.markAsPaid(id, paymentMode));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<SalaryPayment> updatePayment(@PathVariable Long id, @Valid @RequestBody SalaryPayment payment) {
        try {
            return ResponseEntity.ok(salaryPaymentService.updatePayment(id, payment));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
