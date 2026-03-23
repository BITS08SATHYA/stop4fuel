package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.StockTransfer;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StockTransferRepository extends ScidRepository<StockTransfer> {
    List<StockTransfer> findByScidOrderByTransferDateDesc(Long scid);
    List<StockTransfer> findByProductId(Long productId);
    List<StockTransfer> findByScidAndTransferDateBetweenOrderByTransferDateDesc(Long scid, LocalDateTime from, LocalDateTime to);
    List<StockTransfer> findByShiftId(Long shiftId);
}
