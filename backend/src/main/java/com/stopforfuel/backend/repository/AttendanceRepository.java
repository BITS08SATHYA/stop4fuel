package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    List<Attendance> findByEmployeeIdAndDateBetweenOrderByDateDesc(Long employeeId, LocalDate from, LocalDate to);
    List<Attendance> findByDateOrderByEmployeeNameAsc(LocalDate date);
    Optional<Attendance> findByEmployeeIdAndDate(Long employeeId, LocalDate date);
    List<Attendance> findByEmployeeIdAndDateBetween(Long employeeId, LocalDate from, LocalDate to);
}
