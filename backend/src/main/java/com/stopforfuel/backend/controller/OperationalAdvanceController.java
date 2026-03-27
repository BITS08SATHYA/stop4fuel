package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.entity.OperationalAdvance;
import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.service.OperationalAdvanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/operational-advances")
@RequiredArgsConstructor
public class OperationalAdvanceController {

    private final OperationalAdvanceService service;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<OperationalAdvance> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public OperationalAdvance getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<OperationalAdvance> getByStatus(@PathVariable String status) {
        return service.getByStatus(status);
    }

    @GetMapping("/shift/{shiftId}")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<OperationalAdvance> getByShift(@PathVariable Long shiftId) {
        return service.getByShift(shiftId);
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<OperationalAdvance> getByEmployee(@PathVariable Long employeeId) {
        return service.getByEmployee(employeeId);
    }

    @GetMapping("/type/{advanceType}")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<OperationalAdvance> getByType(@PathVariable String advanceType) {
        return service.getByType(advanceType);
    }

    @GetMapping("/outstanding")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<OperationalAdvance> getOutstanding() {
        return service.getOutstanding();
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'SHIFT_MANAGE')")
    public OperationalAdvance create(@Valid @RequestBody OperationalAdvance advance) {
        return service.create(advance);
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("hasPermission(null, 'SHIFT_MANAGE')")
    public OperationalAdvance recordReturn(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BigDecimal returnedAmount = new BigDecimal(body.get("returnedAmount").toString());
        String returnRemarks = body.get("returnRemarks") != null ? body.get("returnRemarks").toString() : null;
        return service.recordReturn(id, returnedAmount, returnRemarks);
    }

    @PostMapping("/{id}/invoices/{invoiceId}")
    @PreAuthorize("hasPermission(null, 'SHIFT_MANAGE')")
    public OperationalAdvance assignInvoice(@PathVariable Long id, @PathVariable Long invoiceId) {
        return service.assignInvoice(id, invoiceId);
    }

    @DeleteMapping("/{id}/invoices/{invoiceId}")
    @PreAuthorize("hasPermission(null, 'SHIFT_MANAGE')")
    public OperationalAdvance unassignInvoice(@PathVariable Long id, @PathVariable Long invoiceId) {
        return service.unassignInvoice(id, invoiceId);
    }

    @GetMapping("/{id}/invoices")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<InvoiceBill> getAssignedInvoices(@PathVariable Long id) {
        return service.getAssignedInvoices(id);
    }

    @PostMapping("/{id}/statement/{statementId}")
    @PreAuthorize("hasPermission(null, 'SHIFT_MANAGE')")
    public OperationalAdvance assignStatement(@PathVariable Long id, @PathVariable Long statementId) {
        return service.assignStatement(id, statementId);
    }

    @DeleteMapping("/{id}/statement")
    @PreAuthorize("hasPermission(null, 'SHIFT_MANAGE')")
    public OperationalAdvance unassignStatement(@PathVariable Long id) {
        return service.unassignStatement(id);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasPermission(null, 'SHIFT_MANAGE')")
    public OperationalAdvance updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.updateStatus(id, body.get("status"));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasPermission(null, 'SHIFT_MANAGE')")
    public OperationalAdvance cancel(@PathVariable Long id) {
        return service.cancel(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'SHIFT_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
