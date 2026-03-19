package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.service.ShiftService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shifts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ShiftController {

    private final ShiftService service;

    @GetMapping
    public List<Shift> getAll() {
        return service.getAllShifts();
    }

    @GetMapping("/active")
    public Shift getActive() {
        return service.getActiveShift();
    }

    @PostMapping("/open")
    public Shift open(@Valid @RequestBody Shift shift) {
        return service.openShift(shift);
    }

    @PostMapping("/{id}/close")
    public Shift close(@PathVariable Long id) {
        return service.closeShift(id);
    }
}
