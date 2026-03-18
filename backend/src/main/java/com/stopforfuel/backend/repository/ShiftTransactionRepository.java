package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.transaction.ShiftTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ShiftTransactionRepository extends JpaRepository<ShiftTransaction, Long> {
    List<ShiftTransaction> findByShiftId(Long shiftId);

    @Query("SELECT COALESCE(SUM(st.receivedAmount), 0) FROM ShiftTransaction st WHERE st.shiftId = :shiftId AND TYPE(st) = com.stopforfuel.backend.entity.transaction.CashTransaction")
    BigDecimal sumCashByShift(@Param("shiftId") Long shiftId);

    @Query("SELECT COALESCE(SUM(st.receivedAmount), 0) FROM ShiftTransaction st WHERE st.shiftId = :shiftId AND TYPE(st) = com.stopforfuel.backend.entity.transaction.UpiTransaction")
    BigDecimal sumUpiByShift(@Param("shiftId") Long shiftId);

    @Query("SELECT COALESCE(SUM(st.receivedAmount), 0) FROM ShiftTransaction st WHERE st.shiftId = :shiftId AND TYPE(st) = com.stopforfuel.backend.entity.transaction.CardTransaction")
    BigDecimal sumCardByShift(@Param("shiftId") Long shiftId);

    @Query("SELECT COALESCE(SUM(st.receivedAmount), 0) FROM ShiftTransaction st WHERE st.shiftId = :shiftId")
    BigDecimal sumAllByShift(@Param("shiftId") Long shiftId);

    @Query("SELECT COALESCE(SUM(st.receivedAmount), 0) FROM ShiftTransaction st WHERE st.shiftId = :shiftId AND TYPE(st) = com.stopforfuel.backend.entity.transaction.ExpenseTransaction")
    BigDecimal sumExpenseByShift(@Param("shiftId") Long shiftId);

    @Query("SELECT COALESCE(SUM(st.receivedAmount), 0) FROM ShiftTransaction st WHERE st.shiftId = :shiftId AND TYPE(st) = com.stopforfuel.backend.entity.transaction.ChequeTransaction")
    BigDecimal sumChequeByShift(@Param("shiftId") Long shiftId);

    @Query("SELECT COALESCE(SUM(st.receivedAmount), 0) FROM ShiftTransaction st WHERE st.shiftId = :shiftId AND TYPE(st) = com.stopforfuel.backend.entity.transaction.BankTransaction")
    BigDecimal sumBankByShift(@Param("shiftId") Long shiftId);

    @Query("SELECT COALESCE(SUM(st.receivedAmount), 0) FROM ShiftTransaction st WHERE st.shiftId = :shiftId AND TYPE(st) = com.stopforfuel.backend.entity.transaction.CcmsTransaction")
    BigDecimal sumCcmsByShift(@Param("shiftId") Long shiftId);

    @Query("SELECT COALESCE(SUM(st.receivedAmount), 0) FROM ShiftTransaction st WHERE st.shiftId = :shiftId AND TYPE(st) = com.stopforfuel.backend.entity.transaction.NightCashTransaction")
    BigDecimal sumNightCashByShift(@Param("shiftId") Long shiftId);

    void deleteByShiftId(Long shiftId);
}
