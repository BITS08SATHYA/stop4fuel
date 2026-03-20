package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
    List<RolePermission> findByRoleId(Long roleId);
    List<RolePermission> findByRoleRoleType(String roleType);
    boolean existsByRoleIdAndPermissionCode(Long roleId, String permissionCode);
    void deleteByRoleIdAndPermissionId(Long roleId, Long permissionId);
    void deleteByRoleId(Long roleId);
}
