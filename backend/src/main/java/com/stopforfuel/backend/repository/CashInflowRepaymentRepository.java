package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.CashInflowRepayment;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CashInflowRepaymentRepository extends ScidRepository<CashInflowRepayment> {
    List<CashInflowRepayment> findByCashInflowIdOrderByRepaymentDateDesc(Long cashInflowId);
    List<CashInflowRepayment> findByShiftIdOrderByRepaymentDateDesc(Long shiftId);
}
