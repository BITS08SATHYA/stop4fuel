package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Attendance;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends ScidRepository<Attendance> {
    List<Attendance> findByEmployeeIdAndDateBetweenAndScidOrderByDateDesc(Long employeeId, LocalDate from, LocalDate to, Long scid);
    List<Attendance> findByDateAndScidOrderByEmployeeNameAsc(LocalDate date, Long scid);
    Optional<Attendance> findByEmployeeIdAndDateAndScid(Long employeeId, LocalDate date, Long scid);
    List<Attendance> findByEmployeeIdAndDateBetweenAndScid(Long employeeId, LocalDate from, LocalDate to, Long scid);
    long countByDateAndScid(LocalDate date, Long scid);
}
