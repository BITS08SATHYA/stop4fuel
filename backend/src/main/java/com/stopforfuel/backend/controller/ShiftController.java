package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.service.ShiftService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shifts")
@RequiredArgsConstructor
public class ShiftController {

    private final ShiftService service;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<Shift> getAll() {
        return service.getAllShifts();
    }

    @GetMapping("/active")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public Shift getActive() {
        return service.getActiveShift();
    }

    @PostMapping("/open")
    @PreAuthorize("hasPermission(null, 'SHIFT_MANAGE')")
    public Shift open(@Valid @RequestBody Shift shift) {
        return service.openShift(shift);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasPermission(null, 'SHIFT_MANAGE')")
    public Shift close(@PathVariable Long id) {
        return service.closeShift(id);
    }
}
