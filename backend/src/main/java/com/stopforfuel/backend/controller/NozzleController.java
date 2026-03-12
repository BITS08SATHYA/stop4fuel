package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.Nozzle;
import com.stopforfuel.backend.service.NozzleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/nozzles")
@CrossOrigin(origins = "*")
public class NozzleController {

    @Autowired
    private NozzleService nozzleService;

    @GetMapping
    public List<Nozzle> getAllNozzles() {
        return nozzleService.getAllNozzles();
    }

    @GetMapping("/active")
    public List<Nozzle> getActiveNozzles() {
        return nozzleService.getActiveNozzles();
    }

    @GetMapping("/tank/{tankId}")
    public List<Nozzle> getNozzlesByTank(@PathVariable Long tankId) {
        return nozzleService.getNozzlesByTank(tankId);
    }

    @GetMapping("/pump/{pumpId}")
    public List<Nozzle> getNozzlesByPump(@PathVariable Long pumpId) {
        return nozzleService.getNozzlesByPump(pumpId);
    }

    @GetMapping("/{id}")
    public Nozzle getNozzleById(@PathVariable Long id) {
        return nozzleService.getNozzleById(id);
    }

    @PostMapping
    public Nozzle createNozzle(@RequestBody Nozzle nozzle) {
        return nozzleService.createNozzle(nozzle);
    }

    @PutMapping("/{id}")
    public Nozzle updateNozzle(@PathVariable Long id, @RequestBody Nozzle nozzle) {
        return nozzleService.updateNozzle(id, nozzle);
    }

    @PatchMapping("/{id}/toggle-status")
    public Nozzle toggleStatus(@PathVariable Long id) {
        return nozzleService.toggleStatus(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteNozzle(@PathVariable Long id) {
        nozzleService.deleteNozzle(id);
        return ResponseEntity.ok().build();
    }
}
