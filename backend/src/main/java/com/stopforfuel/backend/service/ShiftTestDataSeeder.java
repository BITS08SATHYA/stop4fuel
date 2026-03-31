package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.*;
import com.stopforfuel.backend.enums.*;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.repository.*;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed-test-data.enabled", havingValue = "true")
public class ShiftTestDataSeeder {

    private final ShiftRepository shiftRepository;
    private final NozzleRepository nozzleRepository;
    private final TankRepository tankRepository;
    private final NozzleInventoryRepository nozzleInventoryRepository;
    private final TankInventoryRepository tankInventoryRepository;
    private final EAdvanceRepository eAdvanceRepository;
    private final OperationalAdvanceRepository operationalAdvanceRepository;
    private final InvoiceBillRepository invoiceBillRepository;
    private final EmployeeRepository employeeRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final UpiCompanyRepository upiCompanyRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentModeRepository paymentModeRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseTypeRepository expenseTypeRepository;
    private final IncentivePaymentRepository incentivePaymentRepository;
    private final VehicleRepository vehicleRepository;
    private final ProductInventoryRepository productInventoryRepository;

    @Transactional
    public Map<String, Object> seedTestData(Long shiftId) {
        Long scid = SecurityUtils.getScid();

        Shift shift = shiftRepository.findByIdAndScid(shiftId, scid)
                .orElseThrow(() -> new BusinessException("Shift not found"));

        if (!"OPEN".equals(shift.getStatus())) {
            throw new BusinessException("Can only seed test data for OPEN shifts");
        }

        // Idempotency check
        List<EAdvance> existing = eAdvanceRepository.findByShiftIdOrderByTransactionDateDesc(shiftId);
        if (!existing.isEmpty()) {
            throw new BusinessException("Test data already seeded for this shift");
        }

        LocalDateTime shiftStart = shift.getStartTime();
        Map<String, Object> summary = new LinkedHashMap<>();

        // 1. Nozzle Inventory (opening meter readings)
        int nozzleCount = seedNozzleInventory(shiftId, scid);
        summary.put("nozzleInventories", nozzleCount);

        // 2. Tank Inventory (opening stock)
        int tankCount = seedTankInventory(shiftId, scid);
        summary.put("tankInventories", tankCount);

        // 3. EAdvances (Card, UPI, CCMS, Cheque, Bank)
        int eAdvCount = seedEAdvances(shiftId, scid, shiftStart);
        summary.put("eAdvances", eAdvCount);

        // 4. Operational Advances (Cash, Salary)
        int opAdvCount = seedOperationalAdvances(shiftId, scid, shiftStart);
        summary.put("operationalAdvances", opAdvCount);

        // 5. Invoice Bills (fuel: cash + credit, oil/accessories: cash + credit)
        Map<String, Integer> billCounts = seedInvoiceBills(shiftId, scid, shiftStart);
        summary.put("invoiceBills", billCounts);

        // 6. Payments (credit bill payments from customers)
        int paymentCount = seedPayments(shiftId, scid, shiftStart);
        summary.put("payments", paymentCount);

        // 7. Expenses (station expenses)
        int expenseCount = seedExpenses(shiftId, scid, shiftStart);
        summary.put("expenses", expenseCount);

        // 8. Incentive Payments
        int incentiveCount = seedIncentives(shiftId, scid, shiftStart);
        summary.put("incentives", incentiveCount);

        // 9. Product Inventory for non-fuel products (lubricants, accessories)
        int productInvCount = seedProductInventory(shiftId, scid);
        summary.put("productInventories", productInvCount);

        summary.put("message", "Test data seeded successfully");
        return summary;
    }

    // ========== 1. Nozzle Inventory ==========

    private int seedNozzleInventory(Long shiftId, Long scid) {
        List<Nozzle> nozzles = nozzleRepository.findByActiveAndScid(true, scid);
        int count = 0;

        for (Nozzle nozzle : nozzles) {
            NozzleInventory lastReading = nozzleInventoryRepository
                    .findTopByNozzleIdOrderByDateDescIdDesc(nozzle.getId());

            double openReading;
            if (lastReading != null && lastReading.getCloseMeterReading() != null) {
                openReading = lastReading.getCloseMeterReading();
            } else if (lastReading != null && lastReading.getOpenMeterReading() != null) {
                openReading = lastReading.getOpenMeterReading();
            } else {
                String productName = nozzle.getTank().getProduct().getName().toLowerCase();
                if (productName.contains("diesel")) {
                    openReading = 75000.0;
                } else if (productName.contains("premium") || productName.contains("xtra")) {
                    openReading = 30000.0;
                } else {
                    openReading = 50000.0;
                }
            }

            NozzleInventory ni = new NozzleInventory();
            ni.setScid(scid);
            ni.setShiftId(shiftId);
            ni.setDate(LocalDate.now());
            ni.setNozzle(nozzle);
            ni.setOpenMeterReading(openReading);
            ni.setRate(nozzle.getTank().getProduct().getPrice().doubleValue());
            nozzleInventoryRepository.save(ni);
            count++;
        }
        return count;
    }

    // ========== 2. Tank Inventory ==========

    private int seedTankInventory(Long shiftId, Long scid) {
        List<Tank> tanks = tankRepository.findByActiveAndScid(true, scid);
        int count = 0;

        for (Tank tank : tanks) {
            TankInventory lastReading = tankInventoryRepository
                    .findTopByTankIdOrderByDateDescIdDesc(tank.getId());

            double openStock;
            if (lastReading != null && lastReading.getCloseStock() != null) {
                openStock = lastReading.getCloseStock();
            } else {
                openStock = tank.getAvailableStock() > 0 ? tank.getAvailableStock() : tank.getCapacity() * 0.7;
            }

            TankInventory ti = new TankInventory();
            ti.setScid(scid);
            ti.setShiftId(shiftId);
            ti.setDate(LocalDate.now());
            ti.setTank(tank);
            ti.setOpenStock(openStock);
            ti.setIncomeStock(0.0);
            ti.setTotalStock(openStock);
            tankInventoryRepository.save(ti);
            count++;
        }
        return count;
    }

    // ========== 3. EAdvances ==========

    private int seedEAdvances(Long shiftId, Long scid, LocalDateTime shiftStart) {
        int count = 0;
        List<UpiCompany> upiCompanies = upiCompanyRepository.findAll();

        // CARD advances
        String[][] cardData = {
            {"B001", "TID1001", "4521", "HDFC"},
            {"B002", "TID1002", "7834", "SBI"},
            {"B003", "TID1003", "3299", "ICICI"},
        };
        long[] cardAmounts = {2500, 4800, 7500};

        for (int i = 0; i < cardData.length; i++) {
            EAdvance ea = new EAdvance();
            ea.setScid(scid);
            ea.setShiftId(shiftId);
            ea.setAdvanceType(EAdvanceType.CARD);
            ea.setAmount(BigDecimal.valueOf(cardAmounts[i]));
            ea.setTransactionDate(shiftStart.plusMinutes(30 + i * 45));
            ea.setBatchId(cardData[i][0]);
            ea.setTid(cardData[i][1]);
            ea.setCardLast4Digit(cardData[i][2]);
            ea.setBankName(cardData[i][3]);
            ea.setRemarks("Test card payment " + (i + 1));
            eAdvanceRepository.save(ea);
            count++;
        }

        // UPI advances
        if (!upiCompanies.isEmpty()) {
            long[] upiAmounts = {1200, 3500, 2100};
            for (int i = 0; i < upiAmounts.length; i++) {
                EAdvance ea = new EAdvance();
                ea.setScid(scid);
                ea.setShiftId(shiftId);
                ea.setAdvanceType(EAdvanceType.UPI);
                ea.setAmount(BigDecimal.valueOf(upiAmounts[i]));
                ea.setTransactionDate(shiftStart.plusMinutes(60 + i * 40));
                ea.setUpiCompany(upiCompanies.get(i % upiCompanies.size()));
                ea.setRemarks("Test UPI payment " + (i + 1));
                eAdvanceRepository.save(ea);
                count++;
            }
        }

        // CCMS advances
        long[] ccmsAmounts = {8500, 6200};
        for (int i = 0; i < ccmsAmounts.length; i++) {
            EAdvance ea = new EAdvance();
            ea.setScid(scid);
            ea.setShiftId(shiftId);
            ea.setAdvanceType(EAdvanceType.CCMS);
            ea.setAmount(BigDecimal.valueOf(ccmsAmounts[i]));
            ea.setTransactionDate(shiftStart.plusMinutes(90 + i * 60));
            ea.setCcmsNumber("CCMS-" + LocalDate.now() + "-00" + (i + 1));
            ea.setRemarks("Test CCMS payment " + (i + 1));
            eAdvanceRepository.save(ea);
            count++;
        }

        // CHEQUE advance
        EAdvance cheque = new EAdvance();
        cheque.setScid(scid);
        cheque.setShiftId(shiftId);
        cheque.setAdvanceType(EAdvanceType.CHEQUE);
        cheque.setAmount(BigDecimal.valueOf(12000));
        cheque.setTransactionDate(shiftStart.plusMinutes(120));
        cheque.setChequeNo("456789");
        cheque.setChequeDate(LocalDate.now());
        cheque.setBankName("Canara Bank");
        cheque.setInFavorOf("StopForFuel Station");
        cheque.setRemarks("Test cheque payment");
        eAdvanceRepository.save(cheque);
        count++;

        // BANK_TRANSFER advance
        EAdvance bank = new EAdvance();
        bank.setScid(scid);
        bank.setShiftId(shiftId);
        bank.setAdvanceType(EAdvanceType.BANK_TRANSFER);
        bank.setAmount(BigDecimal.valueOf(9500));
        bank.setTransactionDate(shiftStart.plusMinutes(150));
        bank.setBankName("HDFC");
        bank.setRemarks("Test NEFT transfer");
        eAdvanceRepository.save(bank);
        count++;

        return count;
    }

    // ========== 4. Operational Advances ==========

    private int seedOperationalAdvances(Long shiftId, Long scid, LocalDateTime shiftStart) {
        List<Employee> employees = employeeRepository.findByScid(scid);
        if (employees.isEmpty()) return 0;

        int count = 0;

        // CASH advance - petty cash
        OperationalAdvance oa1 = new OperationalAdvance();
        oa1.setScid(scid);
        oa1.setShiftId(shiftId);
        oa1.setAdvanceType(AdvanceType.CASH);
        oa1.setAmount(BigDecimal.valueOf(2000));
        oa1.setAdvanceDate(shiftStart.plusMinutes(45));
        oa1.setStatus(AdvanceStatus.GIVEN);
        oa1.setPurpose("Petty cash for station supplies");
        oa1.setRecipientName(employees.get(0).getName());
        oa1.setEmployee(employees.get(0));
        oa1.setRemarks("Test petty cash advance");
        operationalAdvanceRepository.save(oa1);
        count++;

        // CASH advance - vehicle repair
        Employee emp2 = employees.size() > 1 ? employees.get(1) : employees.get(0);
        OperationalAdvance oa2 = new OperationalAdvance();
        oa2.setScid(scid);
        oa2.setShiftId(shiftId);
        oa2.setAdvanceType(AdvanceType.CASH);
        oa2.setAmount(BigDecimal.valueOf(3500));
        oa2.setAdvanceDate(shiftStart.plusMinutes(100));
        oa2.setStatus(AdvanceStatus.GIVEN);
        oa2.setPurpose("Vehicle repair");
        oa2.setRecipientName(emp2.getName());
        oa2.setEmployee(emp2);
        oa2.setRemarks("Test vehicle repair advance");
        operationalAdvanceRepository.save(oa2);
        count++;

        // SALARY advance
        OperationalAdvance oa3 = new OperationalAdvance();
        oa3.setScid(scid);
        oa3.setShiftId(shiftId);
        oa3.setAdvanceType(AdvanceType.SALARY);
        oa3.setAmount(BigDecimal.valueOf(5000));
        oa3.setAdvanceDate(shiftStart.plusMinutes(180));
        oa3.setStatus(AdvanceStatus.GIVEN);
        oa3.setPurpose("Salary advance");
        oa3.setRecipientName(employees.get(0).getName());
        oa3.setEmployee(employees.get(0));
        oa3.setRemarks("Test salary advance");
        operationalAdvanceRepository.save(oa3);
        count++;

        return count;
    }

    // ========== 5. Invoice Bills (Fuel + Oil/Accessories) ==========

    private Map<String, Integer> seedInvoiceBills(Long shiftId, Long scid, LocalDateTime shiftStart) {
        List<Product> fuels = productRepository.findByCategoryIgnoreCaseAndActiveAndScid("FUEL", true, scid);
        List<Product> lubricants = productRepository.findByCategoryIgnoreCaseAndActiveAndScid("LUBRICANT", true, scid);
        List<Product> accessories = productRepository.findByCategoryIgnoreCaseAndActiveAndScid("ACCESSORY", true, scid);
        List<Nozzle> nozzles = nozzleRepository.findByActiveAndScid(true, scid);
        List<Customer> customers = customerRepository.findAllByScid(scid);

        Map<String, Integer> counts = new LinkedHashMap<>();
        int cashCount = 0;
        int creditCount = 0;

        // Build product-to-nozzle map for fuel bills
        Map<Long, Nozzle> productNozzleMap = new HashMap<>();
        for (Nozzle n : nozzles) {
            productNozzleMap.putIfAbsent(n.getTank().getProduct().getId(), n);
        }

        // --- Fuel CASH bills ---
        if (!fuels.isEmpty() && !nozzles.isEmpty()) {
            double[] cashQuantities = {10.0, 25.0, 15.0, 40.0};
            for (int i = 0; i < cashQuantities.length; i++) {
                Product fuel = fuels.get(i % fuels.size());
                Nozzle nozzle = productNozzleMap.get(fuel.getId());
                if (nozzle == null) continue;

                InvoiceBill bill = createFuelBill(shiftId, scid, fuel, nozzle, cashQuantities[i],
                        BillType.CASH, "CASH", "TEST-C-" + (i + 1), null,
                        shiftStart.plusMinutes(20 + i * 50));
                invoiceBillRepository.save(bill);
                cashCount++;
            }
        }

        // --- Fuel CREDIT bills ---
        if (!fuels.isEmpty() && !nozzles.isEmpty() && !customers.isEmpty()) {
            double[] creditQuantities = {50.0, 30.0, 75.0};
            for (int i = 0; i < creditQuantities.length && i < customers.size(); i++) {
                Product fuel = fuels.get(i % fuels.size());
                Nozzle nozzle = productNozzleMap.get(fuel.getId());
                if (nozzle == null) continue;

                Customer cust = customers.get(i);
                InvoiceBill bill = createFuelBill(shiftId, scid, fuel, nozzle, creditQuantities[i],
                        BillType.CREDIT, null, "TEST-A-" + (i + 1), cust,
                        shiftStart.plusMinutes(40 + i * 60));
                // Assign first vehicle of customer
                List<Vehicle> custVehicles = vehicleRepository.findByCustomerId(cust.getId());
                if (!custVehicles.isEmpty()) {
                    bill.setVehicle(custVehicles.get(0));
                }
                invoiceBillRepository.save(bill);
                creditCount++;
            }
        }

        // --- Oil/Lubricant CASH bills ---
        if (!lubricants.isEmpty()) {
            // Sell 2 lubricant items in one cash bill
            InvoiceBill oilCashBill = new InvoiceBill();
            oilCashBill.setScid(scid);
            oilCashBill.setShiftId(shiftId);
            oilCashBill.setBillType(BillType.CASH);
            oilCashBill.setPaymentMode("CASH");
            oilCashBill.setDate(shiftStart.plusMinutes(75));
            oilCashBill.setBillNo("TEST-C-OIL-1");
            oilCashBill.setBillDesc("Test cash oil sale");
            oilCashBill.setTotalDiscount(BigDecimal.ZERO);

            BigDecimal oilCashTotal = BigDecimal.ZERO;
            int itemCount = Math.min(2, lubricants.size());
            double[] oilQtys = {2.0, 1.0};
            for (int i = 0; i < itemCount; i++) {
                Product oil = lubricants.get(i);
                BigDecimal qty = BigDecimal.valueOf(oilQtys[i]);
                BigDecimal price = oil.getPrice();
                BigDecimal amount = qty.multiply(price).setScale(4, RoundingMode.HALF_UP);

                InvoiceProduct ip = new InvoiceProduct();
                ip.setScid(scid);
                ip.setShiftId(shiftId);
                ip.setProduct(oil);
                ip.setQuantity(qty);
                ip.setUnitPrice(price);
                ip.setAmount(amount);
                ip.setGrossAmount(amount);
                ip.setDiscountRate(BigDecimal.ZERO);
                ip.setDiscountAmount(BigDecimal.ZERO);
                ip.setInvoiceBill(oilCashBill);
                oilCashBill.getProducts().add(ip);
                oilCashTotal = oilCashTotal.add(amount);
            }
            oilCashBill.setGrossAmount(oilCashTotal);
            oilCashBill.setNetAmount(oilCashTotal);
            invoiceBillRepository.save(oilCashBill);
            cashCount++;

            // Another single-item oil cash bill
            if (lubricants.size() > 2) {
                Product oil3 = lubricants.get(2);
                BigDecimal qty = BigDecimal.valueOf(1.0);
                BigDecimal price = oil3.getPrice();
                BigDecimal amount = qty.multiply(price).setScale(4, RoundingMode.HALF_UP);

                InvoiceBill oilCash2 = new InvoiceBill();
                oilCash2.setScid(scid);
                oilCash2.setShiftId(shiftId);
                oilCash2.setBillType(BillType.CASH);
                oilCash2.setPaymentMode("CASH");
                oilCash2.setDate(shiftStart.plusMinutes(200));
                oilCash2.setBillNo("TEST-C-OIL-2");
                oilCash2.setBillDesc("Test cash oil sale - " + oil3.getName());
                oilCash2.setGrossAmount(amount);
                oilCash2.setTotalDiscount(BigDecimal.ZERO);
                oilCash2.setNetAmount(amount);

                InvoiceProduct ip = new InvoiceProduct();
                ip.setScid(scid);
                ip.setShiftId(shiftId);
                ip.setProduct(oil3);
                ip.setQuantity(qty);
                ip.setUnitPrice(price);
                ip.setAmount(amount);
                ip.setGrossAmount(amount);
                ip.setDiscountRate(BigDecimal.ZERO);
                ip.setDiscountAmount(BigDecimal.ZERO);
                ip.setInvoiceBill(oilCash2);
                oilCash2.getProducts().add(ip);

                invoiceBillRepository.save(oilCash2);
                cashCount++;
            }
        }

        // --- Accessory CASH bill ---
        if (!accessories.isEmpty()) {
            InvoiceBill accBill = new InvoiceBill();
            accBill.setScid(scid);
            accBill.setShiftId(shiftId);
            accBill.setBillType(BillType.CASH);
            accBill.setPaymentMode("CASH");
            accBill.setDate(shiftStart.plusMinutes(130));
            accBill.setBillNo("TEST-C-ACC-1");
            accBill.setBillDesc("Test accessory sale");
            accBill.setTotalDiscount(BigDecimal.ZERO);

            BigDecimal accTotal = BigDecimal.ZERO;
            for (int i = 0; i < Math.min(2, accessories.size()); i++) {
                Product acc = accessories.get(i);
                BigDecimal qty = BigDecimal.valueOf(1.0);
                BigDecimal price = acc.getPrice();
                BigDecimal amount = qty.multiply(price).setScale(4, RoundingMode.HALF_UP);

                InvoiceProduct ip = new InvoiceProduct();
                ip.setScid(scid);
                ip.setShiftId(shiftId);
                ip.setProduct(acc);
                ip.setQuantity(qty);
                ip.setUnitPrice(price);
                ip.setAmount(amount);
                ip.setGrossAmount(amount);
                ip.setDiscountRate(BigDecimal.ZERO);
                ip.setDiscountAmount(BigDecimal.ZERO);
                ip.setInvoiceBill(accBill);
                accBill.getProducts().add(ip);
                accTotal = accTotal.add(amount);
            }
            accBill.setGrossAmount(accTotal);
            accBill.setNetAmount(accTotal);
            invoiceBillRepository.save(accBill);
            cashCount++;
        }

        // --- Oil/Lubricant CREDIT bills ---
        if (!lubricants.isEmpty() && !customers.isEmpty()) {
            // Credit oil bill linked to a customer
            int custIdx = Math.min(customers.size() - 1, 1); // use 2nd customer if available
            Customer cust = customers.get(custIdx);
            Product oil = lubricants.get(0);
            BigDecimal qty = BigDecimal.valueOf(3.0);
            BigDecimal price = oil.getPrice();
            BigDecimal amount = qty.multiply(price).setScale(4, RoundingMode.HALF_UP);

            InvoiceBill oilCreditBill = new InvoiceBill();
            oilCreditBill.setScid(scid);
            oilCreditBill.setShiftId(shiftId);
            oilCreditBill.setBillType(BillType.CREDIT);
            oilCreditBill.setDate(shiftStart.plusMinutes(160));
            oilCreditBill.setBillNo("TEST-A-OIL-1");
            oilCreditBill.setCustomer(cust);
            oilCreditBill.setBillDesc("Test credit oil sale - " + oil.getName());
            oilCreditBill.setGrossAmount(amount);
            oilCreditBill.setTotalDiscount(BigDecimal.ZERO);
            oilCreditBill.setNetAmount(amount);

            InvoiceProduct ip = new InvoiceProduct();
            ip.setScid(scid);
            ip.setShiftId(shiftId);
            ip.setProduct(oil);
            ip.setQuantity(qty);
            ip.setUnitPrice(price);
            ip.setAmount(amount);
            ip.setGrossAmount(amount);
            ip.setDiscountRate(BigDecimal.ZERO);
            ip.setDiscountAmount(BigDecimal.ZERO);
            ip.setInvoiceBill(oilCreditBill);
            oilCreditBill.getProducts().add(ip);

            // Assign first vehicle of customer
            List<Vehicle> custVehicles = vehicleRepository.findByCustomerId(cust.getId());
            if (!custVehicles.isEmpty()) {
                oilCreditBill.setVehicle(custVehicles.get(0));
            }
            invoiceBillRepository.save(oilCreditBill);
            creditCount++;
        }

        counts.put("fuelCash", cashCount - (lubricants.isEmpty() ? 0 : (lubricants.size() > 2 ? 2 : 1)) - (accessories.isEmpty() ? 0 : 1));
        counts.put("fuelCredit", creditCount - (lubricants.isEmpty() || customers.isEmpty() ? 0 : 1));
        counts.put("oilCash", lubricants.isEmpty() ? 0 : (lubricants.size() > 2 ? 2 : 1));
        counts.put("oilCredit", lubricants.isEmpty() || customers.isEmpty() ? 0 : 1);
        counts.put("accessoryCash", accessories.isEmpty() ? 0 : 1);
        counts.put("totalCash", cashCount);
        counts.put("totalCredit", creditCount);
        return counts;
    }

    private InvoiceBill createFuelBill(Long shiftId, Long scid, Product fuel, Nozzle nozzle,
                                        double quantity, BillType billType, String paymentMode,
                                        String billNo, Customer customer, LocalDateTime date) {
        BigDecimal qty = BigDecimal.valueOf(quantity);
        BigDecimal price = fuel.getPrice();
        BigDecimal amount = qty.multiply(price).setScale(4, RoundingMode.HALF_UP);

        InvoiceBill bill = new InvoiceBill();
        bill.setScid(scid);
        bill.setShiftId(shiftId);
        bill.setBillType(billType);
        bill.setPaymentMode(paymentMode);
        bill.setDate(date);
        bill.setBillNo(billNo);
        bill.setCustomer(customer);
        bill.setGrossAmount(amount);
        bill.setTotalDiscount(BigDecimal.ZERO);
        bill.setNetAmount(amount);
        bill.setBillDesc("Test " + billType.name().toLowerCase() + " bill - " + fuel.getName());

        InvoiceProduct ip = new InvoiceProduct();
        ip.setScid(scid);
        ip.setShiftId(shiftId);
        ip.setProduct(fuel);
        ip.setNozzle(nozzle);
        ip.setQuantity(qty);
        ip.setUnitPrice(price);
        ip.setAmount(amount);
        ip.setGrossAmount(amount);
        ip.setDiscountRate(BigDecimal.ZERO);
        ip.setDiscountAmount(BigDecimal.ZERO);
        ip.setInvoiceBill(bill);
        bill.getProducts().add(ip);

        return bill;
    }

    // ========== 6. Payments (credit bill payments from customers) ==========

    private int seedPayments(Long shiftId, Long scid, LocalDateTime shiftStart) {
        List<Customer> customers = customerRepository.findAllByScid(scid);
        if (customers.isEmpty()) return 0;

        int count = 0;

        // Payment against previous credit bills (not the ones we just created)
        // This simulates customers coming in to pay their outstanding credit
        PaymentMode cashMode = paymentModeRepository.findByModeName("CASH").orElse(null);
        PaymentMode upiMode = paymentModeRepository.findByModeName("UPI").orElse(null);

        if (cashMode != null && customers.size() >= 1) {
            // Customer 1 pays cash
            Payment p1 = new Payment();
            p1.setScid(scid);
            p1.setShiftId(shiftId);
            p1.setPaymentDate(shiftStart.plusMinutes(110));
            p1.setAmount(BigDecimal.valueOf(15000));
            p1.setPaymentMode(cashMode);
            p1.setCustomer(customers.get(0));
            p1.setRemarks("Test cash payment - outstanding credit");
            paymentRepository.save(p1);
            count++;
        }

        if (upiMode != null && customers.size() >= 2) {
            // Customer 2 pays via UPI
            Payment p2 = new Payment();
            p2.setScid(scid);
            p2.setShiftId(shiftId);
            p2.setPaymentDate(shiftStart.plusMinutes(170));
            p2.setAmount(BigDecimal.valueOf(8500));
            p2.setPaymentMode(upiMode);
            p2.setReferenceNo("UPI-REF-TEST-001");
            p2.setCustomer(customers.get(1));
            p2.setRemarks("Test UPI payment - outstanding credit");
            paymentRepository.save(p2);
            count++;
        }

        if (cashMode != null && customers.size() >= 3) {
            // Customer 3 pays cash
            Payment p3 = new Payment();
            p3.setScid(scid);
            p3.setShiftId(shiftId);
            p3.setPaymentDate(shiftStart.plusMinutes(220));
            p3.setAmount(BigDecimal.valueOf(25000));
            p3.setPaymentMode(cashMode);
            p3.setCustomer(customers.get(2));
            p3.setRemarks("Test cash payment - partial settlement");
            paymentRepository.save(p3);
            count++;
        }

        return count;
    }

    // ========== 7. Expenses ==========

    private int seedExpenses(Long shiftId, Long scid, LocalDateTime shiftStart) {
        List<ExpenseType> expenseTypes = expenseTypeRepository.findAll();
        if (expenseTypes.isEmpty()) return 0;

        int count = 0;

        // Find specific expense types or use what's available
        ExpenseType maintenance = expenseTypes.stream()
                .filter(et -> "Maintenance".equalsIgnoreCase(et.getTypeName()))
                .findFirst().orElse(expenseTypes.get(0));

        ExpenseType miscellaneous = expenseTypes.stream()
                .filter(et -> "Miscellaneous".equalsIgnoreCase(et.getTypeName()))
                .findFirst().orElse(expenseTypes.get(expenseTypes.size() > 1 ? 1 : 0));

        ExpenseType electricity = expenseTypes.stream()
                .filter(et -> "Electricity".equalsIgnoreCase(et.getTypeName()))
                .findFirst().orElse(null);

        // Maintenance expense
        Expense e1 = new Expense();
        e1.setScid(scid);
        e1.setShiftId(shiftId);
        e1.setExpenseDate(shiftStart.plusMinutes(80));
        e1.setAmount(BigDecimal.valueOf(1500));
        e1.setExpenseType(maintenance);
        e1.setDescription("Pump maintenance - filter replacement");
        e1.setRemarks("Test expense");
        expenseRepository.save(e1);
        count++;

        // Miscellaneous expense
        Expense e2 = new Expense();
        e2.setScid(scid);
        e2.setShiftId(shiftId);
        e2.setExpenseDate(shiftStart.plusMinutes(140));
        e2.setAmount(BigDecimal.valueOf(350));
        e2.setExpenseType(miscellaneous);
        e2.setDescription("Stationery and printing");
        e2.setRemarks("Test expense");
        expenseRepository.save(e2);
        count++;

        // Electricity expense (if type exists)
        if (electricity != null) {
            Expense e3 = new Expense();
            e3.setScid(scid);
            e3.setShiftId(shiftId);
            e3.setExpenseDate(shiftStart.plusMinutes(190));
            e3.setAmount(BigDecimal.valueOf(4200));
            e3.setExpenseType(electricity);
            e3.setDescription("EB bill payment");
            e3.setRemarks("Test expense");
            expenseRepository.save(e3);
            count++;
        }

        return count;
    }

    // ========== 8. Incentive Payments ==========

    private int seedIncentives(Long shiftId, Long scid, LocalDateTime shiftStart) {
        List<Customer> customers = customerRepository.findAllByScid(scid);
        if (customers.isEmpty()) return 0;

        int count = 0;

        // Incentive to customer 1
        IncentivePayment inc1 = new IncentivePayment();
        inc1.setScid(scid);
        inc1.setShiftId(shiftId);
        inc1.setPaymentDate(shiftStart.plusMinutes(155));
        inc1.setAmount(BigDecimal.valueOf(500));
        inc1.setCustomer(customers.get(0));
        inc1.setDescription("Loyalty incentive - " + customers.get(0).getName());
        incentivePaymentRepository.save(inc1);
        count++;

        if (customers.size() >= 2) {
            // Incentive to customer 2
            IncentivePayment inc2 = new IncentivePayment();
            inc2.setScid(scid);
            inc2.setShiftId(shiftId);
            inc2.setPaymentDate(shiftStart.plusMinutes(210));
            inc2.setAmount(BigDecimal.valueOf(750));
            inc2.setCustomer(customers.get(1));
            inc2.setDescription("Volume bonus - " + customers.get(1).getName());
            incentivePaymentRepository.save(inc2);
            count++;
        }

        return count;
    }

    // ========== 9. Product Inventory for non-fuel products ==========

    private int seedProductInventory(Long shiftId, Long scid) {
        // Calculate sales per non-fuel product from the invoices we just created
        List<InvoiceBill> invoices = invoiceBillRepository.findByShiftId(shiftId);
        Map<Long, Double> salesByProduct = new HashMap<>();

        for (InvoiceBill inv : invoices) {
            if (inv.getProducts() != null) {
                for (InvoiceProduct ip : inv.getProducts()) {
                    if (ip.getProduct() != null && !"FUEL".equalsIgnoreCase(ip.getProduct().getCategory())) {
                        double qty = ip.getQuantity() != null ? ip.getQuantity().doubleValue() : 0;
                        salesByProduct.merge(ip.getProduct().getId(), qty, Double::sum);
                    }
                }
            }
        }

        int count = 0;
        for (Map.Entry<Long, Double> entry : salesByProduct.entrySet()) {
            Product product = productRepository.findById(entry.getKey()).orElse(null);
            if (product == null) continue;

            double sales = entry.getValue();
            double openStock = sales + 10; // simulate having more stock than sold

            ProductInventory pi = new ProductInventory();
            pi.setScid(scid);
            pi.setShiftId(shiftId);
            pi.setDate(LocalDate.now());
            pi.setProduct(product);
            pi.setOpenStock(openStock);
            pi.setIncomeStock(0.0);
            pi.setTotalStock(openStock);
            pi.setCloseStock(openStock - sales);
            pi.setSales(sales);
            pi.setRate(product.getPrice());
            pi.setAmount(product.getPrice() != null
                    ? product.getPrice().multiply(BigDecimal.valueOf(sales)).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
            productInventoryRepository.save(pi);
            count++;
        }
        return count;
    }
}
