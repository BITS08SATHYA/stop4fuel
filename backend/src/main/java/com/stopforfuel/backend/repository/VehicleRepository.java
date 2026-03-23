package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
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
}
