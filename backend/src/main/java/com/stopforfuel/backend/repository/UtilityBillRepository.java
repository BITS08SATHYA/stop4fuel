package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.UtilityBill;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface UtilityBillRepository extends ScidRepository<UtilityBill> {
    List<UtilityBill> findByBillTypeOrderByBillDateDesc(String billType);
    List<UtilityBill> findByStatusOrderByDueDateAsc(String status);
    List<UtilityBill> findAllByOrderByBillDateDesc();
    List<UtilityBill> findByBillDateBetweenOrderByBillDateDesc(LocalDate from, LocalDate to);
}
