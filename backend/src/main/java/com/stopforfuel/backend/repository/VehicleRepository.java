package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    Optional<Vehicle> findByVehicleNumber(String vehicleNumber);
    java.util.List<Vehicle> findByVehicleNumberContainingIgnoreCase(String vehicleNumber);
    java.util.List<Vehicle> findByCustomerId(Long customerId);
    java.util.List<Vehicle> findByCustomerIsNull();
    java.util.List<Vehicle> findByIdIn(java.util.List<Long> ids);
}
