package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.AttendanceDTO;
import com.stopforfuel.backend.entity.Attendance;
import com.stopforfuel.backend.service.AttendanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
public class AttendanceController {

    @Autowired
    private AttendanceService attendanceService;

    @GetMapping("/attendance/daily")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_VIEW')")
    public List<AttendanceDTO> getDailyAttendance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return attendanceService.getDailyAttendance(date).stream().map(AttendanceDTO::from).toList();
    }

    @GetMapping("/employees/{employeeId}/attendance")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_VIEW')")
    public List<AttendanceDTO> getEmployeeAttendance(
            @PathVariable Long employeeId,
            @RequestParam Integer month,
            @RequestParam Integer year) {
        return attendanceService.getEmployeeAttendance(employeeId, month, year).stream().map(AttendanceDTO::from).toList();
    }

    @PostMapping("/employees/{employeeId}/attendance")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_CREATE')")
    public AttendanceDTO markAttendance(@PathVariable Long employeeId, @Valid @RequestBody Attendance attendance) {
        com.stopforfuel.backend.entity.Employee employee = new com.stopforfuel.backend.entity.Employee();
        employee.setId(employeeId);
        attendance.setEmployee(employee);
        return AttendanceDTO.from(attendanceService.markAttendance(attendance));
    }

    @PostMapping("/attendance/bulk")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_CREATE')")
    public List<AttendanceDTO> markBulkAttendance(@Valid @RequestBody List<Attendance> attendances) {
        return attendanceService.markBulkAttendance(attendances).stream().map(AttendanceDTO::from).toList();
    }

    @DeleteMapping("/attendance/{id}")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_DELETE')")
    public ResponseEntity<Void> deleteAttendance(@PathVariable Long id) {
        attendanceService.deleteAttendance(id);
        return ResponseEntity.ok().build();
    }
}
