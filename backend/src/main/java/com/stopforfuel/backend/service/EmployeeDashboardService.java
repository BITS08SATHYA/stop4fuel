package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.*;
import com.stopforfuel.backend.repository.AttendanceRepository;
import com.stopforfuel.backend.repository.EmployeeRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeeDashboardService {

    private final EmployeeRepository employeeRepository;
    private final AttendanceService attendanceService;
    private final LeaveService leaveService;
    private final SalaryPaymentService salaryPaymentService;
    private final OperationalAdvanceService operationalAdvanceService;
    private final AttendanceRepository attendanceRepository;

    @Transactional(readOnly = true)
    public EmployeeDashboardData getDashboard(Long employeeId) {
        Employee employee = employeeRepository.findByIdAndScid(employeeId, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        EmployeeDashboardData data = new EmployeeDashboardData();

        // Personal info
        PersonalInfo info = new PersonalInfo();
        info.setName(employee.getName());
        info.setDesignation(employee.getDesignation());
        info.setEmployeeCode(employee.getEmployeeCode());
        info.setJoinDate(employee.getJoinDate() != null ? employee.getJoinDate().toString() : null);
        info.setPhone(employee.getPhone());
        info.setEmail(employee.getEmail());
        info.setPhotoUrl(employee.getPhotoUrl());
        data.setPersonalInfo(info);

        // Attendance summary
        LocalDate now = LocalDate.now();
        int month = now.getMonthValue();
        int year = now.getYear();

        AttendanceSummary attendance = new AttendanceSummary();
        long presentDays = attendanceService.countPresentDays(employeeId, month, year);
        long absentDays = attendanceService.countAbsentDays(employeeId, month, year);
        attendance.setThisMonthPresent(presentDays);
        attendance.setThisMonthAbsent(absentDays);
        attendance.setTotalWorkingDays(now.getDayOfMonth());

        // Today's status
        Attendance todayAttendance = attendanceRepository
                .findByEmployeeIdAndDate(employeeId, now).orElse(null);
        if (todayAttendance != null) {
            attendance.setTodayStatus(todayAttendance.getStatus());
            attendance.setTodayCheckIn(todayAttendance.getCheckInTime() != null ? todayAttendance.getCheckInTime().toString() : null);
            attendance.setTodayCheckOut(todayAttendance.getCheckOutTime() != null ? todayAttendance.getCheckOutTime().toString() : null);
        } else {
            attendance.setTodayStatus("NOT_MARKED");
        }

        // Recent 7 days attendance
        List<Attendance> recentAttendance = attendanceService.getEmployeeAttendance(employeeId, month, year);
        attendance.setRecentAttendance(recentAttendance.stream().limit(7).map(a -> {
            AttendanceRecord rec = new AttendanceRecord();
            rec.setDate(a.getDate().toString());
            rec.setStatus(a.getStatus());
            rec.setCheckIn(a.getCheckInTime() != null ? a.getCheckInTime().toString() : null);
            rec.setCheckOut(a.getCheckOutTime() != null ? a.getCheckOutTime().toString() : null);
            rec.setHoursWorked(a.getTotalHoursWorked());
            return rec;
        }).toList());
        data.setAttendanceSummary(attendance);

        // Leave summary
        LeaveSummary leaveSummary = new LeaveSummary();
        leaveSummary.setMonthlyThreshold(employee.getMonthlyLeaveThreshold() != null ? employee.getMonthlyLeaveThreshold() : 4);

        List<LeaveBalance> balances = leaveService.getEmployeeLeaveBalances(employeeId, year);
        leaveSummary.setBalances(balances.stream().map(b -> {
            LeaveBalanceItem item = new LeaveBalanceItem();
            item.setLeaveType(b.getLeaveType() != null ? b.getLeaveType().getTypeName() : "Unknown");
            item.setTotalAllotted(b.getTotalAllotted());
            item.setUsed(b.getUsed());
            item.setRemaining(b.getRemaining());
            return item;
        }).toList());

        List<LeaveRequest> requests = leaveService.getEmployeeLeaveRequests(employeeId);
        leaveSummary.setPendingRequests(requests.stream()
                .filter(r -> r.getStatus() == com.stopforfuel.backend.enums.LeaveStatus.PENDING)
                .limit(5)
                .map(r -> {
                    LeaveRequestItem item = new LeaveRequestItem();
                    item.setId(r.getId());
                    item.setLeaveType(r.getLeaveType() != null ? r.getLeaveType().getTypeName() : "Unknown");
                    item.setFromDate(r.getFromDate().toString());
                    item.setToDate(r.getToDate().toString());
                    item.setDays(r.getNumberOfDays());
                    item.setStatus(r.getStatus().name());
                    item.setReason(r.getReason());
                    return item;
                }).toList());

        leaveSummary.setRecentRequests(requests.stream().limit(5).map(r -> {
            LeaveRequestItem item = new LeaveRequestItem();
            item.setId(r.getId());
            item.setLeaveType(r.getLeaveType() != null ? r.getLeaveType().getTypeName() : "Unknown");
            item.setFromDate(r.getFromDate().toString());
            item.setToDate(r.getToDate().toString());
            item.setDays(r.getNumberOfDays());
            item.setStatus(r.getStatus().name());
            item.setReason(r.getReason());
            return item;
        }).toList());
        data.setLeaveSummary(leaveSummary);

        // Salary summary
        SalarySummary salarySummary = new SalarySummary();
        salarySummary.setCurrentSalary(employee.getSalary() != null ? employee.getSalary() : 0.0);
        salarySummary.setSalaryDay(employee.getSalaryDay());

        List<SalaryPayment> salaryPayments = salaryPaymentService.getEmployeePayments(employeeId);
        salarySummary.setRecentPayments(salaryPayments.stream().limit(6).map(sp -> {
            SalaryPaymentItem item = new SalaryPaymentItem();
            item.setMonth(sp.getMonth());
            item.setYear(sp.getYear());
            item.setBaseSalary(sp.getBaseSalary());
            item.setAdvanceDeduction(sp.getAdvanceDeduction());
            item.setLopDays(sp.getLopDays());
            item.setLopDeduction(sp.getLopDeduction());
            item.setIncentiveAmount(sp.getIncentiveAmount());
            item.setOtherDeductions(sp.getOtherDeductions());
            item.setNetPayable(sp.getNetPayable());
            item.setStatus(sp.getStatus());
            item.setPaymentDate(sp.getPaymentDate() != null ? sp.getPaymentDate().toString() : null);
            item.setPaymentMode(sp.getPaymentMode() != null ? sp.getPaymentMode().name() : null);
            return item;
        }).toList());
        data.setSalarySummary(salarySummary);

        // Advance summary
        AdvanceSummary advanceSummary = new AdvanceSummary();
        List<OperationalAdvance> advances = operationalAdvanceService.getByEmployee(employeeId);

        BigDecimal totalOutstanding = advances.stream()
                .filter(a -> a.getStatus() == com.stopforfuel.backend.enums.AdvanceStatus.GIVEN
                        || a.getStatus() == com.stopforfuel.backend.enums.AdvanceStatus.PARTIALLY_RETURNED)
                .map(a -> {
                    BigDecimal amount = a.getAmount() != null ? a.getAmount() : BigDecimal.ZERO;
                    BigDecimal returned = a.getReturnedAmount() != null ? a.getReturnedAmount() : BigDecimal.ZERO;
                    BigDecimal utilized = a.getUtilizedAmount() != null ? a.getUtilizedAmount() : BigDecimal.ZERO;
                    return amount.subtract(returned).subtract(utilized);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        advanceSummary.setTotalOutstanding(totalOutstanding);
        advanceSummary.setRecentAdvances(advances.stream().limit(5).map(a -> {
            AdvanceItem item = new AdvanceItem();
            item.setId(a.getId());
            item.setDate(a.getAdvanceDate() != null ? a.getAdvanceDate().toString() : null);
            item.setAmount(a.getAmount());
            item.setReturnedAmount(a.getReturnedAmount());
            item.setUtilizedAmount(a.getUtilizedAmount());
            item.setStatus(a.getStatus() != null ? a.getStatus().name() : null);
            item.setPurpose(a.getPurpose());
            return item;
        }).toList());
        data.setAdvanceSummary(advanceSummary);

        return data;
    }

    // --- DTOs ---

    @Getter @Setter @NoArgsConstructor
    public static class EmployeeDashboardData {
        private PersonalInfo personalInfo;
        private AttendanceSummary attendanceSummary;
        private LeaveSummary leaveSummary;
        private SalarySummary salarySummary;
        private AdvanceSummary advanceSummary;
    }

    @Getter @Setter @NoArgsConstructor
    public static class PersonalInfo {
        private String name;
        private String designation;
        private String employeeCode;
        private String joinDate;
        private String phone;
        private String email;
        private String photoUrl;
    }

    @Getter @Setter @NoArgsConstructor
    public static class AttendanceSummary {
        private long thisMonthPresent;
        private long thisMonthAbsent;
        private int totalWorkingDays;
        private String todayStatus;
        private String todayCheckIn;
        private String todayCheckOut;
        private List<AttendanceRecord> recentAttendance;
    }

    @Getter @Setter @NoArgsConstructor
    public static class AttendanceRecord {
        private String date;
        private String status;
        private String checkIn;
        private String checkOut;
        private Double hoursWorked;
    }

    @Getter @Setter @NoArgsConstructor
    public static class LeaveSummary {
        private int monthlyThreshold;
        private List<LeaveBalanceItem> balances;
        private List<LeaveRequestItem> pendingRequests;
        private List<LeaveRequestItem> recentRequests;
    }

    @Getter @Setter @NoArgsConstructor
    public static class LeaveBalanceItem {
        private String leaveType;
        private Double totalAllotted;
        private Double used;
        private Double remaining;
    }

    @Getter @Setter @NoArgsConstructor
    public static class LeaveRequestItem {
        private Long id;
        private String leaveType;
        private String fromDate;
        private String toDate;
        private Double days;
        private String status;
        private String reason;
    }

    @Getter @Setter @NoArgsConstructor
    public static class SalarySummary {
        private double currentSalary;
        private Integer salaryDay;
        private List<SalaryPaymentItem> recentPayments;
    }

    @Getter @Setter @NoArgsConstructor
    public static class SalaryPaymentItem {
        private Integer month;
        private Integer year;
        private Double baseSalary;
        private Double advanceDeduction;
        private Integer lopDays;
        private Double lopDeduction;
        private Double incentiveAmount;
        private Double otherDeductions;
        private Double netPayable;
        private String status;
        private String paymentDate;
        private String paymentMode;
    }

    @Getter @Setter @NoArgsConstructor
    public static class AdvanceSummary {
        private BigDecimal totalOutstanding;
        private List<AdvanceItem> recentAdvances;
    }

    @Getter @Setter @NoArgsConstructor
    public static class AdvanceItem {
        private Long id;
        private String date;
        private BigDecimal amount;
        private BigDecimal returnedAmount;
        private BigDecimal utilizedAmount;
        private String status;
        private String purpose;
    }
}
