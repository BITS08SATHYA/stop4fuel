package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    org.springframework.data.domain.Page<Customer> findByNameContainingIgnoreCaseOrPhoneNumbersContaining(String name, String phone, org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<Customer> findByGroupId(Long groupId, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT c FROM Customer c WHERE c.group.id = :groupId AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR c.phoneNumbers LIKE CONCAT('%', :search, '%'))")
    org.springframework.data.domain.Page<Customer> findByGroupIdAndSearch(@org.springframework.data.repository.query.Param("groupId") Long groupId, @org.springframework.data.repository.query.Param("search") String search, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT c FROM Customer c WHERE " +
            "(:search IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR c.phoneNumbers LIKE CONCAT('%', :search, '%')) " +
            "AND (:groupId IS NULL OR c.group.id = :groupId) " +
            "AND (:status IS NULL OR c.status = :status)")
    org.springframework.data.domain.Page<Customer> findByFilters(
            @org.springframework.data.repository.query.Param("search") String search,
            @org.springframework.data.repository.query.Param("groupId") Long groupId,
            @org.springframework.data.repository.query.Param("status") String status,
            org.springframework.data.domain.Pageable pageable);

    java.util.List<Customer> findByGroupIsNull();
    java.util.List<Customer> findByIdIn(java.util.List<Long> ids);
}
