package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.ShiftClosingDataDTO;
import com.stopforfuel.backend.dto.ShiftClosingSubmitDTO;
import com.stopforfuel.backend.dto.ShiftDTO;
import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.service.ShiftService;
import com.stopforfuel.backend.service.ShiftTestDataSeeder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shifts")
public class ShiftController {

    private final ShiftService service;
    private final ShiftTestDataSeeder testDataSeeder;

    public ShiftController(ShiftService service,
                           @Autowired(required = false) ShiftTestDataSeeder testDataSeeder) {
        this.service = service;
        this.testDataSeeder = testDataSeeder;
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<ShiftDTO> getAll() {
        return service.getAllShifts().stream().map(ShiftDTO::from).toList();
    }

    @GetMapping("/active")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public ShiftDTO getActive() {
        Shift active = service.getActiveShift();
        return active != null ? ShiftDTO.from(active) : null;
    }

    @PostMapping("/open")
    @PreAuthorize("hasPermission(null, 'SHIFT_CREATE')")
    public ShiftDTO open(@Valid @RequestBody Shift shift) {
        return ShiftDTO.from(service.openShift(shift));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasPermission(null, 'SHIFT_UPDATE')")
    public ShiftDTO close(@PathVariable Long id) {
        return ShiftDTO.from(service.closeShift(id));
    }

    // ========== Shift Closing Workspace ==========

    @GetMapping("/{id}/closing-data")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public ShiftClosingDataDTO getClosingData(@PathVariable Long id) {
        return service.getShiftClosingData(id);
    }

    @PostMapping("/{id}/submit-for-review")
    @PreAuthorize("hasPermission(null, 'SHIFT_UPDATE')")
    public ShiftDTO submitForReview(@PathVariable Long id, @Valid @RequestBody ShiftClosingSubmitDTO dto) {
        return ShiftDTO.from(service.submitForReview(id, dto));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasPermission(null, 'SHIFT_UPDATE')")
    public ShiftDTO approve(@PathVariable Long id) {
        return ShiftDTO.from(service.approveAndClose(id));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasPermission(null, 'SHIFT_UPDATE')")
    public ShiftDTO reopen(@PathVariable Long id) {
        return ShiftDTO.from(service.reopenForReview(id));
    }

    @PostMapping("/{id}/reopen-to-edit")
    @PreAuthorize("hasPermission(null, 'SHIFT_UPDATE')")
    public ShiftDTO reopenToEdit(@PathVariable Long id) {
        return ShiftDTO.from(service.reopenToEdit(id));
    }

    @PostMapping("/{id}/seed-test-data")
    @PreAuthorize("hasPermission(null, 'SHIFT_UPDATE')")
    public Map<String, Object> seedTestData(@PathVariable Long id) {
        if (testDataSeeder == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Test data seeding is not available in this environment");
        }
        return testDataSeeder.seedTestData(id);
    }
}
