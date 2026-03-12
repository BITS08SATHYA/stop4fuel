package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Roles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RolesRepository extends JpaRepository<Roles, Long> {
    Optional<Roles> findByRoleType(String roleType);
}
