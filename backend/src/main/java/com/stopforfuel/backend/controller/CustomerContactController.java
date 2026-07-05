package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.CustomerContact;
import com.stopforfuel.backend.service.CustomerContactService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/customers")
public class CustomerContactController {

    private final CustomerContactService service;

    @GetMapping("/{customerId}/contacts")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_VIEW')")
    public ResponseEntity<List<CustomerContact>> getByCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(service.getByCustomer(customerId));
    }

    @PostMapping("/{customerId}/contacts")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_UPDATE')")
    public ResponseEntity<CustomerContact> create(@PathVariable Long customerId,
                                                  @Valid @RequestBody CustomerContact contact) {
        return ResponseEntity.ok(service.create(customerId, contact));
    }

    @PutMapping("/contacts/{id}")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_UPDATE')")
    public ResponseEntity<CustomerContact> update(@PathVariable Long id,
                                                  @Valid @RequestBody CustomerContact contact) {
        return ResponseEntity.ok(service.update(id, contact));
    }

    @DeleteMapping("/contacts/{id}")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_UPDATE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
