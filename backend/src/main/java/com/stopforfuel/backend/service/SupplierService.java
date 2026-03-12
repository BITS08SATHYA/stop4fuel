package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Supplier;
import com.stopforfuel.backend.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository repository;

    public List<Supplier> getAllSuppliers() {
        return repository.findAll();
    }

    public List<Supplier> getActiveSuppliers() {
        return repository.findByActiveTrue();
    }

    public Supplier getSupplierById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + id));
    }

    public Supplier toggleStatus(Long id) {
        Supplier supplier = getSupplierById(id);
        supplier.setActive(!supplier.isActive());
        return repository.save(supplier);
    }

    public Supplier createSupplier(Supplier supplier) {
        if (supplier.getScid() == null) {
            supplier.setScid(1L);
        }
        return repository.save(supplier);
    }

    public Supplier updateSupplier(Long id, Supplier details) {
        Supplier supplier = repository.findById(id).orElseThrow();
        supplier.setName(details.getName());
        supplier.setContactPerson(details.getContactPerson());
        supplier.setPhone(details.getPhone());
        supplier.setEmail(details.getEmail());
        supplier.setActive(details.isActive());
        return repository.save(supplier);
    }

    public void deleteSupplier(Long id) {
        repository.deleteById(id);
    }
}
