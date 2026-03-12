package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {
    Optional<Group> findByGroupName(String groupName);
}
