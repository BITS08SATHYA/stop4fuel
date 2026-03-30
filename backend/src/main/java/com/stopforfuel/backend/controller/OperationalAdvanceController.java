package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.InvoiceBillDTO;
import com.stopforfuel.backend.dto.OperationalAdvanceDTO;
import com.stopforfuel.backend.entity.OperationalAdvance;
import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.service.OperationalAdvanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/operational-advances")
@RequiredArgsConstructor
public class OperationalAdvanceController {

    private final OperationalAdvanceService service;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<OperationalAdvanceDTO> getAll() {
        return service.getAll().stream().map(OperationalAdvanceDTO::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public OperationalAdvanceDTO getById(@PathVariable Long id) {
        return OperationalAdvanceDTO.from(service.getById(id));
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<OperationalAdvanceDTO> getByStatus(@PathVariable String status) {
        return service.getByStatus(status).stream().map(OperationalAdvanceDTO::from).toList();
    }

    @GetMapping("/shift/{shiftId}")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<OperationalAdvanceDTO> getByShift(@PathVariable Long shiftId) {
        return service.getByShift(shiftId).stream().map(OperationalAdvanceDTO::from).toList();
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<OperationalAdvanceDTO> getByEmployee(@PathVariable Long employeeId) {
        return service.getByEmployee(employeeId).stream().map(OperationalAdvanceDTO::from).toList();
    }

    @GetMapping("/type/{advanceType}")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<OperationalAdvanceDTO> getByType(@PathVariable String advanceType) {
        return service.getByType(advanceType).stream().map(OperationalAdvanceDTO::from).toList();
    }

    @GetMapping("/search")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<OperationalAdvanceDTO> search(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return service.getByDateRange(fromDate, toDate).stream().map(OperationalAdvanceDTO::from).toList();
    }

    @GetMapping("/outstanding")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<OperationalAdvanceDTO> getOutstanding() {
        return service.getOutstanding().stream().map(OperationalAdvanceDTO::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'SHIFT_MANAGE')")
    public OperationalAdvanceDTO create(@Valid @RequestBody OperationalAdvance advance) {
        return OperationalAdvanceDTO.from(service.create(advance));
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("hasPermission(null, 'SHIFT_MANAGE')")
    public OperationalAdvanceDTO recordReturn(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BigDecimal returnedAmount = new BigDecimal(body.get("returnedAmount").toString());
        String returnRemarks = body.get("returnRemarks") != null ? body.get("returnRemarks").toString() : null;
        return OperationalAdvanceDTO.from(service.recordReturn(id, returnedAmount, returnRemarks));
    }

    @PostMapping("/{id}/invoices/{invoiceId}")
    @PreAuthorize("hasPermission(null, 'SHIFT_MANAGE')")
    public OperationalAdvanceDTO assignInvoice(@PathVariable Long id, @PathVariable Long invoiceId) {
        return OperationalAdvanceDTO.from(service.assignInvoice(id, invoiceId));
    }

    @DeleteMapping("/{id}/invoices/{invoiceId}")
    @PreAuthorize("hasPermission(null, 'SHIFT_MANAGE')")
    public OperationalAdvanceDTO unassignInvoice(@PathVariable Long id, @PathVariable Long invoiceId) {
        return OperationalAdvanceDTO.from(service.unassignInvoice(id, invoiceId));
    }

    @GetMapping("/{id}/invoices")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<InvoiceBillDTO> getAssignedInvoices(@PathVariable Long id) {
        return service.getAssignedInvoices(id).stream().map(InvoiceBillDTO::from).toList();
    }

    @PostMapping("/{id}/statement/{statementId}")
    @PreAuthorize("hasPermission(null, 'SHIFT_MANAGE')")
    public OperationalAdvanceDTO assignStatement(@PathVariable Long id, @PathVariable Long statementId) {
        return OperationalAdvanceDTO.from(service.assignStatement(id, statementId));
    }

    @DeleteMapping("/{id}/statement")
    @PreAuthorize("hasPermission(null, 'SHIFT_MANAGE')")
    public OperationalAdvanceDTO unassignStatement(@PathVariable Long id) {
        return OperationalAdvanceDTO.from(service.unassignStatement(id));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasPermission(null, 'SHIFT_MANAGE')")
    public OperationalAdvanceDTO updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return OperationalAdvanceDTO.from(service.updateStatus(id, body.get("status")));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasPermission(null, 'SHIFT_MANAGE')")
    public OperationalAdvanceDTO cancel(@PathVariable Long id) {
        return OperationalAdvanceDTO.from(service.cancel(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'SHIFT_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
