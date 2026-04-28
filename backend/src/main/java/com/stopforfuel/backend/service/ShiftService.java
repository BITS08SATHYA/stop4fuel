package com.stopforfuel.backend.service;

import com.stopforfuel.backend.dto.ShiftClosingDataDTO;
import com.stopforfuel.backend.dto.ShiftClosingSubmitDTO;
import com.stopforfuel.backend.dto.ShiftReportPrintData;
import com.stopforfuel.backend.entity.*;
import com.stopforfuel.backend.enums.AdvanceStatus;
import com.stopforfuel.backend.enums.ShiftStatus;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.exception.ResourceNotFoundException;
import com.stopforfuel.backend.repository.*;
import com.stopforfuel.config.SecurityUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
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
    private final ShiftReportPdfGenerator pdfGenerator;
    private final S3StorageService s3StorageService;
    private final ShiftClosingReportRepository shiftClosingReportRepository;
    private final StatementAutoGenerationService statementAutoGenerationService;
    private final ShiftCashInvoiceAutoService shiftCashInvoiceAutoService;
    private final com.stopforfuel.backend.repository.UserRepository userRepository;
    private final com.stopforfuel.config.BusinessMetrics metrics;

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
                        CashInflowRepaymentRepository repaymentRepository,
                        ShiftReportPdfGenerator pdfGenerator,
                        S3StorageService s3StorageService,
                        ShiftClosingReportRepository shiftClosingReportRepository,
                        @Lazy StatementAutoGenerationService statementAutoGenerationService,
                        @Lazy ShiftCashInvoiceAutoService shiftCashInvoiceAutoService,
                        com.stopforfuel.backend.repository.UserRepository userRepository,
                        com.stopforfuel.config.BusinessMetrics metrics) {
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
        this.pdfGenerator = pdfGenerator;
        this.s3StorageService = s3StorageService;
        this.shiftClosingReportRepository = shiftClosingReportRepository;
        this.statementAutoGenerationService = statementAutoGenerationService;
        this.shiftCashInvoiceAutoService = shiftCashInvoiceAutoService;
        this.userRepository = userRepository;
        this.metrics = metrics;
    }

    @Transactional(readOnly = true)
    public List<Shift> getAllShifts() {
        return repository.findByScidOrderByIdDesc(SecurityUtils.getScid());
    }

    @Transactional
    public Shift openShift(Shift shift) {
        repository.findTopByStatusAndScidOrderByIdDesc(ShiftStatus.OPEN, SecurityUtils.getScid()).ifPresent(s -> {
            throw new BusinessException("A shift is already open. Close it before opening a new one.");
        });

        shift.setStartTime(LocalDateTime.now());
        shift.setStatus(ShiftStatus.OPEN);
        if (shift.getScid() == null) {
            shift.setScid(SecurityUtils.getScid());
        }

        // Auto-assign current user as attendant if not explicitly set
        if (shift.getAttendant() == null || shift.getAttendant().getId() == null) {
            Long currentUserId = SecurityUtils.getCurrentUserId();
            if (currentUserId != null) {
                userRepository.findById(currentUserId).ifPresent(shift::setAttendant);
            }
        }

        Shift saved = repository.save(shift);

        // Auto-create ProductInventory records for all active products
        productInventoryService.autoCreateForShift(saved);

        metrics.shiftOpened();
        return saved;
    }

    @Transactional
    public Shift changeAttendant(Long shiftId, Long attendantId) {
        Shift shift = repository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));
        User attendant = userRepository.findById(attendantId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        shift.setAttendant(attendant);
        return repository.save(shift);
    }

    public Shift closeShift(Long id) {
        Shift shift = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));

        shift.setEndTime(LocalDateTime.now());
        shift.setStatus(ShiftStatus.REVIEW);
        Shift closed = repository.save(shift);

        // Finalize rate/amount on ProductInventory records
        productInventoryService.finalizeForShift(closed.getId());

        // Auto-generate shift closing report
        shiftClosingReportService.generateReport(closed.getId());

        return closed;
    }

    @Transactional(readOnly = true)
    public Shift getActiveShift() {
        return repository.findTopByStatusAndScidOrderByIdDesc(ShiftStatus.OPEN, SecurityUtils.getScid()).orElse(null);
    }

    // ========== NEW SHIFT CLOSING WORKSPACE METHODS ==========

    @Transactional(readOnly = true)
    public ShiftClosingDataDTO getShiftClosingData(Long shiftId) {
        Shift shift = repository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));

        if (shift.getStatus() != ShiftStatus.OPEN && shift.getStatus() != ShiftStatus.REVIEW) {
            throw new BusinessException("Shift must be OPEN or in REVIEW to get closing data");
        }

        Long scid = shift.getScid();
        ShiftClosingDataDTO dto = new ShiftClosingDataDTO();
        dto.setShiftId(shiftId);
        dto.setShiftStatus(shift.getStatus() != null ? shift.getStatus().name() : null);
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

            // Check for existing inventory record for THIS shift first
            NozzleInventory currentShiftReading = nozzleInventoryRepository.findByShiftIdAndNozzleId(shiftId, nozzle.getId()).stream().findFirst().orElse(null);
            if (currentShiftReading != null) {
                row.setOpenMeterReading(currentShiftReading.getOpenMeterReading());
                row.setCloseMeterReading(currentShiftReading.getCloseMeterReading());
                row.setTestQuantity(currentShiftReading.getTestQuantity());
            } else {
                // Fallback: get last close reading as the open reading
                NozzleInventory lastReading = nozzleInventoryRepository.findTopByNozzleIdAndScidOrderByDateDescIdDesc(nozzle.getId(), com.stopforfuel.config.SecurityUtils.getScid());
                if (lastReading != null && lastReading.getCloseMeterReading() != null) {
                    row.setOpenMeterReading(lastReading.getCloseMeterReading());
                } else if (lastReading != null && lastReading.getOpenMeterReading() != null) {
                    row.setOpenMeterReading(lastReading.getOpenMeterReading());
                } else {
                    row.setOpenMeterReading(0.0);
                }
            }
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

            // Check for existing inventory record for THIS shift first
            TankInventory currentShiftReading = tankInventoryRepository.findByShiftIdAndTankId(shiftId, tank.getId()).stream().findFirst().orElse(null);
            if (currentShiftReading != null) {
                row.setOpenDip(currentShiftReading.getOpenDip());
                row.setOpenStock(currentShiftReading.getOpenStock());
                row.setIncomeStock(currentShiftReading.getIncomeStock());
                row.setCloseDip(currentShiftReading.getCloseDip());
                row.setCloseStock(currentShiftReading.getCloseStock());
            } else {
                // Fallback: get last close reading as the open reading
                TankInventory lastReading = tankInventoryRepository.findTopByTankIdAndScidOrderByDateDescIdDesc(tank.getId(), com.stopforfuel.config.SecurityUtils.getScid());
                if (lastReading != null && lastReading.getCloseDip() != null) {
                    row.setOpenDip(lastReading.getCloseDip());
                } else if (lastReading != null && lastReading.getOpenDip() != null) {
                    row.setOpenDip(lastReading.getOpenDip());
                }
                if (lastReading != null && lastReading.getCloseStock() != null) {
                    row.setOpenStock(lastReading.getCloseStock());
                } else if (lastReading != null && lastReading.getOpenStock() != null) {
                    row.setOpenStock(lastReading.getOpenStock());
                } else {
                    row.setOpenStock(0.0);
                }
            }
            tankRows.add(row);
        }
        dto.setTankDips(tankRows);

        // Pre-compute shift totals
        computeShiftTotals(dto, shiftId);

        // Credit bill photo upload check
        dto.setCreditBillsMissingPhoto(invoiceBillRepository.countCreditBillsWithoutPhoto(shiftId));

        return dto;
    }

    @Transactional
    public Shift submitForReview(Long shiftId, ShiftClosingSubmitDTO submitDTO) {
        Shift shift = repository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));

        if (shift.getStatus() != ShiftStatus.OPEN && shift.getStatus() != ShiftStatus.REVIEW) {
            throw new BusinessException("Only an OPEN or REVIEW shift can be submitted for review");
        }

        Long scid = shift.getScid();
        LocalDate today = LocalDate.now();

        // Save nozzle inventory readings (upsert: update existing or create new)
        for (ShiftClosingSubmitDTO.NozzleReadingInput input : submitDTO.getNozzleReadings()) {
            NozzleInventory ni = nozzleInventoryRepository.findByShiftIdAndNozzleId(shiftId, input.getNozzleId()).stream().findFirst().orElse(null);
            if (ni == null) {
                ni = new NozzleInventory();
                ni.setScid(scid);
                ni.setShiftId(shiftId);
                ni.setDate(today);
                ni.setNozzle(nozzleRepository.findById(input.getNozzleId())
                        .orElseThrow(() -> new RuntimeException("Nozzle not found: " + input.getNozzleId())));
            }
            ni.setOpenMeterReading(input.getOpenMeterReading());
            ni.setCloseMeterReading(input.getCloseMeterReading());
            ni.setTestQuantity(input.getTestQuantity());
            nozzleInventoryService.save(ni);
        }

        // Save tank inventory readings and update tank available stock (upsert)
        for (ShiftClosingSubmitDTO.TankDipInput input : submitDTO.getTankDips()) {
            Tank tank = tankRepository.findById(input.getTankId())
                    .orElseThrow(() -> new RuntimeException("Tank not found: " + input.getTankId()));

            TankInventory ti = tankInventoryRepository.findByShiftIdAndTankId(shiftId, input.getTankId()).stream().findFirst().orElse(null);
            if (ti == null) {
                ti = new TankInventory();
                ti.setScid(scid);
                ti.setShiftId(shiftId);
                ti.setDate(today);
                ti.setTank(tank);
            }
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
        shift.setStatus(ShiftStatus.REVIEW);
        Shift saved = repository.save(shift);

        // Finalize product inventory and generate report
        productInventoryService.finalizeForShift(shiftId);
        shiftClosingReportService.generateReport(shiftId);

        return saved;
    }

    @Transactional
    public Shift approveAndClose(Long shiftId) {
        return metrics.shiftCloseDuration.record(() -> doApproveAndClose(shiftId));
    }

    private Shift doApproveAndClose(Long shiftId) {
        Shift shift = repository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));

        if (shift.getStatus() != ShiftStatus.REVIEW) {
            throw new BusinessException("Only a shift in REVIEW can be approved and closed");
        }

        shift.setStatus(ShiftStatus.CLOSED);
        Shift saved = repository.save(shift);

        // Finalize report and generate PDF → upload to S3
        try {
            var report = shiftClosingReportService.getReport(shiftId);
            report.setStatus("FINALIZED");
            report.setFinalizedBy("ADMIN");
            report.setFinalizedAt(LocalDateTime.now());

            // Generate PDF
            ShiftReportPrintData printData = shiftClosingReportService.getPrintData(shiftId);
            byte[] pdfBytes = pdfGenerator.generate(printData, report);

            // Upload to S3
            LocalDateTime shiftStart = shift.getStartTime() != null ? shift.getStartTime() : LocalDateTime.now();
            Long scid = shift.getScid() != null ? shift.getScid() : 1L;
            String key = String.format("reports/shift-closing/%d/%d/%02d/%02d/shift-%d.pdf",
                    scid, shiftStart.getYear(), shiftStart.getMonthValue(),
                    shiftStart.getDayOfMonth(), shiftId);

            s3StorageService.upload(key, pdfBytes, "application/pdf");
            report.setReportPdfUrl(key);
            shiftClosingReportRepository.save(report);
        } catch (Exception e) {
            // PDF generation/upload is best-effort; shift is still closed
            System.err.println("Failed to generate/upload shift report PDF: " + e.getMessage());
        }

        // Auto-generate DRAFT statements if shift crosses a statement boundary
        try {
            statementAutoGenerationService.onShiftClosed(saved);
        } catch (Exception e) {
            System.err.println("Failed to auto-generate statement drafts: " + e.getMessage());
        }

        // Auto-generate synthetic cash invoices for residual fuel (meter − invoiced).
        // Best-effort: must not block the cashier's close flow.
        try {
            shiftCashInvoiceAutoService.generateForShift(shiftId);
        } catch (Exception e) {
            log.error("Failed to auto-generate synthetic cash invoices for shift {}", shiftId, e);
        }

        metrics.shiftClosed();
        return saved;
    }

    @Transactional
    public Shift reopenForReview(Long shiftId) {
        Shift shift = repository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));

        if (shift.getStatus() != ShiftStatus.CLOSED) {
            throw new BusinessException("Only a CLOSED shift can be reopened for review");
        }

        shift.setStatus(ShiftStatus.REVIEW);
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

        // Wipe synthetic cash invoices — they'll be regenerated on next approveAndClose
        try {
            int wiped = invoiceBillRepository.deleteByShiftIdAndAutoGeneratedTrue(shiftId);
            if (wiped > 0) log.info("Reopen shift {}: wiped {} synthetic cash invoices", shiftId, wiped);
        } catch (Exception e) {
            log.error("Failed to wipe synthetic cash invoices for reopened shift {}", shiftId, e);
        }

        return saved;
    }

    @Transactional
    public Shift reopenToEdit(Long shiftId) {
        Shift shift = repository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));

        if (shift.getStatus() != ShiftStatus.REVIEW) {
            throw new BusinessException("Only a REVIEW shift can be reopened for editing");
        }

        shift.setStatus(ShiftStatus.OPEN);
        shift.setEndTime(null);
        return repository.save(shift);
    }

    private void computeShiftTotals(ShiftClosingDataDTO dto, Long shiftId) {
        // Credit bill total
        dto.setCreditBillTotal(invoiceBillRepository.sumCreditBillsByShift(shiftId));

        // Payment totals
        dto.setBillPaymentTotal(paymentRepository.sumBillPaymentsByShift(shiftId));
        dto.setStatementPaymentTotal(paymentRepository.sumStatementPaymentsByShift(shiftId));

        // E-Advance totals by type
        Map<String, BigDecimal> eAdvTotals = new LinkedHashMap<>();
        eAdvTotals.put("CARD", eAdvanceRepository.sumByShiftAndType(shiftId, com.stopforfuel.backend.enums.PaymentMode.CARD));
        eAdvTotals.put("UPI", eAdvanceRepository.sumByShiftAndType(shiftId, com.stopforfuel.backend.enums.PaymentMode.UPI));
        eAdvTotals.put("CCMS", eAdvanceRepository.sumByShiftAndType(shiftId, com.stopforfuel.backend.enums.PaymentMode.CCMS));
        eAdvTotals.put("CHEQUE", eAdvanceRepository.sumByShiftAndType(shiftId, com.stopforfuel.backend.enums.PaymentMode.CHEQUE));
        eAdvTotals.put("BANK_TRANSFER", eAdvanceRepository.sumByShiftAndType(shiftId, com.stopforfuel.backend.enums.PaymentMode.BANK_TRANSFER));
        dto.setEAdvanceTotals(eAdvTotals);

        // Operational advance totals by type
        List<OperationalAdvance> opAdvances = operationalAdvanceRepository.findByShiftIdOrderByIdDesc(shiftId);
        Map<String, BigDecimal> opAdvTotals = new LinkedHashMap<>();
        for (OperationalAdvance oa : opAdvances) {
            if (oa.getStatus() == AdvanceStatus.CANCELLED) continue;
            String type = oa.getAdvanceType() != null ? oa.getAdvanceType().name() : "CASH";
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
