package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.IncentivePaymentDTO;
import com.stopforfuel.backend.entity.IncentivePayment;
import com.stopforfuel.backend.service.IncentivePaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/incentive-payments")
@RequiredArgsConstructor
public class IncentivePaymentController {

    private final IncentivePaymentService service;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<IncentivePaymentDTO> getAll() {
        return service.getAll().stream().map(IncentivePaymentDTO::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public IncentivePaymentDTO getById(@PathVariable Long id) {
        return IncentivePaymentDTO.from(service.getById(id));
    }

    @GetMapping("/shift/{shiftId}")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<IncentivePaymentDTO> getByShift(@PathVariable Long shiftId) {
        return service.getByShift(shiftId).stream().map(IncentivePaymentDTO::from).toList();
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<IncentivePaymentDTO> getByCustomer(@PathVariable Long customerId) {
        return service.getByCustomer(customerId).stream().map(IncentivePaymentDTO::from).toList();
    }

    @GetMapping("/shift/{shiftId}/total")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public BigDecimal getShiftTotal(@PathVariable Long shiftId) {
        return service.sumByShift(shiftId);
    }

    @GetMapping("/search")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<IncentivePaymentDTO> search(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return service.getByDateRange(fromDate, toDate).stream().map(IncentivePaymentDTO::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'SHIFT_MANAGE')")
    public IncentivePaymentDTO create(@Valid @RequestBody IncentivePayment payment) {
        return IncentivePaymentDTO.from(service.create(payment));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'SHIFT_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
