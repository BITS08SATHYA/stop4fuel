package com.stopforfuel.backend.service;

import com.stopforfuel.backend.dto.ShiftClosingDataDTO;
import com.stopforfuel.backend.dto.ShiftClosingSubmitDTO;
import com.stopforfuel.backend.entity.*;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.exception.ResourceNotFoundException;
import com.stopforfuel.backend.repository.*;
import com.stopforfuel.config.SecurityUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ShiftService {

    private final ShiftRepository repository;
    private final ShiftClosingReportService shiftClosingReportService;
    private final ProductInventoryService productInventoryService;
    private final NozzleRepository nozzleRepository;
    private final TankRepository tankRepository;
    private final NozzleInventoryRepository nozzleInventoryRepository;
    private final TankInventoryRepository tankInventoryRepository;
    private final NozzleInventoryService nozzleInventoryService;
    private final TankInventoryService tankInventoryService;
    private final InvoiceBillRepository invoiceBillRepository;
    private final PaymentRepository paymentRepository;
    private final EAdvanceRepository eAdvanceRepository;
    private final OperationalAdvanceRepository operationalAdvanceRepository;
    private final ExpenseRepository expenseRepository;
    private final IncentivePaymentRepository incentivePaymentRepository;
    private final ExternalCashInflowRepository inflowRepository;
    private final CashInflowRepaymentRepository repaymentRepository;

    public ShiftService(ShiftRepository repository,
                        @Lazy ShiftClosingReportService shiftClosingReportService,
                        @Lazy ProductInventoryService productInventoryService,
                        NozzleRepository nozzleRepository,
                        TankRepository tankRepository,
                        NozzleInventoryRepository nozzleInventoryRepository,
                        TankInventoryRepository tankInventoryRepository,
                        @Lazy NozzleInventoryService nozzleInventoryService,
                        @Lazy TankInventoryService tankInventoryService,
                        InvoiceBillRepository invoiceBillRepository,
                        PaymentRepository paymentRepository,
                        EAdvanceRepository eAdvanceRepository,
                        OperationalAdvanceRepository operationalAdvanceRepository,
                        ExpenseRepository expenseRepository,
                        IncentivePaymentRepository incentivePaymentRepository,
                        ExternalCashInflowRepository inflowRepository,
                        CashInflowRepaymentRepository repaymentRepository) {
        this.repository = repository;
        this.shiftClosingReportService = shiftClosingReportService;
        this.productInventoryService = productInventoryService;
        this.nozzleRepository = nozzleRepository;
        this.tankRepository = tankRepository;
        this.nozzleInventoryRepository = nozzleInventoryRepository;
        this.tankInventoryRepository = tankInventoryRepository;
        this.nozzleInventoryService = nozzleInventoryService;
        this.tankInventoryService = tankInventoryService;
        this.invoiceBillRepository = invoiceBillRepository;
        this.paymentRepository = paymentRepository;
        this.eAdvanceRepository = eAdvanceRepository;
        this.operationalAdvanceRepository = operationalAdvanceRepository;
        this.expenseRepository = expenseRepository;
        this.incentivePaymentRepository = incentivePaymentRepository;
        this.inflowRepository = inflowRepository;
        this.repaymentRepository = repaymentRepository;
    }

    public List<Shift> getAllShifts() {
        return repository.findAllByScid(SecurityUtils.getScid());
    }

    public Shift openShift(Shift shift) {
        repository.findByStatusAndScid("OPEN", SecurityUtils.getScid()).ifPresent(s -> {
            throw new BusinessException("A shift is already open. Close it before opening a new one.");
        });

        shift.setStartTime(LocalDateTime.now());
        shift.setStatus("OPEN");
        if (shift.getScid() == null) {
            shift.setScid(SecurityUtils.getScid());
        }
        Shift saved = repository.save(shift);

        // Auto-create ProductInventory records for all active products
        productInventoryService.autoCreateForShift(saved);

        return saved;
    }

    public Shift closeShift(Long id) {
        Shift shift = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));

        shift.setEndTime(LocalDateTime.now());
        shift.setStatus("REVIEW");
        Shift closed = repository.save(shift);

        // Finalize rate/amount on ProductInventory records
        productInventoryService.finalizeForShift(closed.getId());

        // Auto-generate shift closing report
        shiftClosingReportService.generateReport(closed.getId());

        return closed;
    }

    public Shift getActiveShift() {
        return repository.findByStatusAndScid("OPEN", SecurityUtils.getScid()).orElse(null);
    }

    // ========== NEW SHIFT CLOSING WORKSPACE METHODS ==========

    public ShiftClosingDataDTO getShiftClosingData(Long shiftId) {
        Shift shift = repository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));

        if (!"OPEN".equals(shift.getStatus()) && !"REVIEW".equals(shift.getStatus())) {
            throw new BusinessException("Shift must be OPEN or in REVIEW to get closing data");
        }

        Long scid = shift.getScid();
        ShiftClosingDataDTO dto = new ShiftClosingDataDTO();
        dto.setShiftId(shiftId);
        dto.setShiftStatus(shift.getStatus());
        dto.setStartTime(shift.getStartTime());
        dto.setAttendantName(shift.getAttendant() != null ? shift.getAttendant().getName() : null);

        // Nozzle readings - pre-fill open from last close reading
        List<Nozzle> activeNozzles = nozzleRepository.findByActiveAndScid(true, scid);
        List<ShiftClosingDataDTO.NozzleReadingRow> nozzleRows = new ArrayList<>();
        for (Nozzle nozzle : activeNozzles) {
            ShiftClosingDataDTO.NozzleReadingRow row = new ShiftClosingDataDTO.NozzleReadingRow();
            row.setNozzleId(nozzle.getId());
            row.setNozzleName(nozzle.getNozzleName());
            row.setPumpName(nozzle.getPump() != null ? nozzle.getPump().getName() : null);
            row.setProductName(nozzle.getTank() != null && nozzle.getTank().getProduct() != null
                    ? nozzle.getTank().getProduct().getName() : null);
            row.setProductPrice(nozzle.getTank() != null && nozzle.getTank().getProduct() != null
                    && nozzle.getTank().getProduct().getPrice() != null
                    ? nozzle.getTank().getProduct().getPrice().doubleValue() : null);

            // Get last close reading as the open reading
            NozzleInventory lastReading = nozzleInventoryRepository.findTopByNozzleIdOrderByDateDescIdDesc(nozzle.getId());
            row.setOpenMeterReading(lastReading != null ? lastReading.getCloseMeterReading() : 0.0);
            nozzleRows.add(row);
        }
        dto.setNozzleReadings(nozzleRows);

        // Tank dips - pre-fill open from last close reading
        List<Tank> activeTanks = tankRepository.findByActiveAndScid(true, scid);
        List<ShiftClosingDataDTO.TankDipRow> tankRows = new ArrayList<>();
        for (Tank tank : activeTanks) {
            ShiftClosingDataDTO.TankDipRow row = new ShiftClosingDataDTO.TankDipRow();
            row.setTankId(tank.getId());
            row.setTankName(tank.getName());
            row.setProductName(tank.getProduct() != null ? tank.getProduct().getName() : null);
            row.setCapacity(tank.getCapacity());

            TankInventory lastReading = tankInventoryRepository.findTopByTankIdOrderByDateDescIdDesc(tank.getId());
            row.setOpenDip(lastReading != null ? lastReading.getCloseDip() : null);
            row.setOpenStock(lastReading != null && lastReading.getCloseStock() != null ? lastReading.getCloseStock() : 0.0);
            tankRows.add(row);
        }
        dto.setTankDips(tankRows);

        // Pre-compute shift totals
        computeShiftTotals(dto, shiftId);

        return dto;
    }

    @Transactional
    public Shift submitForReview(Long shiftId, ShiftClosingSubmitDTO submitDTO) {
        Shift shift = repository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));

        if (!"OPEN".equals(shift.getStatus())) {
            throw new BusinessException("Only an OPEN shift can be submitted for review");
        }

        Long scid = shift.getScid();
        LocalDate today = LocalDate.now();

        // Save nozzle inventory readings
        for (ShiftClosingSubmitDTO.NozzleReadingInput input : submitDTO.getNozzleReadings()) {
            NozzleInventory ni = new NozzleInventory();
            ni.setScid(scid);
            ni.setShiftId(shiftId);
            ni.setDate(today);
            ni.setNozzle(nozzleRepository.findById(input.getNozzleId())
                    .orElseThrow(() -> new RuntimeException("Nozzle not found: " + input.getNozzleId())));
            ni.setOpenMeterReading(input.getOpenMeterReading());
            ni.setCloseMeterReading(input.getCloseMeterReading());
            ni.setTestQuantity(input.getTestQuantity());
            nozzleInventoryService.save(ni);
        }

        // Save tank inventory readings and update tank available stock
        for (ShiftClosingSubmitDTO.TankDipInput input : submitDTO.getTankDips()) {
            Tank tank = tankRepository.findById(input.getTankId())
                    .orElseThrow(() -> new RuntimeException("Tank not found: " + input.getTankId()));

            TankInventory ti = new TankInventory();
            ti.setScid(scid);
            ti.setShiftId(shiftId);
            ti.setDate(today);
            ti.setTank(tank);
            ti.setOpenDip(input.getOpenDip());
            ti.setOpenStock(input.getOpenStock());
            ti.setIncomeStock(input.getIncomeStock());
            ti.setCloseDip(input.getCloseDip());
            ti.setCloseStock(input.getCloseStock());
            tankInventoryService.save(ti);

            // Update tank's available stock
            if (input.getCloseStock() != null) {
                tank.setAvailableStock(input.getCloseStock());
                tankRepository.save(tank);
            }
        }

        // Close the shift and move to REVIEW
        shift.setEndTime(LocalDateTime.now());
        shift.setStatus("REVIEW");
        Shift saved = repository.save(shift);

        // Finalize product inventory and generate report
        productInventoryService.finalizeForShift(shiftId);
        shiftClosingReportService.generateReport(shiftId);

        return saved;
    }

    @Transactional
    public Shift approveAndClose(Long shiftId) {
        Shift shift = repository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));

        if (!"REVIEW".equals(shift.getStatus())) {
            throw new BusinessException("Only a shift in REVIEW can be approved and closed");
        }

        shift.setStatus("CLOSED");
        Shift saved = repository.save(shift);

        // Finalize the report (generate PDF, store to S3)
        try {
            var report = shiftClosingReportService.getReport(shiftId);
            shiftClosingReportService.finalizeReport(report.getId(), "ADMIN");
        } catch (Exception e) {
            // Report finalization is best-effort; shift is still closed
        }

        return saved;
    }

    @Transactional
    public Shift reopenForReview(Long shiftId) {
        Shift shift = repository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));

        if (!"CLOSED".equals(shift.getStatus())) {
            throw new BusinessException("Only a CLOSED shift can be reopened for review");
        }

        shift.setStatus("REVIEW");
        Shift saved = repository.save(shift);

        // Set report back to DRAFT so it's editable
        try {
            var report = shiftClosingReportService.getReport(shiftId);
            report.setStatus("DRAFT");
            report.setFinalizedAt(null);
            report.setFinalizedBy(null);
        } catch (Exception ignored) {
            // Report may not exist yet
        }

        return saved;
    }

    private void computeShiftTotals(ShiftClosingDataDTO dto, Long shiftId) {
        // Credit bill total
        List<InvoiceBill> invoices = invoiceBillRepository.findByShiftId(shiftId);
        BigDecimal creditBillTotal = BigDecimal.ZERO;
        for (InvoiceBill inv : invoices) {
            if ("CREDIT".equals(inv.getBillType()) && inv.getNetAmount() != null) {
                creditBillTotal = creditBillTotal.add(inv.getNetAmount());
            }
        }
        dto.setCreditBillTotal(creditBillTotal);

        // Payment totals
        List<Payment> payments = paymentRepository.findByShiftId(shiftId);
        BigDecimal billPaymentTotal = BigDecimal.ZERO;
        BigDecimal statementPaymentTotal = BigDecimal.ZERO;
        for (Payment p : payments) {
            if (p.getInvoiceBill() != null) {
                billPaymentTotal = billPaymentTotal.add(p.getAmount());
            } else if (p.getStatement() != null) {
                statementPaymentTotal = statementPaymentTotal.add(p.getAmount());
            }
        }
        dto.setBillPaymentTotal(billPaymentTotal);
        dto.setStatementPaymentTotal(statementPaymentTotal);

        // E-Advance totals by type
        Map<String, BigDecimal> eAdvTotals = new LinkedHashMap<>();
        eAdvTotals.put("CARD", eAdvanceRepository.sumByShiftAndType(shiftId, "CARD"));
        eAdvTotals.put("UPI", eAdvanceRepository.sumByShiftAndType(shiftId, "UPI"));
        eAdvTotals.put("CCMS", eAdvanceRepository.sumByShiftAndType(shiftId, "CCMS"));
        eAdvTotals.put("CHEQUE", eAdvanceRepository.sumByShiftAndType(shiftId, "CHEQUE"));
        eAdvTotals.put("BANK_TRANSFER", eAdvanceRepository.sumByShiftAndType(shiftId, "BANK_TRANSFER"));
        dto.setEAdvanceTotals(eAdvTotals);

        // Operational advance totals by type
        List<OperationalAdvance> opAdvances = operationalAdvanceRepository.findByShiftIdOrderByAdvanceDateDesc(shiftId);
        Map<String, BigDecimal> opAdvTotals = new LinkedHashMap<>();
        for (OperationalAdvance oa : opAdvances) {
            if ("CANCELLED".equals(oa.getStatus())) continue;
            String type = oa.getAdvanceType() != null ? oa.getAdvanceType() : "CASH";
            opAdvTotals.merge(type, oa.getAmount(), BigDecimal::add);
        }
        dto.setOpAdvanceTotals(opAdvTotals);

        // Expense total
        dto.setExpenseTotal(expenseRepository.sumByShift(shiftId));

        // Incentive total
        dto.setIncentiveTotal(incentivePaymentRepository.sumByShift(shiftId));

        // External cash inflows
        List<ExternalCashInflow> inflows = inflowRepository.findByShiftIdOrderByInflowDateDesc(shiftId);
        BigDecimal inflowTotal = BigDecimal.ZERO;
        for (ExternalCashInflow inflow : inflows) {
            inflowTotal = inflowTotal.add(inflow.getAmount());
        }
        dto.setExternalInflowTotal(inflowTotal);

        // Inflow repayments
        List<CashInflowRepayment> repayments = repaymentRepository.findByShiftIdOrderByRepaymentDateDesc(shiftId);
        BigDecimal repaymentTotal = BigDecimal.ZERO;
        for (CashInflowRepayment r : repayments) {
            repaymentTotal = repaymentTotal.add(r.getAmount());
        }
        dto.setInflowRepaymentTotal(repaymentTotal);
    }
}
