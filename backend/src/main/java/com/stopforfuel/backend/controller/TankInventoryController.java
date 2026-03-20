package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.entity.TankInventory;
import com.stopforfuel.backend.service.TankInventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/inventory/tanks")
@RequiredArgsConstructor
public class TankInventoryController {

    private final TankInventoryService service;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'INVENTORY_VIEW')")
    public List<TankInventory> getAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long tankId) {
        if (date != null) return service.getByDate(date);
        if (tankId != null) return service.getByTankId(tankId);
        return service.getAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTORY_VIEW')")
    public TankInventory getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'INVENTORY_MANAGE')")
    public TankInventory create(@Valid @RequestBody TankInventory inventory) {
        return service.save(inventory);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTORY_MANAGE')")
    public TankInventory update(@PathVariable Long id, @Valid @RequestBody TankInventory inventory) {
        return service.update(id, inventory);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTORY_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
