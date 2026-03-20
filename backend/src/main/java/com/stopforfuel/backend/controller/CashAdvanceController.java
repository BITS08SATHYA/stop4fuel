package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.entity.CashAdvance;
import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.service.CashAdvanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/advances")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class CashAdvanceController {

    private final CashAdvanceService cashAdvanceService;

    @GetMapping
    public List<CashAdvance> getAll() {
        return cashAdvanceService.getAll();
    }

    @GetMapping("/{id}")
    public CashAdvance getById(@PathVariable Long id) {
        return cashAdvanceService.getById(id);
    }

    @GetMapping("/status/{status}")
    public List<CashAdvance> getByStatus(@PathVariable String status) {
        return cashAdvanceService.getByStatus(status);
    }

    @GetMapping("/shift/{shiftId}")
    public List<CashAdvance> getByShift(@PathVariable Long shiftId) {
        return cashAdvanceService.getByShift(shiftId);
    }

    @GetMapping("/employee/{employeeId}")
    public List<CashAdvance> getByEmployee(@PathVariable Long employeeId) {
        return cashAdvanceService.getByEmployee(employeeId);
    }

    @GetMapping("/outstanding")
    public List<CashAdvance> getOutstanding() {
        return cashAdvanceService.getOutstanding();
    }

    @PostMapping
    public CashAdvance create(@Valid @RequestBody CashAdvance advance) {
        return cashAdvanceService.create(advance);
    }

    @PostMapping("/{id}/return")
    public CashAdvance recordReturn(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BigDecimal returnedAmount = new BigDecimal(body.get("returnedAmount").toString());
        String returnRemarks = body.get("returnRemarks") != null ? body.get("returnRemarks").toString() : null;
        return cashAdvanceService.recordReturn(id, returnedAmount, returnRemarks);
    }

    // Invoice assignment
    @PostMapping("/{id}/invoices/{invoiceId}")
    public CashAdvance assignInvoice(@PathVariable Long id, @PathVariable Long invoiceId) {
        return cashAdvanceService.assignInvoice(id, invoiceId);
    }

    @DeleteMapping("/{id}/invoices/{invoiceId}")
    public CashAdvance unassignInvoice(@PathVariable Long id, @PathVariable Long invoiceId) {
        return cashAdvanceService.unassignInvoice(id, invoiceId);
    }

    @GetMapping("/{id}/invoices")
    public List<InvoiceBill> getAssignedInvoices(@PathVariable Long id) {
        return cashAdvanceService.getAssignedInvoices(id);
    }

    // Statement assignment
    @PostMapping("/{id}/statement/{statementId}")
    public CashAdvance assignStatement(@PathVariable Long id, @PathVariable Long statementId) {
        return cashAdvanceService.assignStatement(id, statementId);
    }

    @DeleteMapping("/{id}/statement")
    public CashAdvance unassignStatement(@PathVariable Long id) {
        return cashAdvanceService.unassignStatement(id);
    }

    @PatchMapping("/{id}/cancel")
    public CashAdvance cancel(@PathVariable Long id) {
        return cashAdvanceService.cancel(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        cashAdvanceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
