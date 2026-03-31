package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Attendance;
import com.stopforfuel.backend.entity.Employee;
import com.stopforfuel.backend.repository.AttendanceRepository;
import com.stopforfuel.backend.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;

    private final EmployeeRepository employeeRepository;

    @Transactional(readOnly = true)
    public List<Attendance> getDailyAttendance(LocalDate date) {
        return attendanceRepository.findByDateOrderByEmployeeNameAsc(date);
    }

    @Transactional(readOnly = true)
    public List<Attendance> getEmployeeAttendance(Long employeeId, Integer month, Integer year) {
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        return attendanceRepository.findByEmployeeIdAndDateBetweenOrderByDateDesc(employeeId, from, to);
    }

    @Transactional
    public Attendance markAttendance(Attendance attendance) {
        // Check if already exists for this employee+date
        Attendance existing = attendanceRepository
                .findByEmployeeIdAndDate(attendance.getEmployee().getId(), attendance.getDate())
                .orElse(null);

        if (existing != null) {
            // Update existing
            existing.setCheckInTime(attendance.getCheckInTime());
            existing.setCheckOutTime(attendance.getCheckOutTime());
            existing.setStatus(attendance.getStatus());
            existing.setSource(attendance.getSource());
            existing.setRemarks(attendance.getRemarks());
            existing.setShiftRefId(attendance.getShiftRefId());
            if (existing.getCheckInTime() != null && existing.getCheckOutTime() != null) {
                long minutes = ChronoUnit.MINUTES.between(existing.getCheckInTime(), existing.getCheckOutTime());
                existing.setTotalHoursWorked(minutes / 60.0);
            }
            return attendanceRepository.save(existing);
        }

        // Calculate hours if both times present
        if (attendance.getCheckInTime() != null && attendance.getCheckOutTime() != null) {
            long minutes = ChronoUnit.MINUTES.between(attendance.getCheckInTime(), attendance.getCheckOutTime());
            attendance.setTotalHoursWorked(minutes / 60.0);
        }

        return attendanceRepository.save(attendance);
    }

    @Transactional
    public List<Attendance> markBulkAttendance(List<Attendance> attendances) {
        return attendances.stream().map(this::markAttendance).toList();
    }

    public void deleteAttendance(Long id) {
        attendanceRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public long countPresentDays(Long employeeId, Integer month, Integer year) {
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        return attendanceRepository.findByEmployeeIdAndDateBetween(employeeId, from, to)
                .stream()
                .filter(a -> "PRESENT".equals(a.getStatus()))
                .count();
    }

    @Transactional(readOnly = true)
    public long countAbsentDays(Long employeeId, Integer month, Integer year) {
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        return attendanceRepository.findByEmployeeIdAndDateBetween(employeeId, from, to)
                .stream()
                .filter(a -> "ABSENT".equals(a.getStatus()))
                .count();
    }
}
