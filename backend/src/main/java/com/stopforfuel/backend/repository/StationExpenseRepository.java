package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.StationExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface StationExpenseRepository extends JpaRepository<StationExpense, Long> {
    List<StationExpense> findAllByOrderByExpenseDateDesc();
    List<StationExpense> findByExpenseDateBetweenOrderByExpenseDateDesc(LocalDate from, LocalDate to);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM StationExpense e WHERE e.expenseDate BETWEEN :from AND :to")
    Double sumAmountBetween(LocalDate from, LocalDate to);
}
