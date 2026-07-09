package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.Roles;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.exception.DuplicateResourceException;
import com.stopforfuel.backend.exception.ResourceNotFoundException;
import com.stopforfuel.backend.repository.RolesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RolesController {

    // Seeded roles referenced by code paths (user sync, employee account creation) — not deletable/renamable
    private static final Set<String> PROTECTED_ROLES = Set.of("CUSTOMER", "EMPLOYEE", "DEALER", "OWNER", "ADMIN", "CASHIER");

    private final RolesRepository rolesRepository;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_VIEW')")
    public List<Roles> getAll() {
        return rolesRepository.findAll(Sort.by("roleType"));
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_CREATE')")
    public ResponseEntity<Roles> create(@Valid @RequestBody Roles role) {
        String roleType = normalize(role.getRoleType());
        if (rolesRepository.findByRoleType(roleType).isPresent()) {
            throw new DuplicateResourceException("Role '" + roleType + "' already exists");
        }
        Roles toSave = new Roles();
        toSave.setRoleType(roleType);
        return ResponseEntity.ok(rolesRepository.save(toSave));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_UPDATE')")
    public ResponseEntity<Roles> update(@PathVariable Long id, @Valid @RequestBody Roles details) {
        Roles role = rolesRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
        if (PROTECTED_ROLES.contains(role.getRoleType())) {
            throw new BusinessException("System role '" + role.getRoleType() + "' cannot be renamed");
        }
        String roleType = normalize(details.getRoleType());
        rolesRepository.findByRoleType(roleType).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new DuplicateResourceException("Role '" + roleType + "' already exists");
            }
        });
        role.setRoleType(roleType);
        return ResponseEntity.ok(rolesRepository.save(role));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Roles role = rolesRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
        if (PROTECTED_ROLES.contains(role.getRoleType())) {
            throw new BusinessException("System role '" + role.getRoleType() + "' cannot be deleted");
        }
        rolesRepository.delete(role);
        return ResponseEntity.ok().build();
    }

    private String normalize(String roleType) {
        if (roleType == null || roleType.isBlank()) {
            throw new BusinessException("Role name is required");
        }
        return roleType.trim().toUpperCase().replaceAll("\\s+", "_");
    }
}
