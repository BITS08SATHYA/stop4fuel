package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Customer;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends ScidRepository<Customer> {
    org.springframework.data.domain.Page<Customer> findByNameContainingIgnoreCaseOrPhoneNumbersContaining(String name, String phone, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"group", "party", "customerCategory", "phoneNumbers", "emails"})
    org.springframework.data.domain.Page<Customer> findByGroupId(Long groupId, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"group", "party", "customerCategory", "phoneNumbers", "emails"})
    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT c FROM Customer c LEFT JOIN c.phoneNumbers p WHERE c.group.id = :groupId AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR p LIKE CONCAT('%', :search, '%'))")
    org.springframework.data.domain.Page<Customer> findByGroupIdAndSearch(@org.springframework.data.repository.query.Param("groupId") Long groupId, @org.springframework.data.repository.query.Param("search") String search, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"group", "party", "customerCategory", "phoneNumbers", "emails"})
    @org.springframework.data.jpa.repository.Query("SELECT c FROM Customer c LEFT JOIN c.customerCategory cc WHERE " +
            "(:groupId IS NULL OR c.group.id = :groupId) " +
            "AND (:status IS NULL OR c.status = :status) " +
            "AND (:categoryType IS NULL OR cc.categoryType = :categoryType)")
    org.springframework.data.domain.Page<Customer> findByGroupAndStatus(
            @org.springframework.data.repository.query.Param("groupId") Long groupId,
            @org.springframework.data.repository.query.Param("status") String status,
            @org.springframework.data.repository.query.Param("categoryType") String categoryType,
            org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"group", "party", "customerCategory", "phoneNumbers", "emails"})
    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT c FROM Customer c LEFT JOIN c.phoneNumbers p LEFT JOIN c.customerCategory cc WHERE " +
            "(LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR p LIKE CONCAT('%', :search, '%')) " +
            "AND (:groupId IS NULL OR c.group.id = :groupId) " +
            "AND (:status IS NULL OR c.status = :status) " +
            "AND (:categoryType IS NULL OR cc.categoryType = :categoryType)")
    org.springframework.data.domain.Page<Customer> findBySearchAndFilters(
            @org.springframework.data.repository.query.Param("search") String search,
            @org.springframework.data.repository.query.Param("groupId") Long groupId,
            @org.springframework.data.repository.query.Param("status") String status,
            @org.springframework.data.repository.query.Param("categoryType") String categoryType,
            org.springframework.data.domain.Pageable pageable);

    java.util.List<Customer> findByGroupIsNull();
    java.util.List<Customer> findByIdIn(java.util.List<Long> ids);
}
