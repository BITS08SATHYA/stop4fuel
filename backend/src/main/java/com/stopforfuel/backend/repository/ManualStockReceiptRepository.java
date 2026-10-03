package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.ManualStockReceipt;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ManualStockReceiptRepository extends ScidRepository<ManualStockReceipt> {

    List<ManualStockReceipt> findByShiftIdOrderByIdAsc(Long shiftId);
}
