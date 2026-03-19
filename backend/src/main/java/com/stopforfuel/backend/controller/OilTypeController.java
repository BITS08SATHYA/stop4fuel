package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.OilType;
import com.stopforfuel.backend.service.OilTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/oil-types")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class OilTypeController {

    private final OilTypeService service;

    @GetMapping
    public List<OilType> getAll() {
        return service.getAllOilTypes();
    }

    @GetMapping("/active")
    public List<OilType> getActive() {
        return service.getActiveOilTypes();
    }

    @GetMapping("/{id}")
    public OilType getById(@PathVariable Long id) {
        return service.getOilTypeById(id);
    }

    @PostMapping
    public OilType create(@RequestBody OilType oilType) {
        return service.createOilType(oilType);
    }

    @PutMapping("/{id}")
    public OilType update(@PathVariable Long id, @RequestBody OilType oilType) {
        return service.updateOilType(id, oilType);
    }

    @PatchMapping("/{id}/toggle-status")
    public OilType toggleStatus(@PathVariable Long id) {
        return service.toggleStatus(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.deleteOilType(id);
    }
}
