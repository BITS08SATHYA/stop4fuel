package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.PartyDTO;
import com.stopforfuel.backend.entity.Party;
import com.stopforfuel.backend.repository.PartyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/parties")
@RequiredArgsConstructor
public class PartyController {

    private final PartyRepository repository;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'CUSTOMER_VIEW')")
    public List<Party> getAll() {
        return repository.findAll();
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'CUSTOMER_CREATE')")
    public Party create(@Valid @RequestBody Party party) {
        return repository.save(party);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
