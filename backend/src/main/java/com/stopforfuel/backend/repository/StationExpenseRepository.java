package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.StationExpense;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface StationExpenseRepository extends ScidRepository<StationExpense> {
    List<StationExpense> findAllByScidOrderByExpenseDateDesc(Long scid);
    // shiftId is already tenant-scoped via shift creation
    List<StationExpense> findByShiftIdOrderByIdDesc(Long shiftId);
    List<StationExpense> findByExpenseDateBetweenAndScidOrderByExpenseDateDesc(LocalDate from, LocalDate to, Long scid);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM StationExpense e WHERE e.expenseDate BETWEEN :from AND :to AND e.scid = :scid")
    Double sumAmountBetween(LocalDate from, LocalDate to, @org.springframework.data.repository.query.Param("scid") Long scid);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM StationExpense e WHERE e.shiftId = :shiftId")
    Double sumAmountByShift(Long shiftId);
}
