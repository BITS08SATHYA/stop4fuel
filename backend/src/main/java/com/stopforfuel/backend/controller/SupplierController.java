package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
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
    public List<Supplier> getAll() {
        return service.getAllSuppliers();
    }

    @GetMapping("/active")
    @PreAuthorize("hasPermission(null, 'PRODUCT_VIEW')")
    public List<Supplier> getActive() {
        return service.getActiveSuppliers();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PRODUCT_VIEW')")
    public Supplier getById(@PathVariable Long id) {
        return service.getSupplierById(id);
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'PRODUCT_MANAGE')")
    public Supplier create(@Valid @RequestBody Supplier supplier) {
        return service.createSupplier(supplier);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PRODUCT_MANAGE')")
    public Supplier update(@PathVariable Long id, @Valid @RequestBody Supplier supplier) {
        return service.updateSupplier(id, supplier);
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasPermission(null, 'PRODUCT_MANAGE')")
    public Supplier toggleStatus(@PathVariable Long id) {
        return service.toggleStatus(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PRODUCT_MANAGE')")
    public void delete(@PathVariable Long id) {
        service.deleteSupplier(id);
    }
}
