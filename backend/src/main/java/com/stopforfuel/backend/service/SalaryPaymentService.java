package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Employee;
import com.stopforfuel.backend.entity.OperationalAdvance;
import com.stopforfuel.backend.entity.LeaveRequest;
import com.stopforfuel.backend.entity.SalaryPayment;
import com.stopforfuel.backend.repository.OperationalAdvanceRepository;
import com.stopforfuel.backend.repository.EmployeeRepository;
import com.stopforfuel.backend.repository.LeaveRequestRepository;
import com.stopforfuel.backend.repository.SalaryPaymentRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SalaryPaymentService {

    private final SalaryPaymentRepository salaryPaymentRepository;

    private final EmployeeRepository employeeRepository;

    private final OperationalAdvanceRepository operationalAdvanceRepository;

    private final LeaveRequestRepository leaveRequestRepository;

    public List<SalaryPayment> getMonthlyPayments(Integer month, Integer year) {
        return salaryPaymentRepository.findByMonthAndYear(month, year);
    }

    public List<SalaryPayment> getEmployeePayments(Long employeeId) {
        return salaryPaymentRepository.findByEmployeeIdOrderByYearDescMonthDesc(employeeId);
    }

    @Transactional
    public List<SalaryPayment> processMonthlyPayroll(Integer month, Integer year) {
        List<Employee> employees = employeeRepository.findByStatus("ACTIVE");
        List<SalaryPayment> payments = new ArrayList<>();

        for (Employee emp : employees) {
            // Skip if already processed
            SalaryPayment existing = salaryPaymentRepository
                    .findByEmployeeIdAndMonthAndYear(emp.getId(), month, year)
                    .orElse(null);
            if (existing != null) {
                payments.add(existing);
                continue;
            }

            // Calculate advance deductions
            List<OperationalAdvance> pendingAdvances = operationalAdvanceRepository
                    .findByEmployeeIdAndStatus(emp.getId(), "PENDING");
            double advanceDeduction = pendingAdvances.stream()
                    .map(OperationalAdvance::getAmount)
                    .filter(a -> a != null)
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
                    .doubleValue();

            // Calculate LOP (Loss of Pay) based on monthly leave threshold
            int lopDays = 0;
            double lopDeduction = 0.0;
            int threshold = emp.getMonthlyLeaveThreshold() != null ? emp.getMonthlyLeaveThreshold() : 4;
            int totalLeaveDays = calculateLeaveDaysInMonth(emp.getId(), month, year);
            if (totalLeaveDays > threshold) {
                lopDays = totalLeaveDays - threshold;
                double baseSalary = emp.getSalary() != null ? emp.getSalary() : 0.0;
                int daysInMonth = YearMonth.of(year, month).lengthOfMonth();
                double perDaySalary = baseSalary / daysInMonth;
                lopDeduction = Math.round(perDaySalary * lopDays * 100.0) / 100.0;
            }

            SalaryPayment payment = new SalaryPayment();
            payment.setEmployee(emp);
            payment.setMonth(month);
            payment.setYear(year);
            payment.setBaseSalary(emp.getSalary() != null ? emp.getSalary() : 0.0);
            payment.setAdvanceDeduction(advanceDeduction);
            payment.setLopDays(lopDays);
            payment.setLopDeduction(lopDeduction);
            payment.setIncentiveAmount(0.0);
            payment.setOtherDeductions(0.0);
            payment.setNetPayable(payment.getBaseSalary() - advanceDeduction - lopDeduction
                    + payment.getIncentiveAmount() - payment.getOtherDeductions());
            payment.setStatus("DRAFT");

            payments.add(salaryPaymentRepository.save(payment));
        }

        return payments;
    }

    @Transactional
    public SalaryPayment markAsPaid(Long paymentId, String paymentMode) {
        SalaryPayment payment = salaryPaymentRepository.findByIdAndScidForUpdate(paymentId, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        payment.setStatus("PAID");
        payment.setPaymentDate(LocalDate.now());
        payment.setPaymentMode(paymentMode);

        // Mark pending advances as DEDUCTED
        List<OperationalAdvance> pendingAdvances = operationalAdvanceRepository
                .findByEmployeeIdAndStatus(payment.getEmployee().getId(), "PENDING");
        for (OperationalAdvance advance : pendingAdvances) {
            advance.setStatus("DEDUCTED");
            operationalAdvanceRepository.save(advance);
        }

        return salaryPaymentRepository.save(payment);
    }

    @Transactional
    public SalaryPayment updatePayment(Long id, SalaryPayment details) {
        SalaryPayment payment = salaryPaymentRepository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        payment.setBaseSalary(details.getBaseSalary());
        payment.setAdvanceDeduction(details.getAdvanceDeduction());
        payment.setLopDays(details.getLopDays() != null ? details.getLopDays() : 0);
        payment.setLopDeduction(details.getLopDeduction() != null ? details.getLopDeduction() : 0.0);
        payment.setIncentiveAmount(details.getIncentiveAmount());
        payment.setOtherDeductions(details.getOtherDeductions());
        payment.setNetPayable(details.getBaseSalary() - details.getAdvanceDeduction()
                - (details.getLopDeduction() != null ? details.getLopDeduction() : 0.0)
                + details.getIncentiveAmount() - details.getOtherDeductions());
        payment.setRemarks(details.getRemarks());

        return salaryPaymentRepository.save(payment);
    }

    /**
     * Calculate total approved leave days for an employee in a given month.
     * Handles leaves that span across months by counting only the days within the target month.
     */
    private int calculateLeaveDaysInMonth(Long employeeId, int month, int year) {
        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd = YearMonth.of(year, month).atEndOfMonth();

        List<LeaveRequest> approvedLeaves = leaveRequestRepository
                .findApprovedLeavesOverlappingMonth(employeeId, monthStart, monthEnd);

        int totalDays = 0;
        for (LeaveRequest leave : approvedLeaves) {
            LocalDate effectiveStart = leave.getFromDate().isBefore(monthStart) ? monthStart : leave.getFromDate();
            LocalDate effectiveEnd = leave.getToDate().isAfter(monthEnd) ? monthEnd : leave.getToDate();
            totalDays += (int) (effectiveEnd.toEpochDay() - effectiveStart.toEpochDay()) + 1;
        }
        return totalDays;
    }
}
