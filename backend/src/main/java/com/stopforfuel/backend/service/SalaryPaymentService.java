package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Employee;
import com.stopforfuel.backend.entity.EmployeeAdvance;
import com.stopforfuel.backend.entity.SalaryPayment;
import com.stopforfuel.backend.repository.EmployeeAdvanceRepository;
import com.stopforfuel.backend.repository.EmployeeRepository;
import com.stopforfuel.backend.repository.SalaryPaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class SalaryPaymentService {

    @Autowired
    private SalaryPaymentRepository salaryPaymentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmployeeAdvanceRepository employeeAdvanceRepository;

    public List<SalaryPayment> getMonthlyPayments(Integer month, Integer year) {
        return salaryPaymentRepository.findByMonthAndYear(month, year);
    }

    public List<SalaryPayment> getEmployeePayments(Long employeeId) {
        return salaryPaymentRepository.findByEmployeeIdOrderByYearDescMonthDesc(employeeId);
    }

    @Transactional
    public List<SalaryPayment> processMonthlyPayroll(Integer month, Integer year) {
        List<Employee> employees = employeeRepository.findByStatus("Active");
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
            List<EmployeeAdvance> pendingAdvances = employeeAdvanceRepository
                    .findByEmployeeIdAndStatus(emp.getId(), "PENDING");
            double advanceDeduction = pendingAdvances.stream()
                    .mapToDouble(EmployeeAdvance::getAmount)
                    .sum();

            SalaryPayment payment = new SalaryPayment();
            payment.setEmployee(emp);
            payment.setMonth(month);
            payment.setYear(year);
            payment.setBaseSalary(emp.getSalary() != null ? emp.getSalary() : 0.0);
            payment.setAdvanceDeduction(advanceDeduction);
            payment.setIncentiveAmount(0.0);
            payment.setOtherDeductions(0.0);
            payment.setNetPayable(payment.getBaseSalary() - advanceDeduction + payment.getIncentiveAmount() - payment.getOtherDeductions());
            payment.setStatus("DRAFT");

            payments.add(salaryPaymentRepository.save(payment));
        }

        return payments;
    }

    @Transactional
    public SalaryPayment markAsPaid(Long paymentId, String paymentMode) {
        SalaryPayment payment = salaryPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        payment.setStatus("PAID");
        payment.setPaymentDate(LocalDate.now());
        payment.setPaymentMode(paymentMode);

        // Mark pending advances as DEDUCTED
        List<EmployeeAdvance> pendingAdvances = employeeAdvanceRepository
                .findByEmployeeIdAndStatus(payment.getEmployee().getId(), "PENDING");
        for (EmployeeAdvance advance : pendingAdvances) {
            advance.setStatus("DEDUCTED");
            employeeAdvanceRepository.save(advance);
        }

        return salaryPaymentRepository.save(payment);
    }

    @Transactional
    public SalaryPayment updatePayment(Long id, SalaryPayment details) {
        SalaryPayment payment = salaryPaymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        payment.setBaseSalary(details.getBaseSalary());
        payment.setAdvanceDeduction(details.getAdvanceDeduction());
        payment.setIncentiveAmount(details.getIncentiveAmount());
        payment.setOtherDeductions(details.getOtherDeductions());
        payment.setNetPayable(details.getBaseSalary() - details.getAdvanceDeduction()
                + details.getIncentiveAmount() - details.getOtherDeductions());
        payment.setRemarks(details.getRemarks());

        return salaryPaymentRepository.save(payment);
    }
}
