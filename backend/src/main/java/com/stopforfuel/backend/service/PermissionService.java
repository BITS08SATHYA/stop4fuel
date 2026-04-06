package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Permission;
import com.stopforfuel.backend.entity.RolePermission;
import com.stopforfuel.backend.entity.Roles;
import com.stopforfuel.backend.repository.PermissionRepository;
import com.stopforfuel.backend.repository.RolePermissionRepository;
import com.stopforfuel.backend.repository.RolesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final RolesRepository rolesRepository;

    @Transactional(readOnly = true)
    @Cacheable(value = "permissions", key = "#roleType + ':' + #permissionCode")
    public boolean hasPermission(String roleType, String permissionCode) {
        if ("OWNER".equalsIgnoreCase(roleType)) {
            return true;
        }
        Roles role = rolesRepository.findByRoleType(roleType).orElse(null);
        if (role == null) return false;
        return rolePermissionRepository.existsByRoleIdAndPermissionCode(role.getId(), permissionCode);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "rolePermissions", key = "#roleType")
    public List<String> getPermissionsForRole(String roleType) {
        if ("OWNER".equalsIgnoreCase(roleType)) {
            return permissionRepository.findAll().stream()
                    .map(Permission::getCode)
                    .collect(Collectors.toList());
        }
        return rolePermissionRepository.findByRoleRoleType(roleType).stream()
                .map(rp -> rp.getPermission().getCode())
                .collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = {"permissions", "rolePermissions"}, allEntries = true)
    public void grantPermissionByRoleAndCode(String roleType, String permissionCode) {
        Roles role = rolesRepository.findByRoleType(roleType)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleType));
        if (rolePermissionRepository.existsByRoleIdAndPermissionCode(role.getId(), permissionCode)) return;
        Permission perm = permissionRepository.findByCode(permissionCode).orElse(null);
        if (perm == null) return;
        RolePermission rp = new RolePermission();
        rp.setRole(role);
        rp.setPermission(perm);
        rolePermissionRepository.save(rp);
    }

    @CacheEvict(value = {"permissions", "rolePermissions"}, allEntries = true)
    public void clearCaches() {
        // Spring handles cache eviction via annotation
    }

    @Transactional
    @CacheEvict(value = {"permissions", "rolePermissions"}, allEntries = true)
    public void grantPermission(Long roleId, Long permissionId) {
        if (rolePermissionRepository.existsByRoleIdAndPermissionCode(
                roleId,
                permissionRepository.findById(permissionId).map(Permission::getCode).orElse(""))) {
            return;
        }
        RolePermission rp = new RolePermission();
        rp.setRole(rolesRepository.findById(roleId).orElseThrow());
        rp.setPermission(permissionRepository.findById(permissionId).orElseThrow());
        rolePermissionRepository.save(rp);
    }

    @Transactional
    @CacheEvict(value = {"permissions", "rolePermissions"}, allEntries = true)
    public void revokePermission(Long roleId, Long permissionId) {
        rolePermissionRepository.deleteByRoleIdAndPermissionId(roleId, permissionId);
    }

    @Transactional
    @CacheEvict(value = {"permissions", "rolePermissions"}, allEntries = true)
    public void updatePermissionsForRole(String roleType, List<String> permissionCodes) {
        Roles role = rolesRepository.findByRoleType(roleType)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleType));

        rolePermissionRepository.deleteByRoleId(role.getId());

        for (String code : permissionCodes) {
            Permission permission = permissionRepository.findByCode(code).orElse(null);
            if (permission != null) {
                RolePermission rp = new RolePermission();
                rp.setRole(role);
                rp.setPermission(permission);
                rolePermissionRepository.save(rp);
            }
        }
    }

    @Transactional(readOnly = true)
    public Map<String, List<Permission>> getAllPermissionsGrouped() {
        List<Permission> all = permissionRepository.findAllByOrderByModuleAscCodeAsc();
        return all.stream().collect(Collectors.groupingBy(
                Permission::getModule,
                LinkedHashMap::new,
                Collectors.toList()
        ));
    }

    @Transactional(readOnly = true)
    public List<Permission> getAllPermissions() {
        return permissionRepository.findAllByOrderByModuleAscCodeAsc();
    }

    @Transactional
    @CacheEvict(value = {"permissions", "rolePermissions"}, allEntries = true)
    public List<Permission> createModulePermissions(String module, String description) {
        String mod = module.toUpperCase().trim();
        if (permissionRepository.existsByCode(mod + "_VIEW")) {
            throw new RuntimeException("Module '" + mod + "' already exists");
        }

        String desc = (description != null && !description.isBlank()) ? description : mod.toLowerCase();
        String[][] actions = {
            {"VIEW", "View " + desc},
            {"CREATE", "Create " + desc},
            {"UPDATE", "Update " + desc},
            {"DELETE", "Delete " + desc}
        };

        List<Permission> created = new ArrayList<>();
        for (String[] action : actions) {
            Permission p = new Permission(
                mod + "_" + action[0], action[1], mod, action[0], false
            );
            created.add(permissionRepository.save(p));
        }
        return created;
    }

    @Transactional
    @CacheEvict(value = {"permissions", "rolePermissions"}, allEntries = true)
    public void deleteModulePermissions(String module) {
        List<Permission> perms = permissionRepository.findByModule(module.toUpperCase());
        if (perms.isEmpty()) {
            throw new RuntimeException("Module not found: " + module);
        }
        if (perms.stream().anyMatch(Permission::isSystemDefault)) {
            throw new RuntimeException("Cannot delete system default module: " + module);
        }
        for (Permission p : perms) {
            rolePermissionRepository.deleteByPermissionId(p.getId());
        }
        permissionRepository.deleteByModule(module.toUpperCase());
    }
}
