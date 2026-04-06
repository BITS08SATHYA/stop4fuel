package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.IncentiveDTO;
import com.stopforfuel.backend.entity.Incentive;
import com.stopforfuel.backend.service.IncentiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/incentives")
@RequiredArgsConstructor
public class IncentiveController {

    private final IncentiveService service;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'CUSTOMER_VIEW')")
    public List<Incentive> getAll() {
        return service.getAll();
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_VIEW')")
    public List<Incentive> getByCustomer(@PathVariable Long customerId) {
        return service.getByCustomer(customerId);
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'CUSTOMER_CREATE')")
    public Incentive create(@Valid @RequestBody Incentive incentive) {
        return service.create(incentive);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_UPDATE')")
    public Incentive update(@PathVariable Long id, @Valid @RequestBody Incentive incentive) {
        return service.update(id, incentive);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_DELETE')")
    public void deactivate(@PathVariable Long id) {
        service.deactivate(id);
    }
}
