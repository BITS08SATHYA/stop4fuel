package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.EAdvanceDTO;
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
    public List<EAdvanceDTO> getAll() {
        return service.getAll().stream().map(EAdvanceDTO::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public EAdvanceDTO getById(@PathVariable Long id) {
        return EAdvanceDTO.from(service.getById(id));
    }

    @GetMapping("/shift/{shiftId}")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<EAdvanceDTO> getByShift(@PathVariable Long shiftId) {
        return service.getByShift(shiftId).stream().map(EAdvanceDTO::from).toList();
    }

    @GetMapping("/shift/{shiftId}/summary")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public Map<String, BigDecimal> getShiftSummary(@PathVariable Long shiftId) {
        return service.getShiftSummary(shiftId);
    }

    @GetMapping("/type/{advanceType}")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<EAdvanceDTO> getByType(@PathVariable String advanceType) {
        return service.getByType(advanceType).stream().map(EAdvanceDTO::from).toList();
    }

    @GetMapping("/search")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<EAdvanceDTO> search(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String type) {
        return service.getByDateRange(fromDate, toDate, type).stream().map(EAdvanceDTO::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'SHIFT_CREATE')")
    public EAdvanceDTO create(@Valid @RequestBody EAdvance eAdvance) {
        return EAdvanceDTO.from(service.create(eAdvance));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'SHIFT_UPDATE')")
    public EAdvanceDTO update(@PathVariable Long id, @Valid @RequestBody EAdvance eAdvance) {
        return EAdvanceDTO.from(service.update(id, eAdvance));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'SHIFT_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
