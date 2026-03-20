package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.entity.Pump;
import com.stopforfuel.backend.service.PumpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pumps")
public class PumpController {

    @Autowired
    private PumpService pumpService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'STATION_VIEW')")
    public List<Pump> getAllPumps() {
        return pumpService.getAllPumps();
    }

    @GetMapping("/active")
    @PreAuthorize("hasPermission(null, 'STATION_VIEW')")
    public List<Pump> getActivePumps() {
        return pumpService.getActivePumps();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'STATION_VIEW')")
    public Pump getPumpById(@PathVariable Long id) {
        return pumpService.getPumpById(id);
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'STATION_MANAGE')")
    public Pump createPump(@Valid @RequestBody Pump pump) {
        return pumpService.createPump(pump);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'STATION_MANAGE')")
    public Pump updatePump(@PathVariable Long id, @Valid @RequestBody Pump pump) {
        return pumpService.updatePump(id, pump);
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasPermission(null, 'STATION_MANAGE')")
    public Pump toggleStatus(@PathVariable Long id) {
        return pumpService.toggleStatus(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'STATION_MANAGE')")
    public ResponseEntity<?> deletePump(@PathVariable Long id) {
        pumpService.deletePump(id);
        return ResponseEntity.ok().build();
    }
}
