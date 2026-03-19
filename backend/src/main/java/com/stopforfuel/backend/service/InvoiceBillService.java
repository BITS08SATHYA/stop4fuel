package com.stopforfuel.backend.service;

import com.stopforfuel.backend.dto.ProductSalesSummary;
import com.stopforfuel.backend.entity.*;
import com.stopforfuel.backend.entity.transaction.*;
import com.stopforfuel.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InvoiceBillService {

    private final InvoiceBillRepository repository;
    private final CustomerRepository customerRepository;
    private final VehicleRepository vehicleRepository;
    private final TankInventoryRepository tankInventoryRepository;
    private final NozzleInventoryRepository nozzleInventoryRepository;
    private final ProductInventoryRepository productInventoryRepository;
    private final com.stopforfuel.backend.repository.NozzleRepository nozzleRepository;
    private final ProductRepository productRepository;
    private final CustomerService customerService;
    private final IncentiveService incentiveService;
    private final ShiftService shiftService;
    private final ShiftTransactionService shiftTransactionService;
    private final BillSequenceService billSequenceService;

    public List<InvoiceBill> getAllInvoices() {
        return repository.findAll();
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
                throw new RuntimeException(
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
                throw new RuntimeException(
                        "Cannot create invoice: Vehicle '" + vehicle.getVehicleNumber() +
                        "' is " + vehicle.getStatus() +
                        (vehicle.isBlocked() ? " (liter limit exceeded)" : " (manually disabled)"));
            }
            invoice.setVehicle(vehicle);
        }

        // --- Validate all products are active and have sufficient inventory ---
        if (invoice.getProducts() != null) {
            for (InvoiceProduct ip : invoice.getProducts()) {
                if (ip.getProduct() != null && ip.getProduct().getId() != null) {
                    Product prod = productRepository.findById(ip.getProduct().getId())
                            .orElseThrow(() -> new RuntimeException("Product not found"));
                    if (!prod.isActive()) {
                        throw new RuntimeException(
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
                                    throw new RuntimeException(
                                            "Cannot create invoice: No inventory record found for tank linked to product '"
                                            + prod.getName() + "'.");
                                }
                                double available = tankInv.getCloseStock() != null ? tankInv.getCloseStock() : 0.0;
                                if (available <= 0) {
                                    throw new RuntimeException(
                                            "Cannot create invoice: Tank stock for product '" + prod.getName()
                                            + "' is empty (0 liters available).");
                                }
                                if (available < requiredQty.doubleValue()) {
                                    throw new RuntimeException(
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
                                throw new RuntimeException(
                                        "Cannot create invoice: Product '" + prod.getName()
                                        + "' is out of stock (0 available).");
                            }
                            if (available < requiredQty.doubleValue()) {
                                throw new RuntimeException(
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
            invoice.setScid(1L);
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

        return saved;
    }

    /**
     * Auto-creates a shift transaction entry when a CASH invoice is created.
     * Maps the invoice payment mode to the appropriate transaction type.
     */
    private void autoCreateShiftTransaction(InvoiceBill invoice) {
        if (!"CASH".equals(invoice.getBillType())) {
            return; // Only auto-create for cash invoices
        }

        Shift activeShift = shiftService.getActiveShift();
        if (activeShift == null) {
            return; // No open shift, skip auto-creation
        }

        BigDecimal amount = invoice.getNetAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        String paymentMode = invoice.getPaymentMode();
        if (paymentMode == null) paymentMode = "CASH";

        String customerName = invoice.getCustomer() != null ? invoice.getCustomer().getName() : null;
        String remark = "Auto: Invoice #" + invoice.getId()
                + (customerName != null ? " - " + customerName : "");

        ShiftTransaction txn;
        switch (paymentMode.toUpperCase()) {
            case "UPI":
                UpiTransaction upiTxn = new UpiTransaction();
                upiTxn.setReceivedAmount(amount);
                upiTxn.setRemarks(remark);
                txn = upiTxn;
                break;
            case "CARD":
                CardTransaction cardTxn = new CardTransaction();
                cardTxn.setReceivedAmount(amount);
                cardTxn.setRemarks(remark);
                if (customerName != null) cardTxn.setCustomerName(customerName);
                txn = cardTxn;
                break;
            case "CHEQUE":
                ChequeTransaction chequeTxn = new ChequeTransaction();
                chequeTxn.setReceivedAmount(amount);
                chequeTxn.setRemarks(remark);
                txn = chequeTxn;
                break;
            case "BANK TRANSFER":
            case "BANK":
                BankTransaction bankTxn = new BankTransaction();
                bankTxn.setReceivedAmount(amount);
                bankTxn.setRemarks(remark);
                txn = bankTxn;
                break;
            case "CCMS":
                CcmsTransaction ccmsTxn = new CcmsTransaction();
                ccmsTxn.setReceivedAmount(amount);
                ccmsTxn.setRemarks(remark);
                txn = ccmsTxn;
                break;
            default:
                CashTransaction cashTxn = new CashTransaction();
                cashTxn.setReceivedAmount(amount);
                cashTxn.setRemarks(remark);
                txn = cashTxn;
                break;
        }

        txn.setShiftId(activeShift.getId());
        txn.setScid(invoice.getScid());
        shiftTransactionService.create(txn);
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

    public Page<InvoiceBill> getInvoiceHistory(String billType, String paymentStatus,
            LocalDateTime fromDate, LocalDateTime toDate, String search, Pageable pageable) {
        String bt = (billType != null && !billType.isEmpty()) ? billType : null;
        String ps = (paymentStatus != null && !paymentStatus.isEmpty()) ? paymentStatus : null;
        String s = (search != null && !search.isEmpty()) ? search : "";
        LocalDateTime fd = fromDate != null ? fromDate : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime td = toDate != null ? toDate : LocalDateTime.of(2099, 12, 31, 23, 59, 59);
        return repository.findAllFiltered(bt, ps, fd, td, s, pageable);
    }

    public List<ProductSalesSummary> getProductSalesSummary(String billType, String paymentStatus,
            LocalDateTime fromDate, LocalDateTime toDate) {
        String bt = (billType != null && !billType.isEmpty()) ? billType : null;
        String ps = (paymentStatus != null && !paymentStatus.isEmpty()) ? paymentStatus : null;
        LocalDateTime fd = fromDate != null ? fromDate : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime td = toDate != null ? toDate : LocalDateTime.of(2099, 12, 31, 23, 59, 59);
        return repository.getProductSalesSummary(bt, ps, fd, td);
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
        return repository.findById(id).orElseThrow(() -> new RuntimeException("Invoice not found with id: " + id));
    }

    public void deleteInvoice(Long id) {
        repository.deleteById(id);
    }
}
