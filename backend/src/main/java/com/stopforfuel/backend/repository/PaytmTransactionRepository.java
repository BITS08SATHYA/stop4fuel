package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.PaytmTransaction;
import com.stopforfuel.backend.enums.ReconStatus;
import com.stopforfuel.backend.enums.SettlementStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaytmTransactionRepository extends ScidRepository<PaytmTransaction> {

    Optional<PaytmTransaction> findByScidAndPaytmOrderId(Long scid, String paytmOrderId);

    boolean existsByScidAndPaytmOrderId(Long scid, String paytmOrderId);

    @Query("SELECT t FROM PaytmTransaction t WHERE t.scid = :scid " +
           "AND t.txnDate BETWEEN :from AND :to ORDER BY t.txnDate DESC")
    List<PaytmTransaction> findByDateRange(@Param("scid") Long scid,
                                           @Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to);

    @Query("SELECT t FROM PaytmTransaction t WHERE t.scid = :scid " +
           "AND t.reconStatus = :status ORDER BY t.txnDate DESC")
    List<PaytmTransaction> findByReconStatus(@Param("scid") Long scid,
                                             @Param("status") ReconStatus status);

    @Query("SELECT t FROM PaytmTransaction t WHERE t.scid = :scid " +
           "AND t.settlementStatus = :status ORDER BY t.txnDate DESC")
    List<PaytmTransaction> findBySettlementStatus(@Param("scid") Long scid,
                                                  @Param("status") SettlementStatus status);

    @Query("SELECT COALESCE(SUM(t.txnAmount), 0) FROM PaytmTransaction t " +
           "WHERE t.scid = :scid AND t.txnDate BETWEEN :from AND :to " +
           "AND t.txnStatus = 'TXN_SUCCESS'")
    BigDecimal sumSuccessfulByDateRange(@Param("scid") Long scid,
                                       @Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(t) FROM PaytmTransaction t WHERE t.scid = :scid AND t.reconStatus = :status")
    long countByScidAndReconStatus(@Param("scid") Long scid, @Param("status") ReconStatus status);

    @Query("SELECT COALESCE(SUM(t.settlementAmount), 0) FROM PaytmTransaction t " +
           "WHERE t.scid = :scid AND t.txnDate BETWEEN :from AND :to " +
           "AND t.settlementStatus = 'SETTLED'")
    BigDecimal sumSettledByDateRange(@Param("scid") Long scid,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(t.txnAmount), 0) FROM PaytmTransaction t " +
           "WHERE t.scid = :scid AND t.txnDate BETWEEN :from AND :to " +
           "AND t.txnStatus = 'TXN_SUCCESS' AND t.settlementStatus = 'PENDING'")
    BigDecimal sumPendingSettlementByDateRange(@Param("scid") Long scid,
                                              @Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to);

    Optional<PaytmTransaction> findByMatchedInvoiceId(Long invoiceId);
}
