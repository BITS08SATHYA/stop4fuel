package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.Incentive;
import com.stopforfuel.backend.service.IncentiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/incentives")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class IncentiveController {

    private final IncentiveService service;

    @GetMapping
    public List<Incentive> getAll() {
        return service.getAll();
    }

    @GetMapping("/customer/{customerId}")
    public List<Incentive> getByCustomer(@PathVariable Long customerId) {
        return service.getByCustomer(customerId);
    }

    @PostMapping
    public Incentive create(@RequestBody Incentive incentive) {
        return service.create(incentive);
    }

    @PutMapping("/{id}")
    public Incentive update(@PathVariable Long id, @RequestBody Incentive incentive) {
        return service.update(id, incentive);
    }

    @DeleteMapping("/{id}")
    public void deactivate(@PathVariable Long id) {
        service.deactivate(id);
    }
}
