package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.entity.EAdvance;
import com.stopforfuel.backend.service.EAdvanceService;
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
@RequestMapping("/api/e-advances")
@RequiredArgsConstructor
public class EAdvanceController {

    private final EAdvanceService service;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<EAdvance> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public EAdvance getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @GetMapping("/shift/{shiftId}")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<EAdvance> getByShift(@PathVariable Long shiftId) {
        return service.getByShift(shiftId);
    }

    @GetMapping("/shift/{shiftId}/summary")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public Map<String, BigDecimal> getShiftSummary(@PathVariable Long shiftId) {
        return service.getShiftSummary(shiftId);
    }

    @GetMapping("/type/{advanceType}")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<EAdvance> getByType(@PathVariable String advanceType) {
        return service.getByType(advanceType);
    }

    @GetMapping("/search")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<EAdvance> search(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String type) {
        return service.getByDateRange(fromDate, toDate, type);
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'SHIFT_MANAGE')")
    public EAdvance create(@Valid @RequestBody EAdvance eAdvance) {
        return service.create(eAdvance);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'SHIFT_MANAGE')")
    public EAdvance update(@PathVariable Long id, @Valid @RequestBody EAdvance eAdvance) {
        return service.update(id, eAdvance);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'SHIFT_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
