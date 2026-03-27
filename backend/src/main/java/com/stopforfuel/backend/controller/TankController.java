package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.entity.Tank;
import com.stopforfuel.backend.service.TankService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tanks")
public class TankController {

    @Autowired
    private TankService tankService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'STATION_VIEW')")
    public List<Tank> getAllTanks() {
        return tankService.getAllTanks();
    }

    @GetMapping("/active")
    @PreAuthorize("hasPermission(null, 'STATION_VIEW')")
    public List<Tank> getActiveTanks() {
        return tankService.getActiveTanks();
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasPermission(null, 'STATION_VIEW')")
    public List<Tank> getLowStockTanks() {
        return tankService.getLowStockTanks();
    }

    @GetMapping("/product/{productId}")
    @PreAuthorize("hasPermission(null, 'STATION_VIEW')")
    public List<Tank> getTanksByProduct(@PathVariable Long productId) {
        return tankService.getTanksByProduct(productId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'STATION_VIEW')")
    public Tank getTankById(@PathVariable Long id) {
        return tankService.getTankById(id);
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'STATION_MANAGE')")
    public Tank createTank(@Valid @RequestBody Tank tank) {
        return tankService.createTank(tank);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'STATION_MANAGE')")
    public Tank updateTank(@PathVariable Long id, @Valid @RequestBody Tank tank) {
        return tankService.updateTank(id, tank);
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasPermission(null, 'STATION_MANAGE')")
    public Tank toggleStatus(@PathVariable Long id) {
        return tankService.toggleStatus(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'STATION_MANAGE')")
    public ResponseEntity<?> deleteTank(@PathVariable Long id) {
        tankService.deleteTank(id);
        return ResponseEntity.ok().build();
    }
}
