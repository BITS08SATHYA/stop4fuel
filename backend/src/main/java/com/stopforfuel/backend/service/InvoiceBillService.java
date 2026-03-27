package com.stopforfuel.backend.service;

import com.stopforfuel.backend.dto.ProductSalesSummary;
import com.stopforfuel.backend.entity.*;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.exception.ResourceNotFoundException;
import com.stopforfuel.backend.repository.*;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InvoiceBillService {

    private final InvoiceBillRepository repository;
    private final S3StorageService s3StorageService;
    private final CustomerRepository customerRepository;
    private final VehicleRepository vehicleRepository;
    private final TankInventoryRepository tankInventoryRepository;
    private final NozzleInventoryRepository nozzleInventoryRepository;
    private final ProductInventoryRepository productInventoryRepository;
    private final com.stopforfuel.backend.repository.NozzleRepository nozzleRepository;
    private final ProductRepository productRepository;
    private final CashierStockRepository cashierStockRepository;
    private final CustomerService customerService;
    private final IncentiveService incentiveService;
    private final ShiftService shiftService;
    private final EAdvanceService eAdvanceService;
    private final IncentivePaymentService incentivePaymentService;
    private final BillSequenceService billSequenceService;

    public List<InvoiceBill> getAllInvoices() {
        return repository.findAllByScid(SecurityUtils.getScid());
    }

    public List<InvoiceBill> getInvoicesByShift(Long shiftId) {
        return repository.findByShiftId(shiftId);
    }

    @Transactional
    public InvoiceBill createInvoice(InvoiceBill invoice) {
        // --- Validation: Check customer and vehicle status ---
        if (invoice.getCustomer() != null && invoice.getCustomer().getId() != null) {
            Customer customer = customerRepository.findById(invoice.getCustomer().getId())
                    .orElseThrow(() -> new RuntimeException("Customer not found"));

            if (!customer.canRaiseInvoice()) {
                throw new BusinessException(
                        "Cannot create invoice: Customer '" + customer.getName() +
                        "' is " + customer.getStatus() +
                        (customer.isBlocked() ? " (credit limit exceeded)" : " (manually disabled)"));
            }
            invoice.setCustomer(customer);
        }

        if (invoice.getVehicle() != null && invoice.getVehicle().getId() != null) {
            Vehicle vehicle = vehicleRepository.findById(invoice.getVehicle().getId())
                    .orElseThrow(() -> new RuntimeException("Vehicle not found"));

            if (!vehicle.canRaiseInvoice()) {
                throw new BusinessException(
                        "Cannot create invoice: Vehicle '" + vehicle.getVehicleNumber() +
                        "' is " + vehicle.getStatus() +
                        (vehicle.isBlocked() ? " (liter limit exceeded)" : " (manually disabled)"));
            }
            invoice.setVehicle(vehicle);
        }

        // --- Pre-invoice credit limit validation (CREDIT invoices only) ---
        if ("CREDIT".equals(invoice.getBillType()) && invoice.getCustomer() != null && invoice.getCustomer().getId() != null) {
            // Calculate total liters and amount for this invoice
            BigDecimal preLiters = BigDecimal.ZERO;
            BigDecimal preAmount = BigDecimal.ZERO;
            if (invoice.getProducts() != null) {
                for (InvoiceProduct ip : invoice.getProducts()) {
                    BigDecimal qty = ip.getQuantity() != null ? ip.getQuantity() : BigDecimal.ZERO;
                    BigDecimal price = ip.getUnitPrice() != null ? ip.getUnitPrice() : BigDecimal.ZERO;
                    preLiters = preLiters.add(qty);
                    preAmount = preAmount.add(qty.multiply(price));
                }
            }

            // Check customer credit limit (amount and liters)
            String customerLimitError = customerService.validateCreditLimitBeforeInvoice(
                    invoice.getCustomer().getId(), preAmount, preLiters);
            if (customerLimitError != null) {
                throw new BusinessException("Cannot create invoice: " + customerLimitError);
            }

            // Check vehicle monthly liter limit
            if (invoice.getVehicle() != null && invoice.getVehicle().getId() != null) {
                String vehicleLimitError = customerService.validateVehicleLimitBeforeInvoice(
                        invoice.getVehicle().getId(), preLiters);
                if (vehicleLimitError != null) {
                    throw new BusinessException("Cannot create invoice: " + vehicleLimitError);
                }
            }
        }

        // --- Validate fuel type compatibility and max capacity ---
        Vehicle validatedVehicle = invoice.getVehicle();
        if (validatedVehicle != null && validatedVehicle.getId() != null && invoice.getProducts() != null) {
            Vehicle veh = vehicleRepository.findById(validatedVehicle.getId()).orElse(null);
            if (veh != null) {
                // Determine vehicle's fuel family from its preferred product
                String vehicleFuelFamily = null;
                if (veh.getPreferredProduct() != null) {
                    Product prefProd = productRepository.findById(veh.getPreferredProduct().getId()).orElse(null);
                    if (prefProd != null) {
                        vehicleFuelFamily = prefProd.getFuelFamily();
                    }
                }

                BigDecimal totalFuelQty = BigDecimal.ZERO;
                for (InvoiceProduct ip : invoice.getProducts()) {
                    if (ip.getProduct() != null && ip.getProduct().getId() != null) {
                        Product prod = productRepository.findById(ip.getProduct().getId()).orElse(null);
                        if (prod != null && "FUEL".equalsIgnoreCase(prod.getCategory())) {
                            BigDecimal qty = ip.getQuantity() != null ? ip.getQuantity() : BigDecimal.ZERO;
                            totalFuelQty = totalFuelQty.add(qty);

                            // Validate fuel family compatibility
                            if (vehicleFuelFamily != null && prod.getFuelFamily() != null
                                    && !vehicleFuelFamily.equalsIgnoreCase(prod.getFuelFamily())) {
                                throw new BusinessException(
                                        "Cannot create invoice: Product '" + prod.getName()
                                        + "' (" + prod.getFuelFamily() + " family) is not compatible with vehicle '"
                                        + veh.getVehicleNumber() + "' which uses " + vehicleFuelFamily + " fuel.");
                            }
                        }
                    }
                }

                // Validate total fuel quantity against vehicle max tank capacity
                if (veh.getMaxCapacity() != null && veh.getMaxCapacity().compareTo(BigDecimal.ZERO) > 0
                        && totalFuelQty.compareTo(veh.getMaxCapacity()) > 0) {
                    throw new BusinessException(
                            "Cannot create invoice: Total fuel quantity (" + totalFuelQty + " L) exceeds vehicle '"
                            + veh.getVehicleNumber() + "' max tank capacity of " + veh.getMaxCapacity() + " L.");
                }
            }
        }

        // --- Validate all products are active and have sufficient inventory ---
        if (invoice.getProducts() != null) {
            for (InvoiceProduct ip : invoice.getProducts()) {
                if (ip.getProduct() != null && ip.getProduct().getId() != null) {
                    Product prod = productRepository.findById(ip.getProduct().getId())
                            .orElseThrow(() -> new RuntimeException("Product not found"));
                    if (!prod.isActive()) {
                        throw new BusinessException(
                                "Cannot create invoice: Product '" + prod.getName() + "' is disabled.");
                    }

                    // Check inventory availability
                    BigDecimal requiredQty = ip.getQuantity() != null ? ip.getQuantity() : BigDecimal.ZERO;
                    if (requiredQty.compareTo(BigDecimal.ZERO) > 0) {
                        // Check tank inventory if nozzle is specified (fuel products)
                        if (ip.getNozzle() != null && ip.getNozzle().getId() != null) {
                            Nozzle nozzle = nozzleRepository.findById(ip.getNozzle().getId()).orElse(null);
                            if (nozzle != null && nozzle.getTank() != null) {
                                TankInventory tankInv = tankInventoryRepository
                                        .findTopByTankIdOrderByDateDescIdDesc(nozzle.getTank().getId());
                                if (tankInv == null) {
                                    throw new BusinessException(
                                            "Cannot create invoice: No inventory record found for tank linked to product '"
                                            + prod.getName() + "'.");
                                }
                                double available = tankInv.getCloseStock() != null ? tankInv.getCloseStock() : 0.0;
                                if (available <= 0) {
                                    throw new BusinessException(
                                            "Cannot create invoice: Tank stock for product '" + prod.getName()
                                            + "' is empty (0 liters available).");
                                }
                                if (available < requiredQty.doubleValue()) {
                                    throw new BusinessException(
                                            "Cannot create invoice: Insufficient tank stock for product '"
                                            + prod.getName() + "'. Available: " + String.format("%.2f", available)
                                            + ", Required: " + requiredQty + ".");
                                }
                            }
                        }

                        // Check product inventory
                        ProductInventory productInv = productInventoryRepository
                                .findTopByProductIdOrderByDateDescIdDesc(prod.getId());
                        if (productInv != null) {
                            double available = productInv.getCloseStock() != null ? productInv.getCloseStock() : 0.0;
                            if (available <= 0) {
                                throw new BusinessException(
                                        "Cannot create invoice: Product '" + prod.getName()
                                        + "' is out of stock (0 available).");
                            }
                            if (available < requiredQty.doubleValue()) {
                                throw new BusinessException(
                                        "Cannot create invoice: Insufficient stock for product '"
                                        + prod.getName() + "'. Available: " + String.format("%.2f", available)
                                        + ", Required: " + requiredQty + ".");
                            }
                        }
                    }
                }
            }
        }

        // --- Calculate net amount from products (with incentive discounts) ---
        BigDecimal totalLiters = BigDecimal.ZERO;
        if (invoice.getProducts() != null) {
            BigDecimal totalGross = BigDecimal.ZERO;
            BigDecimal totalDiscountSum = BigDecimal.ZERO;

            Long custId = (invoice.getCustomer() != null && invoice.getCustomer().getId() != null)
                    ? invoice.getCustomer().getId() : null;

            for (InvoiceProduct product : invoice.getProducts()) {
                product.setInvoiceBill(invoice);
                if (product.getQuantity() != null) {
                    totalLiters = totalLiters.add(product.getQuantity());
                }

                // Compute gross amount (qty * unitPrice)
                BigDecimal qty = product.getQuantity() != null ? product.getQuantity() : BigDecimal.ZERO;
                BigDecimal price = product.getUnitPrice() != null ? product.getUnitPrice() : BigDecimal.ZERO;
                BigDecimal gross = qty.multiply(price);
                product.setGrossAmount(gross);
                totalGross = totalGross.add(gross);

                // Apply incentive discount if applicable
                if (custId != null && product.getProduct() != null && product.getProduct().getId() != null) {
                    incentiveService.getActiveIncentive(custId, product.getProduct().getId())
                            .ifPresent(incentive -> {
                                boolean meetsMin = incentive.getMinQuantity() == null
                                        || qty.compareTo(incentive.getMinQuantity()) >= 0;
                                if (meetsMin) {
                                    product.setDiscountRate(incentive.getDiscountRate());
                                    BigDecimal discAmt = incentive.getDiscountRate().multiply(qty);
                                    product.setDiscountAmount(discAmt);
                                    product.setAmount(gross.subtract(discAmt));
                                }
                            });
                }

                // If no discount was applied, set amount = gross
                if (product.getAmount() == null || product.getDiscountRate() != null) {
                    // already set above if discount applied
                } else {
                    product.setAmount(gross);
                }
                if (product.getAmount() == null) {
                    product.setAmount(gross);
                }

                if (product.getDiscountAmount() != null) {
                    totalDiscountSum = totalDiscountSum.add(product.getDiscountAmount());
                }
            }

            invoice.setGrossAmount(totalGross);
            invoice.setTotalDiscount(totalDiscountSum);
            BigDecimal netAmount = totalGross.subtract(totalDiscountSum);
            if (invoice.getNetAmount() == null || invoice.getNetAmount().compareTo(BigDecimal.ZERO) == 0) {
                invoice.setNetAmount(netAmount);
            }
        }

        if (invoice.getScid() == null) {
            invoice.setScid(SecurityUtils.getScid());
        }

        // --- Generate bill number ---
        String billNo = billSequenceService.getNextBillNo(invoice.getBillType());
        invoice.setBillNo(billNo);

        // --- Save the invoice ---
        InvoiceBill saved = repository.save(invoice);

        // --- Update consumed liters on vehicle and customer ---
        if (totalLiters.compareTo(BigDecimal.ZERO) > 0) {
            updateConsumedLiters(invoice.getVehicle(), invoice.getCustomer(), totalLiters);
        }

        // --- Auto-deduct inventory ---
        if (invoice.getProducts() != null) {
            for (InvoiceProduct invoiceProduct : invoice.getProducts()) {
                deductInventory(invoiceProduct);
            }
        }

        // --- Auto-block check (amount, liters, aging) ---
        if (invoice.getCustomer() != null && invoice.getCustomer().getId() != null) {
            customerService.checkAndAutoBlock(invoice.getCustomer().getId());
        }

        // --- Auto-create shift transaction for CASH invoices ---
        autoCreateShiftTransaction(saved);

        // --- Auto-create incentive payment for CASH invoices with discount ---
        autoCreateIncentivePayment(saved);

        return saved;
    }

    /**
     * Auto-creates an EAdvance entry when a CASH invoice is paid via electronic mode.
     * Cash payments need no separate record — the InvoiceBill itself is the source of truth.
     */
    private void autoCreateShiftTransaction(InvoiceBill invoice) {
        if (!"CASH".equals(invoice.getBillType())) {
            return;
        }

        Shift activeShift = shiftService.getActiveShift();
        if (activeShift == null) {
            return;
        }

        BigDecimal amount = invoice.getNetAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        String paymentMode = invoice.getPaymentMode();
        if (paymentMode == null) paymentMode = "CASH";

        String upperMode = paymentMode.toUpperCase();
        // Only create EAdvance for electronic payment modes
        switch (upperMode) {
            case "UPI":
            case "CARD":
            case "CHEQUE":
            case "BANK TRANSFER":
            case "BANK":
            case "CCMS":
                String customerName = invoice.getCustomer() != null ? invoice.getCustomer().getName() : null;
                String remark = "Auto: Invoice #" + invoice.getId()
                        + (customerName != null ? " - " + customerName : "");
                EAdvance eAdv = new EAdvance();
                eAdv.setAmount(amount);
                eAdv.setRemarks(remark);
                eAdv.setShiftId(activeShift.getId());
                eAdv.setScid(invoice.getScid());
                String type = "BANK TRANSFER".equals(upperMode) ? "BANK_TRANSFER" : upperMode;
                eAdv.setAdvanceType(type);
                eAdv.setInvoiceBill(invoice);
                if ("CARD".equals(upperMode) && customerName != null) {
                    eAdv.setCustomerName(customerName);
                }
                eAdvanceService.create(eAdv);
                break;
            default:
                // CASH — no separate record needed, InvoiceBill is the source of truth
                break;
        }
    }

    /**
     * Auto-creates an IncentivePayment entry when a CASH invoice has a discount applied.
     */
    private void autoCreateIncentivePayment(InvoiceBill invoice) {
        if (!"CASH".equals(invoice.getBillType())) {
            return;
        }

        BigDecimal discount = invoice.getTotalDiscount();
        if (discount == null || discount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        Shift activeShift = shiftService.getActiveShift();
        if (activeShift == null) {
            return;
        }

        String customerName = invoice.getCustomer() != null ? invoice.getCustomer().getName() : "Walk-in";
        IncentivePayment incentivePayment = new IncentivePayment();
        incentivePayment.setAmount(discount);
        incentivePayment.setDescription("Auto: Discount on Invoice #" + invoice.getBillNo() + " - " + customerName);
        incentivePayment.setCustomer(invoice.getCustomer());
        incentivePayment.setInvoiceBill(invoice);
        incentivePayment.setShiftId(activeShift.getId());
        incentivePayment.setScid(invoice.getScid());
        incentivePayment.setPaymentDate(LocalDateTime.now());
        incentivePaymentService.create(incentivePayment);
    }

    /**
     * Updates consumed liters on both vehicle and customer,
     * and auto-blocks them if limits are exceeded.
     */
    private void updateConsumedLiters(Vehicle vehicle, Customer customer, BigDecimal liters) {
        if (vehicle != null) {
            Vehicle v = vehicleRepository.findById(vehicle.getId()).orElse(null);
            if (v != null) {
                BigDecimal newConsumed = (v.getConsumedLiters() != null ? v.getConsumedLiters() : BigDecimal.ZERO)
                        .add(liters);
                v.setConsumedLiters(newConsumed);

                if (v.getMaxLitersPerMonth() != null
                        && newConsumed.compareTo(v.getMaxLitersPerMonth()) >= 0
                        && "ACTIVE".equals(v.getStatus())) {
                    v.setStatus("BLOCKED");
                }
                vehicleRepository.save(v);
            }
        }

        if (customer != null) {
            Customer c = customerRepository.findById(customer.getId()).orElse(null);
            if (c != null) {
                BigDecimal newConsumed = (c.getConsumedLiters() != null ? c.getConsumedLiters() : BigDecimal.ZERO)
                        .add(liters);
                c.setConsumedLiters(newConsumed);

                if (c.getCreditLimitLiters() != null
                        && newConsumed.compareTo(c.getCreditLimitLiters()) >= 0
                        && "ACTIVE".equals(c.getStatus())) {
                    c.setStatus("BLOCKED");
                }
                customerRepository.save(c);
            }
        }
    }

    /**
     * Auto-deducts inventory based on the invoice product line item.
     * For each product sold:
     * 1. Tank inventory: reduce closeStock by quantity (if nozzle → tank link exists)
     * 2. Product inventory: reduce closeStock by quantity
     * 3. Nozzle inventory: advance closeMeterReading by quantity
     */
    private void deductInventory(InvoiceProduct invoiceProduct) {
        if (invoiceProduct.getQuantity() == null || invoiceProduct.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        double qty = invoiceProduct.getQuantity().doubleValue();

        // --- 1. Tank inventory deduction (via nozzle → tank) ---
        if (invoiceProduct.getNozzle() != null && invoiceProduct.getNozzle().getId() != null) {
            Nozzle nozzle = nozzleRepository.findById(invoiceProduct.getNozzle().getId()).orElse(null);
            if (nozzle != null && nozzle.getTank() != null) {
                Long tankId = nozzle.getTank().getId();
                TankInventory tankInv = tankInventoryRepository.findTopByTankIdOrderByDateDescIdDesc(tankId);
                if (tankInv != null) {
                    double currentClose = tankInv.getCloseStock() != null ? tankInv.getCloseStock() : 0.0;
                    tankInv.setCloseStock(currentClose - qty);
                    // Recalculate saleStock
                    double total = tankInv.getTotalStock() != null ? tankInv.getTotalStock() : 0.0;
                    tankInv.setSaleStock(total - tankInv.getCloseStock());
                    tankInventoryRepository.save(tankInv);
                }

                // --- 3. Nozzle meter reading update ---
                NozzleInventory nozzleInv = nozzleInventoryRepository
                        .findTopByNozzleIdOrderByDateDescIdDesc(nozzle.getId());
                if (nozzleInv != null) {
                    double currentReading = nozzleInv.getCloseMeterReading() != null
                            ? nozzleInv.getCloseMeterReading() : 0.0;
                    nozzleInv.setCloseMeterReading(currentReading + qty);
                    // Recalculate sales
                    double openReading = nozzleInv.getOpenMeterReading() != null
                            ? nozzleInv.getOpenMeterReading() : 0.0;
                    nozzleInv.setSales(nozzleInv.getCloseMeterReading() - openReading);
                    nozzleInventoryRepository.save(nozzleInv);
                }
            }
        }

        // --- 2. Product inventory deduction ---
        if (invoiceProduct.getProduct() != null && invoiceProduct.getProduct().getId() != null) {
            Long productId = invoiceProduct.getProduct().getId();
            ProductInventory productInv = productInventoryRepository
                    .findTopByProductIdOrderByDateDescIdDesc(productId);
            if (productInv != null) {
                double currentClose = productInv.getCloseStock() != null ? productInv.getCloseStock() : 0.0;
                productInv.setCloseStock(currentClose - qty);
                // Recalculate sales
                double total = productInv.getTotalStock() != null ? productInv.getTotalStock() : 0.0;
                productInv.setSales(total - productInv.getCloseStock());
                productInventoryRepository.save(productInv);
            }

            // --- 4. CashierStock deduction (non-fuel products) ---
            if (invoiceProduct.getNozzle() == null || invoiceProduct.getNozzle().getId() == null) {
                Long scid = invoiceProduct.getInvoiceBill() != null && invoiceProduct.getInvoiceBill().getScid() != null
                        ? invoiceProduct.getInvoiceBill().getScid() : SecurityUtils.getScid();
                cashierStockRepository.findByProductIdAndScid(productId, scid).ifPresent(cashierStock -> {
                    double current = cashierStock.getCurrentStock() != null ? cashierStock.getCurrentStock() : 0.0;
                    cashierStock.setCurrentStock(Math.max(0, current - qty));
                    cashierStockRepository.save(cashierStock);
                });
            }
        }
    }

    public org.springframework.data.domain.Page<InvoiceBill> getInvoicesByCustomer(
            Long customerId, String billType, String paymentStatus,
            java.time.LocalDateTime fromDate, java.time.LocalDateTime toDate,
            org.springframework.data.domain.Pageable pageable) {

        boolean hasBillType = billType != null && !billType.isEmpty();
        boolean hasPaymentStatus = paymentStatus != null && !paymentStatus.isEmpty();
        boolean hasDateRange = fromDate != null && toDate != null;

        if (hasDateRange) {
            if (hasBillType && hasPaymentStatus) {
                return repository.findByCustomerIdAndBillTypeAndPaymentStatusAndDateRange(customerId, billType, paymentStatus, fromDate, toDate, pageable);
            } else if (hasBillType) {
                return repository.findByCustomerIdAndBillTypeAndDateRange(customerId, billType, fromDate, toDate, pageable);
            } else if (hasPaymentStatus) {
                return repository.findByCustomerIdAndPaymentStatusAndDateRange(customerId, paymentStatus, fromDate, toDate, pageable);
            } else {
                return repository.findByCustomerIdAndDateRange(customerId, fromDate, toDate, pageable);
            }
        } else {
            if (hasBillType && hasPaymentStatus) {
                return repository.findByCustomerIdAndBillTypeAndPaymentStatusOrderByDateDesc(customerId, billType, paymentStatus, pageable);
            } else if (hasBillType) {
                return repository.findByCustomerIdAndBillTypeOrderByDateDesc(customerId, billType, pageable);
            } else if (hasPaymentStatus) {
                return repository.findByCustomerIdAndPaymentStatusOrderByDateDesc(customerId, paymentStatus, pageable);
            } else {
                return repository.findByCustomerIdOrderByDateDesc(customerId, pageable);
            }
        }
    }

    public Page<InvoiceBill> getInvoiceHistory(String billType, String paymentStatus, String categoryType,
            LocalDateTime fromDate, LocalDateTime toDate, String search, Pageable pageable) {
        String bt = (billType != null && !billType.isEmpty()) ? billType : null;
        String ps = (paymentStatus != null && !paymentStatus.isEmpty()) ? paymentStatus : null;
        String ct = (categoryType != null && !categoryType.isEmpty()) ? categoryType : null;
        String s = (search != null && !search.isEmpty()) ? search : "";
        LocalDateTime fd = fromDate != null ? fromDate : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime td = toDate != null ? toDate : LocalDateTime.of(2099, 12, 31, 23, 59, 59);
        return repository.findAllFiltered(bt, ps, ct, fd, td, s, pageable);
    }

    public List<ProductSalesSummary> getProductSalesSummary(String billType, String paymentStatus, String categoryType,
            LocalDateTime fromDate, LocalDateTime toDate) {
        String bt = (billType != null && !billType.isEmpty()) ? billType : null;
        String ps = (paymentStatus != null && !paymentStatus.isEmpty()) ? paymentStatus : null;
        String ct = (categoryType != null && !categoryType.isEmpty()) ? categoryType : null;
        LocalDateTime fd = fromDate != null ? fromDate : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime td = toDate != null ? toDate : LocalDateTime.of(2099, 12, 31, 23, 59, 59);
        return repository.getProductSalesSummary(bt, ps, ct, fd, td);
    }

    @Transactional
    public InvoiceBill updateInvoice(Long id, InvoiceBill updated) {
        InvoiceBill existing = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found with id: " + id));

        // Update basic fields
        existing.setDriverName(updated.getDriverName());
        existing.setDriverPhone(updated.getDriverPhone());
        existing.setIndentNo(updated.getIndentNo());
        existing.setPaymentMode(updated.getPaymentMode());
        existing.setVehicleKM(updated.getVehicleKM());
        existing.setBillDesc(updated.getBillDesc());

        // Update billType, customer, vehicle if provided
        if (updated.getBillType() != null) {
            existing.setBillType(updated.getBillType());
        }
        if (updated.getCustomer() != null) {
            existing.setCustomer(updated.getCustomer());
        }
        if (updated.getVehicle() != null) {
            existing.setVehicle(updated.getVehicle());
        }

        // Update products — clear old and add new
        existing.getProducts().clear();

        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalDiscountSum = BigDecimal.ZERO;

        if (updated.getProducts() != null) {
            for (InvoiceProduct ip : updated.getProducts()) {
                ip.setInvoiceBill(existing);

                BigDecimal qty = ip.getQuantity() != null ? ip.getQuantity() : BigDecimal.ZERO;
                BigDecimal price = ip.getUnitPrice() != null ? ip.getUnitPrice() : BigDecimal.ZERO;
                BigDecimal gross = qty.multiply(price);
                ip.setGrossAmount(gross);

                if (ip.getDiscountRate() != null && ip.getDiscountRate().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal discAmt = ip.getDiscountRate().multiply(qty);
                    ip.setDiscountAmount(discAmt);
                    ip.setAmount(gross.subtract(discAmt));
                } else {
                    ip.setDiscountRate(null);
                    ip.setDiscountAmount(null);
                    ip.setAmount(gross);
                }

                totalGross = totalGross.add(gross);
                if (ip.getDiscountAmount() != null) {
                    totalDiscountSum = totalDiscountSum.add(ip.getDiscountAmount());
                }

                existing.getProducts().add(ip);
            }
        }

        existing.setGrossAmount(totalGross);
        existing.setTotalDiscount(totalDiscountSum);
        existing.setNetAmount(totalGross.subtract(totalDiscountSum));

        return repository.save(existing);
    }

    public InvoiceBill getInvoiceById(Long id) {
        return repository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("Invoice not found with id: " + id));
    }

    public void deleteInvoice(Long id) {
        repository.deleteById(id);
    }

    // --- File Uploads to S3 ---

    public InvoiceBill uploadFile(Long id, String type, MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        InvoiceBill invoice = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found with id: " + id));

        // Build S3 key using invoice date
        LocalDateTime invoiceDate = invoice.getDate() != null ? invoice.getDate() : LocalDateTime.now();
        String ext = getExtension(file.getOriginalFilename());
        String key = String.format("invoices/%d/%02d/%02d/%d/%s.%s",
                invoiceDate.getYear(), invoiceDate.getMonthValue(), invoiceDate.getDayOfMonth(),
                id, type, ext);

        // Delete old file if exists
        String oldKey = getFileKey(invoice, type);
        if (oldKey != null && !oldKey.isEmpty()) {
            try { s3StorageService.delete(oldKey); } catch (Exception ignored) {}
        }

        // Upload new file
        s3StorageService.upload(key, file);

        // Save key to entity
        switch (type) {
            case "bill-pic": invoice.setBillPic(key); break;
            case "pump-bill-pic": invoice.setPumpBillPic(key); break;
            case "indent-pic": invoice.setIndentPic(key); break;
            default: throw new IllegalArgumentException("Invalid file type: " + type);
        }

        return repository.save(invoice);
    }

    public String getFilePresignedUrl(Long id, String type) {
        InvoiceBill invoice = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found with id: " + id));
        String key = getFileKey(invoice, type);
        if (key == null || key.isEmpty()) {
            throw new ResourceNotFoundException("No file uploaded for type: " + type);
        }
        return s3StorageService.getPresignedUrl(key);
    }

    private String getFileKey(InvoiceBill invoice, String type) {
        switch (type) {
            case "bill-pic": return invoice.getBillPic();
            case "pump-bill-pic": return invoice.getPumpBillPic();
            case "indent-pic": return invoice.getIndentPic();
            default: return null;
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "bin";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
