package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.TankDTO;
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
    public List<TankDTO> getAllTanks() {
        return tankService.getAllTanks().stream().map(TankDTO::from).toList();
    }

    @GetMapping("/active")
    @PreAuthorize("hasPermission(null, 'STATION_VIEW')")
    public List<TankDTO> getActiveTanks() {
        return tankService.getActiveTanks().stream().map(TankDTO::from).toList();
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasPermission(null, 'STATION_VIEW')")
    public List<TankDTO> getLowStockTanks() {
        return tankService.getLowStockTanks().stream().map(TankDTO::from).toList();
    }

    @GetMapping("/product/{productId}")
    @PreAuthorize("hasPermission(null, 'STATION_VIEW')")
    public List<TankDTO> getTanksByProduct(@PathVariable Long productId) {
        return tankService.getTanksByProduct(productId).stream().map(TankDTO::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'STATION_VIEW')")
    public TankDTO getTankById(@PathVariable Long id) {
        return TankDTO.from(tankService.getTankById(id));
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'STATION_MANAGE')")
    public TankDTO createTank(@Valid @RequestBody Tank tank) {
        return TankDTO.from(tankService.createTank(tank));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'STATION_MANAGE')")
    public TankDTO updateTank(@PathVariable Long id, @Valid @RequestBody Tank tank) {
        return TankDTO.from(tankService.updateTank(id, tank));
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasPermission(null, 'STATION_MANAGE')")
    public TankDTO toggleStatus(@PathVariable Long id) {
        return TankDTO.from(tankService.toggleStatus(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'STATION_MANAGE')")
    public ResponseEntity<?> deleteTank(@PathVariable Long id) {
        tankService.deleteTank(id);
        return ResponseEntity.ok().build();
    }
}
