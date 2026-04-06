package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.NozzleDTO;
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
    public List<NozzleDTO> getAllNozzles() {
        return nozzleService.getAllNozzles().stream().map(NozzleDTO::from).toList();
    }

    @GetMapping("/active")
    @PreAuthorize("hasPermission(null, 'STATION_VIEW')")
    public List<NozzleDTO> getActiveNozzles() {
        return nozzleService.getActiveNozzles().stream().map(NozzleDTO::from).toList();
    }

    @GetMapping("/tank/{tankId}")
    @PreAuthorize("hasPermission(null, 'STATION_VIEW')")
    public List<NozzleDTO> getNozzlesByTank(@PathVariable Long tankId) {
        return nozzleService.getNozzlesByTank(tankId).stream().map(NozzleDTO::from).toList();
    }

    @GetMapping("/pump/{pumpId}")
    @PreAuthorize("hasPermission(null, 'STATION_VIEW')")
    public List<NozzleDTO> getNozzlesByPump(@PathVariable Long pumpId) {
        return nozzleService.getNozzlesByPump(pumpId).stream().map(NozzleDTO::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'STATION_VIEW')")
    public NozzleDTO getNozzleById(@PathVariable Long id) {
        return NozzleDTO.from(nozzleService.getNozzleById(id));
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'STATION_CREATE')")
    public NozzleDTO createNozzle(@Valid @RequestBody Nozzle nozzle) {
        return NozzleDTO.from(nozzleService.createNozzle(nozzle));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'STATION_UPDATE')")
    public NozzleDTO updateNozzle(@PathVariable Long id, @Valid @RequestBody Nozzle nozzle) {
        return NozzleDTO.from(nozzleService.updateNozzle(id, nozzle));
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasPermission(null, 'STATION_UPDATE')")
    public NozzleDTO toggleStatus(@PathVariable Long id) {
        return NozzleDTO.from(nozzleService.toggleStatus(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'STATION_DELETE')")
    public ResponseEntity<?> deleteNozzle(@PathVariable Long id) {
        nozzleService.deleteNozzle(id);
        return ResponseEntity.ok().build();
    }
}
