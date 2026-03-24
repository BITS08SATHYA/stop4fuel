package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.CashierStock;
import com.stopforfuel.backend.entity.GodownStock;
import com.stopforfuel.backend.entity.ProductInventory;
import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.entity.StockTransfer;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.exception.ResourceNotFoundException;
import com.stopforfuel.backend.repository.CashierStockRepository;
import com.stopforfuel.backend.repository.GodownStockRepository;
import com.stopforfuel.backend.repository.ProductInventoryRepository;
import com.stopforfuel.backend.repository.StockTransferRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StockTransferService {

    private final StockTransferRepository repository;
    private final GodownStockRepository godownStockRepository;
    private final CashierStockRepository cashierStockRepository;
    private final ProductInventoryRepository productInventoryRepository;
    private final ShiftService shiftService;

    public List<StockTransfer> getAll() {
        return repository.findByScidOrderByTransferDateDesc(SecurityUtils.getScid());
    }

    public List<StockTransfer> getByProduct(Long productId) {
        return repository.findByProductId(productId);
    }

    public List<StockTransfer> getByDateRange(LocalDateTime from, LocalDateTime to) {
        return repository.findByScidAndTransferDateBetweenOrderByTransferDateDesc(SecurityUtils.getScid(), from, to);
    }

    public List<StockTransfer> getByShiftId(Long shiftId) {
        return repository.findByShiftId(shiftId);
    }

    @Transactional
    public StockTransfer createTransfer(StockTransfer transfer) {
        if (transfer.getScid() == null) transfer.setScid(SecurityUtils.getScid());
        if (transfer.getTransferDate() == null) transfer.setTransferDate(LocalDateTime.now());

        // Link transfer to active shift
        Shift activeShift = shiftService.getActiveShift();
        if (activeShift != null) {
            transfer.setShiftId(activeShift.getId());
        }

        Long productId = transfer.getProduct().getId();
        Double qty = transfer.getQuantity();

        if ("GODOWN".equals(transfer.getFromLocation())) {
            // Godown -> Cashier
            GodownStock godown = godownStockRepository.findByProductIdAndScid(productId, SecurityUtils.getScid())
                    .orElseThrow(() -> new ResourceNotFoundException("No godown stock found for product: " + productId));
            if (godown.getCurrentStock() < qty) {
                throw new BusinessException("Insufficient godown stock. Available: " + godown.getCurrentStock() + ", Requested: " + qty);
            }
            godown.setCurrentStock(godown.getCurrentStock() - qty);
            godownStockRepository.save(godown);

            CashierStock cashier = cashierStockRepository.findByProductIdAndScid(productId, SecurityUtils.getScid())
                    .orElseGet(() -> {
                        CashierStock cs = new CashierStock();
                        cs.setProduct(transfer.getProduct());
                        cs.setCurrentStock(0.0);
                        cs.setMaxCapacity(0.0);
                        cs.setScid(SecurityUtils.getScid());
                        return cs;
                    });
            cashier.setCurrentStock(cashier.getCurrentStock() + qty);
            cashierStockRepository.save(cashier);

            // Auto-update ProductInventory.incomeStock for the active shift
            if (activeShift != null) {
                updateProductInventoryIncome(activeShift.getId(), productId, qty);
            }
        } else {
            // Cashier -> Godown
            CashierStock cashier = cashierStockRepository.findByProductIdAndScid(productId, SecurityUtils.getScid())
                    .orElseThrow(() -> new ResourceNotFoundException("No cashier stock found for product: " + productId));
            if (cashier.getCurrentStock() < qty) {
                throw new BusinessException("Insufficient cashier stock. Available: " + cashier.getCurrentStock() + ", Requested: " + qty);
            }
            cashier.setCurrentStock(cashier.getCurrentStock() - qty);
            cashierStockRepository.save(cashier);

            GodownStock godown = godownStockRepository.findByProductIdAndScid(productId, SecurityUtils.getScid())
                    .orElseGet(() -> {
                        GodownStock gs = new GodownStock();
                        gs.setProduct(transfer.getProduct());
                        gs.setCurrentStock(0.0);
                        gs.setReorderLevel(0.0);
                        gs.setMaxStock(0.0);
                        gs.setScid(SecurityUtils.getScid());
                        return gs;
                    });
            godown.setCurrentStock(godown.getCurrentStock() + qty);
            godownStockRepository.save(godown);

            // Cashier -> Godown means stock is leaving the counter, reduce incomeStock
            if (activeShift != null) {
                updateProductInventoryIncome(activeShift.getId(), productId, -qty);
            }
        }

        return repository.save(transfer);
    }

    /**
     * Auto-update ProductInventory.incomeStock when a stock transfer happens.
     * Positive qty = stock received at counter (GODOWN→CASHIER).
     * Negative qty = stock returned to godown (CASHIER→GODOWN).
     */
    private void updateProductInventoryIncome(Long shiftId, Long productId, double qty) {
        ProductInventory pi = productInventoryRepository.findTopByShiftIdAndProductIdOrderByIdDesc(shiftId, productId);
        if (pi != null) {
            double currentIncome = pi.getIncomeStock() != null ? pi.getIncomeStock() : 0.0;
            pi.setIncomeStock(currentIncome + qty);

            // Recalculate derived fields
            double open = pi.getOpenStock() != null ? pi.getOpenStock() : 0.0;
            double total = open + pi.getIncomeStock();
            pi.setTotalStock(total);
            if (pi.getCloseStock() != null) {
                pi.setSales(total - pi.getCloseStock());
            }
            productInventoryRepository.save(pi);
        }
    }
}
