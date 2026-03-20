package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByCognitoId(String cognitoId);
    Optional<User> findByUsername(String username);
    boolean existsByCognitoId(String cognitoId);
    List<User> findByStatus(String status);
}
