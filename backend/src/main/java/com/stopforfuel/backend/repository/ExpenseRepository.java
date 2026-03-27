package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Expense;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ExpenseRepository extends ScidRepository<Expense> {
    List<Expense> findByShiftIdOrderByExpenseDateDesc(Long shiftId);
    List<Expense> findAllByOrderByExpenseDateDesc();

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.shiftId = :shiftId")
    BigDecimal sumByShift(@Param("shiftId") Long shiftId);
}
