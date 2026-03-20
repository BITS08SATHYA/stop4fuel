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

    @Cacheable(value = "permissions", key = "#roleType + ':' + #permissionCode")
    public boolean hasPermission(String roleType, String permissionCode) {
        if ("OWNER".equalsIgnoreCase(roleType)) {
            return true;
        }
        Roles role = rolesRepository.findByRoleType(roleType).orElse(null);
        if (role == null) return false;
        return rolePermissionRepository.existsByRoleIdAndPermissionCode(role.getId(), permissionCode);
    }

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

    public Map<String, List<Permission>> getAllPermissionsGrouped() {
        List<Permission> all = permissionRepository.findAllByOrderByModuleAscCodeAsc();
        return all.stream().collect(Collectors.groupingBy(
                Permission::getModule,
                LinkedHashMap::new,
                Collectors.toList()
        ));
    }

    public List<Permission> getAllPermissions() {
        return permissionRepository.findAllByOrderByModuleAscCodeAsc();
    }
}
