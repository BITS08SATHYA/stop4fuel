package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Vehicle;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Vehicle v WHERE v.id = :id")
    Optional<Vehicle> findByIdForUpdate(@Param("id") Long id);
    Optional<Vehicle> findByVehicleNumber(String vehicleNumber);

    @Query("SELECT v FROM Vehicle v LEFT JOIN FETCH v.customer WHERE LOWER(v.vehicleNumber) LIKE LOWER(CONCAT('%', :search, '%'))")
    List<Vehicle> findByVehicleNumberContainingIgnoreCaseWithCustomer(@Param("search") String search);

    @Query("SELECT v FROM Vehicle v LEFT JOIN FETCH v.customer")
    List<Vehicle> findAllWithCustomer();

    @Query("SELECT v FROM Vehicle v LEFT JOIN FETCH v.customer WHERE v.customer.id = :customerId")
    List<Vehicle> findByCustomerIdWithCustomer(@Param("customerId") Long customerId);

    List<Vehicle> findByVehicleNumberContainingIgnoreCase(String vehicleNumber);
    List<Vehicle> findByCustomerId(Long customerId);
    List<Vehicle> findByCustomerIsNull();
    List<Vehicle> findByIdIn(List<Long> ids);

    // Vehicle extends SimpleBaseEntity (no scid field) — use count() instead
}
