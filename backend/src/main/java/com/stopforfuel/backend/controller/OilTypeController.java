package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.OilTypeDTO;
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
    public List<OilTypeDTO> getAll() {
        return service.getAllOilTypes().stream().map(OilTypeDTO::from).toList();
    }

    @GetMapping("/active")
    @PreAuthorize("hasPermission(null, 'PRODUCT_VIEW')")
    public List<OilTypeDTO> getActive() {
        return service.getActiveOilTypes().stream().map(OilTypeDTO::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PRODUCT_VIEW')")
    public OilTypeDTO getById(@PathVariable Long id) {
        return OilTypeDTO.from(service.getOilTypeById(id));
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'PRODUCT_CREATE')")
    public OilTypeDTO create(@Valid @RequestBody OilType oilType) {
        return OilTypeDTO.from(service.createOilType(oilType));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PRODUCT_UPDATE')")
    public OilTypeDTO update(@PathVariable Long id, @Valid @RequestBody OilType oilType) {
        return OilTypeDTO.from(service.updateOilType(id, oilType));
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasPermission(null, 'PRODUCT_UPDATE')")
    public OilTypeDTO toggleStatus(@PathVariable Long id) {
        return OilTypeDTO.from(service.toggleStatus(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PRODUCT_DELETE')")
    public void delete(@PathVariable Long id) {
        service.deleteOilType(id);
    }
}
