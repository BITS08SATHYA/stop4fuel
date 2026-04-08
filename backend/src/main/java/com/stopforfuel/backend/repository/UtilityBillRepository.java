package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.UtilityBill;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface UtilityBillRepository extends ScidRepository<UtilityBill> {
    List<UtilityBill> findByBillTypeAndScidOrderByBillDateDesc(String billType, Long scid);
    List<UtilityBill> findByStatusAndScidOrderByDueDateAsc(String status, Long scid);
    List<UtilityBill> findAllByScidOrderByBillDateDesc(Long scid);
    List<UtilityBill> findByBillDateBetweenAndScidOrderByBillDateDesc(LocalDate from, LocalDate to, Long scid);
}
