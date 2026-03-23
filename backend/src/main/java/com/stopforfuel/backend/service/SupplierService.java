package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Supplier;
import com.stopforfuel.backend.repository.SupplierRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository repository;

    public List<Supplier> getAllSuppliers() {
        return repository.findAllByScid(SecurityUtils.getScid());
    }

    public List<Supplier> getActiveSuppliers() {
        return repository.findByActiveTrueAndScid(SecurityUtils.getScid());
    }

    public Supplier getSupplierById(Long id) {
        return repository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + id));
    }

    public Supplier toggleStatus(Long id) {
        Supplier supplier = getSupplierById(id);
        supplier.setActive(!supplier.isActive());
        return repository.save(supplier);
    }

    public Supplier createSupplier(Supplier supplier) {
        if (supplier.getScid() == null) {
            supplier.setScid(SecurityUtils.getScid());
        }
        return repository.save(supplier);
    }

    public Supplier updateSupplier(Long id, Supplier details) {
        Supplier supplier = repository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + id));
        supplier.setName(details.getName());
        supplier.setContactPerson(details.getContactPerson());
        supplier.setPhone(details.getPhone());
        supplier.setEmail(details.getEmail());
        supplier.setActive(details.isActive());
        return repository.save(supplier);
    }

    public void deleteSupplier(Long id) {
        Supplier supplier = getSupplierById(id);
        repository.delete(supplier);
    }
}
