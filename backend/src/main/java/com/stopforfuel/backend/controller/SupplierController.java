package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.Supplier;
import com.stopforfuel.backend.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SupplierController {

    private final SupplierService service;

    @GetMapping
    public List<Supplier> getAll() {
        return service.getAllSuppliers();
    }

    @GetMapping("/active")
    public List<Supplier> getActive() {
        return service.getActiveSuppliers();
    }

    @GetMapping("/{id}")
    public Supplier getById(@PathVariable Long id) {
        return service.getSupplierById(id);
    }

    @PostMapping
    public Supplier create(@RequestBody Supplier supplier) {
        return service.createSupplier(supplier);
    }

    @PutMapping("/{id}")
    public Supplier update(@PathVariable Long id, @RequestBody Supplier supplier) {
        return service.updateSupplier(id, supplier);
    }

    @PatchMapping("/{id}/toggle-status")
    public Supplier toggleStatus(@PathVariable Long id) {
        return service.toggleStatus(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.deleteSupplier(id);
    }
}
