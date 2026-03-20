package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.entity.OilType;
import com.stopforfuel.backend.service.OilTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/oil-types")
@RequiredArgsConstructor
public class OilTypeController {

    private final OilTypeService service;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'PRODUCT_VIEW')")
    public List<OilType> getAll() {
        return service.getAllOilTypes();
    }

    @GetMapping("/active")
    @PreAuthorize("hasPermission(null, 'PRODUCT_VIEW')")
    public List<OilType> getActive() {
        return service.getActiveOilTypes();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PRODUCT_VIEW')")
    public OilType getById(@PathVariable Long id) {
        return service.getOilTypeById(id);
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'PRODUCT_MANAGE')")
    public OilType create(@Valid @RequestBody OilType oilType) {
        return service.createOilType(oilType);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PRODUCT_MANAGE')")
    public OilType update(@PathVariable Long id, @Valid @RequestBody OilType oilType) {
        return service.updateOilType(id, oilType);
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasPermission(null, 'PRODUCT_MANAGE')")
    public OilType toggleStatus(@PathVariable Long id) {
        return service.toggleStatus(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PRODUCT_MANAGE')")
    public void delete(@PathVariable Long id) {
        service.deleteOilType(id);
    }
}
