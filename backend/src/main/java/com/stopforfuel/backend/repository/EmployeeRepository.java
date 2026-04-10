package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Employee;
import com.stopforfuel.backend.enums.EntityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends ScidRepository<Employee> {
    Optional<Employee> findByAadharNumber(String aadharNumber);
    List<Employee> findByScid(Long scid);
    List<Employee> findByStatusAndScid(EntityStatus status, Long scid);
    long countByScidAndStatus(Long scid, EntityStatus status);

    @Query("SELECT e FROM Employee e WHERE e.scid = :scid " +
           "AND (:search IS NULL OR LOWER(e.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "OR LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "AND (:status IS NULL OR CAST(e.status AS string) = CAST(:status AS string))")
    Page<Employee> findBySearchAndStatus(@Param("search") String search, @Param("status") String status, @Param("scid") Long scid, Pageable pageable);
}
