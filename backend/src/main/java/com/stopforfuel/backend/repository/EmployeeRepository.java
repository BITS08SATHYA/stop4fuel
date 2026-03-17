package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    List<Employee> findByScid(Long scid);
    List<Employee> findByStatus(String status);
}
