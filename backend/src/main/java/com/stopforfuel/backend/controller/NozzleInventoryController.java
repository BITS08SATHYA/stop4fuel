package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.NozzleInventory;
import com.stopforfuel.backend.service.NozzleInventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/inventory/nozzles")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class NozzleInventoryController {

    private final NozzleInventoryService service;

    @GetMapping
    public List<NozzleInventory> getAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long nozzleId) {
        if (date != null) return service.getByDate(date);
        if (nozzleId != null) return service.getByNozzleId(nozzleId);
        return service.getAll();
    }

    @GetMapping("/{id}")
    public NozzleInventory getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    public NozzleInventory create(@RequestBody NozzleInventory inventory) {
        return service.save(inventory);
    }

    @PutMapping("/{id}")
    public NozzleInventory update(@PathVariable Long id, @RequestBody NozzleInventory inventory) {
        return service.update(id, inventory);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
