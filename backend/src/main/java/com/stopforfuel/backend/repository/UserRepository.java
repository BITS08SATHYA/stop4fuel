package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends ScidRepository<User> {
    Optional<User> findByCognitoId(String cognitoId);
    Optional<User> findByUsername(String username);
    boolean existsByCognitoId(String cognitoId);
    List<User> findByStatus(String status);

    @Query("SELECT u FROM User u JOIN u.phoneNumbers p WHERE p = :phone")
    Optional<User> findByPhoneNumber(@Param("phone") String phone);

    @Query("SELECT u FROM User u JOIN u.phoneNumbers p WHERE p = :phone AND u.scid = :scid")
    Optional<User> findByPhoneNumberAndScid(@Param("phone") String phone, @Param("scid") Long scid);
}
