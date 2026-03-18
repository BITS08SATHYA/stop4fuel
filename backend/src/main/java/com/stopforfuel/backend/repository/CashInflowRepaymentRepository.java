package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.CashInflowRepayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CashInflowRepaymentRepository extends JpaRepository<CashInflowRepayment, Long> {
    List<CashInflowRepayment> findByCashInflowIdOrderByRepaymentDateDesc(Long cashInflowId);
    List<CashInflowRepayment> findByShiftIdOrderByRepaymentDateDesc(Long shiftId);
}
