package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.CashierStock;
import com.stopforfuel.backend.entity.GodownStock;
import com.stopforfuel.backend.entity.StockTransfer;
import com.stopforfuel.backend.repository.CashierStockRepository;
import com.stopforfuel.backend.repository.GodownStockRepository;
import com.stopforfuel.backend.repository.StockTransferRepository;
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

    public List<StockTransfer> getAll() {
        return repository.findByScidOrderByTransferDateDesc(1L);
    }

    public List<StockTransfer> getByProduct(Long productId) {
        return repository.findByProductId(productId);
    }

    public List<StockTransfer> getByDateRange(LocalDateTime from, LocalDateTime to) {
        return repository.findByScidAndTransferDateBetweenOrderByTransferDateDesc(1L, from, to);
    }

    @Transactional
    public StockTransfer createTransfer(StockTransfer transfer) {
        if (transfer.getScid() == null) transfer.setScid(1L);
        if (transfer.getTransferDate() == null) transfer.setTransferDate(LocalDateTime.now());

        Long productId = transfer.getProduct().getId();
        Double qty = transfer.getQuantity();

        if ("GODOWN".equals(transfer.getFromLocation())) {
            // Godown -> Cashier
            GodownStock godown = godownStockRepository.findByProductIdAndScid(productId, 1L)
                    .orElseThrow(() -> new RuntimeException("No godown stock found for product: " + productId));
            if (godown.getCurrentStock() < qty) {
                throw new RuntimeException("Insufficient godown stock. Available: " + godown.getCurrentStock() + ", Requested: " + qty);
            }
            godown.setCurrentStock(godown.getCurrentStock() - qty);
            godownStockRepository.save(godown);

            CashierStock cashier = cashierStockRepository.findByProductIdAndScid(productId, 1L)
                    .orElseGet(() -> {
                        CashierStock cs = new CashierStock();
                        cs.setProduct(transfer.getProduct());
                        cs.setCurrentStock(0.0);
                        cs.setMaxCapacity(0.0);
                        cs.setScid(1L);
                        return cs;
                    });
            cashier.setCurrentStock(cashier.getCurrentStock() + qty);
            cashierStockRepository.save(cashier);
        } else {
            // Cashier -> Godown
            CashierStock cashier = cashierStockRepository.findByProductIdAndScid(productId, 1L)
                    .orElseThrow(() -> new RuntimeException("No cashier stock found for product: " + productId));
            if (cashier.getCurrentStock() < qty) {
                throw new RuntimeException("Insufficient cashier stock. Available: " + cashier.getCurrentStock() + ", Requested: " + qty);
            }
            cashier.setCurrentStock(cashier.getCurrentStock() - qty);
            cashierStockRepository.save(cashier);

            GodownStock godown = godownStockRepository.findByProductIdAndScid(productId, 1L)
                    .orElseGet(() -> {
                        GodownStock gs = new GodownStock();
                        gs.setProduct(transfer.getProduct());
                        gs.setCurrentStock(0.0);
                        gs.setReorderLevel(0.0);
                        gs.setMaxStock(0.0);
                        gs.setScid(1L);
                        return gs;
                    });
            godown.setCurrentStock(godown.getCurrentStock() + qty);
            godownStockRepository.save(godown);
        }

        return repository.save(transfer);
    }
}
