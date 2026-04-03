package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.PumpDTO;
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
    public List<PumpDTO> getAllPumps() {
        return pumpService.getAllPumps().stream().map(PumpDTO::from).toList();
    }

    @GetMapping("/active")
    @PreAuthorize("hasPermission(null, 'STATION_VIEW')")
    public List<PumpDTO> getActivePumps() {
        return pumpService.getActivePumps().stream().map(PumpDTO::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'STATION_VIEW')")
    public PumpDTO getPumpById(@PathVariable Long id) {
        return PumpDTO.from(pumpService.getPumpById(id));
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'STATION_MANAGE')")
    public PumpDTO createPump(@Valid @RequestBody Pump pump) {
        return PumpDTO.from(pumpService.createPump(pump));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'STATION_MANAGE')")
    public PumpDTO updatePump(@PathVariable Long id, @Valid @RequestBody Pump pump) {
        return PumpDTO.from(pumpService.updatePump(id, pump));
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasPermission(null, 'STATION_MANAGE')")
    public PumpDTO toggleStatus(@PathVariable Long id) {
        return PumpDTO.from(pumpService.toggleStatus(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'STATION_MANAGE')")
    public ResponseEntity<?> deletePump(@PathVariable Long id) {
        pumpService.deletePump(id);
        return ResponseEntity.ok().build();
    }
}
