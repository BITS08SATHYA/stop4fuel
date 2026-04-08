package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Attendance;
import com.stopforfuel.backend.entity.Employee;
import com.stopforfuel.backend.repository.AttendanceRepository;
import com.stopforfuel.backend.repository.EmployeeRepository;
import com.stopforfuel.config.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private AttendanceService attendanceService;

    private Employee testEmployee;
    private Attendance testAttendance;

    private static final Long TEST_SCID = 1L;

    @BeforeEach
    void setUp() {
        testEmployee = new Employee();
        testEmployee.setId(1L);
        testEmployee.setName("Ravi Kumar");

        testAttendance = new Attendance();
        testAttendance.setId(1L);
        testAttendance.setEmployee(testEmployee);
        testAttendance.setDate(LocalDate.of(2026, 3, 19));
        testAttendance.setCheckInTime(LocalTime.of(9, 0));
        testAttendance.setCheckOutTime(LocalTime.of(17, 0));
        testAttendance.setStatus("PRESENT");
        testAttendance.setSource("MANUAL");
    }

    // --- getDailyAttendance ---

    @Test
    void getDailyAttendance_returnsList() {
        LocalDate date = LocalDate.of(2026, 3, 19);
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getScid).thenReturn(TEST_SCID);
            when(attendanceRepository.findByDateAndScidOrderByEmployeeNameAsc(date, TEST_SCID))
                    .thenReturn(List.of(testAttendance));

            List<Attendance> result = attendanceService.getDailyAttendance(date);

            assertEquals(1, result.size());
            verify(attendanceRepository).findByDateAndScidOrderByEmployeeNameAsc(date, TEST_SCID);
        }
    }

    // --- getEmployeeAttendance ---

    @Test
    void getEmployeeAttendance_queriesCorrectDateRange() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getScid).thenReturn(TEST_SCID);
            when(attendanceRepository.findByEmployeeIdAndDateBetweenAndScidOrderByDateDesc(1L, from, to, TEST_SCID))
                    .thenReturn(List.of(testAttendance));

            List<Attendance> result = attendanceService.getEmployeeAttendance(1L, 3, 2026);

            assertEquals(1, result.size());
            verify(attendanceRepository).findByEmployeeIdAndDateBetweenAndScidOrderByDateDesc(1L, from, to, TEST_SCID);
        }
    }

    // --- markAttendance ---

    @Test
    void markAttendance_new_savesNew() {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getScid).thenReturn(TEST_SCID);
            when(attendanceRepository.findByEmployeeIdAndDateAndScid(1L, testAttendance.getDate(), TEST_SCID))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(i -> i.getArgument(0));

            Attendance result = attendanceService.markAttendance(testAttendance);

            assertNotNull(result);
            verify(attendanceRepository).save(testAttendance);
        }
    }

    @Test
    void markAttendance_existing_updatesExisting() {
        Attendance existing = new Attendance();
        existing.setId(10L);
        existing.setEmployee(testEmployee);
        existing.setDate(testAttendance.getDate());
        existing.setStatus("ABSENT");

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getScid).thenReturn(TEST_SCID);
            when(attendanceRepository.findByEmployeeIdAndDateAndScid(1L, testAttendance.getDate(), TEST_SCID))
                    .thenReturn(Optional.of(existing));
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(i -> i.getArgument(0));

            Attendance result = attendanceService.markAttendance(testAttendance);

            assertEquals(10L, result.getId());
            assertEquals("PRESENT", result.getStatus());
            assertEquals(LocalTime.of(9, 0), result.getCheckInTime());
            assertEquals(LocalTime.of(17, 0), result.getCheckOutTime());
            verify(attendanceRepository).save(existing);
        }
    }

    @Test
    void markAttendance_calculatesHoursWorked() {
        Attendance attendance = new Attendance();
        attendance.setEmployee(testEmployee);
        attendance.setDate(LocalDate.of(2026, 3, 19));
        attendance.setCheckInTime(LocalTime.of(9, 0));
        attendance.setCheckOutTime(LocalTime.of(17, 0));
        attendance.setStatus("PRESENT");

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getScid).thenReturn(TEST_SCID);
            when(attendanceRepository.findByEmployeeIdAndDateAndScid(1L, attendance.getDate(), TEST_SCID))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(i -> i.getArgument(0));

            Attendance result = attendanceService.markAttendance(attendance);

            assertEquals(8.0, result.getTotalHoursWorked());
        }
    }

    // --- deleteAttendance ---

    @Test
    void deleteAttendance_callsDeleteById() {
        attendanceService.deleteAttendance(1L);

        verify(attendanceRepository).deleteById(1L);
    }

    // --- countPresentDays ---

    @Test
    void countPresentDays_countsCorrectly() {
        Attendance present1 = new Attendance();
        present1.setStatus("PRESENT");
        Attendance present2 = new Attendance();
        present2.setStatus("PRESENT");
        Attendance present3 = new Attendance();
        present3.setStatus("PRESENT");
        Attendance absent1 = new Attendance();
        absent1.setStatus("ABSENT");
        Attendance absent2 = new Attendance();
        absent2.setStatus("ABSENT");

        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getScid).thenReturn(TEST_SCID);
            when(attendanceRepository.findByEmployeeIdAndDateBetweenAndScid(1L, from, to, TEST_SCID))
                    .thenReturn(List.of(present1, present2, present3, absent1, absent2));

            long result = attendanceService.countPresentDays(1L, 3, 2026);

            assertEquals(3, result);
        }
    }

    // --- countAbsentDays ---

    @Test
    void countAbsentDays_countsCorrectly() {
        Attendance present1 = new Attendance();
        present1.setStatus("PRESENT");
        Attendance present2 = new Attendance();
        present2.setStatus("PRESENT");
        Attendance present3 = new Attendance();
        present3.setStatus("PRESENT");
        Attendance absent1 = new Attendance();
        absent1.setStatus("ABSENT");
        Attendance absent2 = new Attendance();
        absent2.setStatus("ABSENT");

        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getScid).thenReturn(TEST_SCID);
            when(attendanceRepository.findByEmployeeIdAndDateBetweenAndScid(1L, from, to, TEST_SCID))
                    .thenReturn(List.of(present1, present2, present3, absent1, absent2));

            long result = attendanceService.countAbsentDays(1L, 3, 2026);

            assertEquals(2, result);
        }
    }
}
