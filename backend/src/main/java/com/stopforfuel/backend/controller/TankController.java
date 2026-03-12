package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.Tank;
import com.stopforfuel.backend.service.TankService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tanks")
@CrossOrigin(origins = "*")
public class TankController {

    @Autowired
    private TankService tankService;

    @GetMapping
    public List<Tank> getAllTanks() {
        return tankService.getAllTanks();
    }

    @GetMapping("/active")
    public List<Tank> getActiveTanks() {
        return tankService.getActiveTanks();
    }

    @GetMapping("/product/{productId}")
    public List<Tank> getTanksByProduct(@PathVariable Long productId) {
        return tankService.getTanksByProduct(productId);
    }

    @GetMapping("/{id}")
    public Tank getTankById(@PathVariable Long id) {
        return tankService.getTankById(id);
    }

    @PostMapping
    public Tank createTank(@RequestBody Tank tank) {
        return tankService.createTank(tank);
    }

    @PutMapping("/{id}")
    public Tank updateTank(@PathVariable Long id, @RequestBody Tank tank) {
        return tankService.updateTank(id, tank);
    }

    @PatchMapping("/{id}/toggle-status")
    public Tank toggleStatus(@PathVariable Long id) {
        return tankService.toggleStatus(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTank(@PathVariable Long id) {
        tankService.deleteTank(id);
        return ResponseEntity.ok().build();
    }
}
