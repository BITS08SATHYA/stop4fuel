package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.entity.Attendance;
import com.stopforfuel.backend.service.AttendanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
public class AttendanceController {

    @Autowired
    private AttendanceService attendanceService;

    @GetMapping("/attendance/daily")
    public List<Attendance> getDailyAttendance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return attendanceService.getDailyAttendance(date);
    }

    @GetMapping("/employees/{employeeId}/attendance")
    public List<Attendance> getEmployeeAttendance(
            @PathVariable Long employeeId,
            @RequestParam Integer month,
            @RequestParam Integer year) {
        return attendanceService.getEmployeeAttendance(employeeId, month, year);
    }

    @PostMapping("/employees/{employeeId}/attendance")
    public Attendance markAttendance(@PathVariable Long employeeId, @Valid @RequestBody Attendance attendance) {
        com.stopforfuel.backend.entity.Employee employee = new com.stopforfuel.backend.entity.Employee();
        employee.setId(employeeId);
        attendance.setEmployee(employee);
        return attendanceService.markAttendance(attendance);
    }

    @PostMapping("/attendance/bulk")
    public List<Attendance> markBulkAttendance(@RequestBody List<Attendance> attendances) {
        return attendanceService.markBulkAttendance(attendances);
    }

    @DeleteMapping("/attendance/{id}")
    public ResponseEntity<Void> deleteAttendance(@PathVariable Long id) {
        attendanceService.deleteAttendance(id);
        return ResponseEntity.ok().build();
    }
}
