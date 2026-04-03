package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.dto.DesignationDTO;
import com.stopforfuel.backend.entity.Designation;
import com.stopforfuel.backend.repository.DesignationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/designations")
@RequiredArgsConstructor
public class DesignationController {

    private final DesignationRepository designationRepository;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_VIEW')")
    public List<Designation> getAll() {
        return designationRepository.findAll();
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_MANAGE')")
    public ResponseEntity<Designation> create(@Valid @RequestBody Designation designation) {
        return ResponseEntity.ok(designationRepository.save(designation));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_MANAGE')")
    public ResponseEntity<Designation> update(@PathVariable Long id, @Valid @RequestBody Designation details) {
        Designation designation = designationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Designation not found"));
        designation.setName(details.getName());
        designation.setDefaultRole(details.getDefaultRole());
        designation.setDescription(details.getDescription());
        return ResponseEntity.ok(designationRepository.save(designation));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        designationRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
