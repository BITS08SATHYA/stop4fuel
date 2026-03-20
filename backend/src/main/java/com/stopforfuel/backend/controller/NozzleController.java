package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.entity.Nozzle;
import com.stopforfuel.backend.service.NozzleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/nozzles")
public class NozzleController {

    @Autowired
    private NozzleService nozzleService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'STATION_VIEW')")
    public List<Nozzle> getAllNozzles() {
        return nozzleService.getAllNozzles();
    }

    @GetMapping("/active")
    @PreAuthorize("hasPermission(null, 'STATION_VIEW')")
    public List<Nozzle> getActiveNozzles() {
        return nozzleService.getActiveNozzles();
    }

    @GetMapping("/tank/{tankId}")
    @PreAuthorize("hasPermission(null, 'STATION_VIEW')")
    public List<Nozzle> getNozzlesByTank(@PathVariable Long tankId) {
        return nozzleService.getNozzlesByTank(tankId);
    }

    @GetMapping("/pump/{pumpId}")
    @PreAuthorize("hasPermission(null, 'STATION_VIEW')")
    public List<Nozzle> getNozzlesByPump(@PathVariable Long pumpId) {
        return nozzleService.getNozzlesByPump(pumpId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'STATION_VIEW')")
    public Nozzle getNozzleById(@PathVariable Long id) {
        return nozzleService.getNozzleById(id);
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'STATION_MANAGE')")
    public Nozzle createNozzle(@Valid @RequestBody Nozzle nozzle) {
        return nozzleService.createNozzle(nozzle);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'STATION_MANAGE')")
    public Nozzle updateNozzle(@PathVariable Long id, @Valid @RequestBody Nozzle nozzle) {
        return nozzleService.updateNozzle(id, nozzle);
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasPermission(null, 'STATION_MANAGE')")
    public Nozzle toggleStatus(@PathVariable Long id) {
        return nozzleService.toggleStatus(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'STATION_MANAGE')")
    public ResponseEntity<?> deleteNozzle(@PathVariable Long id) {
        nozzleService.deleteNozzle(id);
        return ResponseEntity.ok().build();
    }
}
