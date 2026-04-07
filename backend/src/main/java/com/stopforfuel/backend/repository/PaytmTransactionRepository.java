package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.PaytmTransaction;
import com.stopforfuel.backend.enums.PaytmTxnStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaytmTransactionRepository extends JpaRepository<PaytmTransaction, Long> {

    Optional<PaytmTransaction> findByMerchantTxnId(String merchantTxnId);

    Optional<PaytmTransaction> findByCpayId(String cpayId);

    List<PaytmTransaction> findByInvoiceBillIdAndStatus(Long invoiceBillId, PaytmTxnStatus status);

    List<PaytmTransaction> findByStatementIdAndStatus(Long statementId, PaytmTxnStatus status);

    List<PaytmTransaction> findByStatusAndCreatedAtBefore(PaytmTxnStatus status, LocalDateTime cutoff);
}
