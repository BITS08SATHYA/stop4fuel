package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.SupplierDTO;
import com.stopforfuel.backend.entity.Supplier;
import com.stopforfuel.backend.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService service;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'PRODUCT_VIEW')")
    public List<SupplierDTO> getAll() {
        return service.getAllSuppliers().stream().map(SupplierDTO::from).toList();
    }

    @GetMapping("/active")
    @PreAuthorize("hasPermission(null, 'PRODUCT_VIEW')")
    public List<SupplierDTO> getActive() {
        return service.getActiveSuppliers().stream().map(SupplierDTO::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PRODUCT_VIEW')")
    public SupplierDTO getById(@PathVariable Long id) {
        return SupplierDTO.from(service.getSupplierById(id));
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'PRODUCT_CREATE')")
    public SupplierDTO create(@Valid @RequestBody Supplier supplier) {
        return SupplierDTO.from(service.createSupplier(supplier));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PRODUCT_UPDATE')")
    public SupplierDTO update(@PathVariable Long id, @Valid @RequestBody Supplier supplier) {
        return SupplierDTO.from(service.updateSupplier(id, supplier));
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasPermission(null, 'PRODUCT_UPDATE')")
    public SupplierDTO toggleStatus(@PathVariable Long id) {
        return SupplierDTO.from(service.toggleStatus(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PRODUCT_DELETE')")
    public void delete(@PathVariable Long id) {
        service.deleteSupplier(id);
    }
}
