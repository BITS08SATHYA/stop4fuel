package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.Pump;
import com.stopforfuel.backend.service.PumpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pumps")
@CrossOrigin(origins = "*")
public class PumpController {

    @Autowired
    private PumpService pumpService;

    @GetMapping
    public List<Pump> getAllPumps() {
        return pumpService.getAllPumps();
    }

    @GetMapping("/active")
    public List<Pump> getActivePumps() {
        return pumpService.getActivePumps();
    }

    @GetMapping("/{id}")
    public Pump getPumpById(@PathVariable Long id) {
        return pumpService.getPumpById(id);
    }

    @PostMapping
    public Pump createPump(@RequestBody Pump pump) {
        return pumpService.createPump(pump);
    }

    @PutMapping("/{id}")
    public Pump updatePump(@PathVariable Long id, @RequestBody Pump pump) {
        return pumpService.updatePump(id, pump);
    }

    @PatchMapping("/{id}/toggle-status")
    public Pump toggleStatus(@PathVariable Long id) {
        return pumpService.toggleStatus(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePump(@PathVariable Long id) {
        pumpService.deletePump(id);
        return ResponseEntity.ok().build();
    }
}
