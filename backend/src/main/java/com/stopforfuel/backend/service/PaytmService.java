package com.stopforfuel.backend.service;

import com.stopforfuel.backend.config.PaytmConfig;
import com.stopforfuel.backend.entity.*;
import com.stopforfuel.backend.enums.PaymentMode;
import com.stopforfuel.backend.enums.PaymentStatus;
import com.stopforfuel.backend.enums.PaytmTxnStatus;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.exception.ResourceNotFoundException;
import com.stopforfuel.backend.repository.*;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaytmService {

    private final PaytmConfig paytmConfig;
    private final PaytmTransactionRepository paytmTransactionRepository;
    private final InvoiceBillRepository invoiceBillRepository;
    private final StatementRepository statementRepository;
    private final UserRepository userRepository;
    private final PaymentService paymentService;
    private final InvoiceBillService invoiceBillService;
    private final ShiftService shiftService;
    private final WebClient paytmWebClient;

    private static final DateTimeFormatter PAYTM_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Initiate a payment request to Paytm POS terminal.
     */
    @Transactional
    public Map<String, Object> initiatePayment(BigDecimal amount, Long invoiceBillId, Long statementId, String txnType) {
        if (!paytmConfig.isEnabled()) {
            throw new BusinessException("Paytm POS integration is not enabled");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Amount must be greater than zero");
        }

        // Generate unique transaction ID
        String merchantTxnId = "SFF-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        String amountInPaisa = amount.multiply(BigDecimal.valueOf(100)).setScale(0).toPlainString();

        // Build Paytm API request body
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("merchantTxnId", merchantTxnId);
        body.put("txnAmount", amountInPaisa);
        body.put("txnDate", LocalDateTime.now().format(PAYTM_DATE_FMT));
        body.put("mid", paytmConfig.getMid());
        body.put("storeId", paytmConfig.getStoreId());

        // Compute reqHash
        String reqHash = computeReqHash(body);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("head", Map.of("clientId", paytmConfig.getClientId(), "reqHash", reqHash));
        requestBody.put("body", body);

        // Call Paytm Payment Request API via WebClient
        String cpayId = null;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = paytmWebClient.post()
                    .uri("/edc-integration-service/payment/request")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("clientId", paytmConfig.getClientId())
                    .header("reqHash", reqHash)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseBody = (Map<String, Object>) response.get("body");
                if (responseBody != null) {
                    cpayId = String.valueOf(responseBody.get("cpayId"));
                    String resultStatus = String.valueOf(responseBody.get("resultStatus"));
                    if (!"SUCCESS".equalsIgnoreCase(resultStatus)) {
                        String msg = String.valueOf(responseBody.getOrDefault("resultMsg", "Unknown error"));
                        throw new BusinessException("Paytm payment request failed: " + msg);
                    }
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Paytm API call failed", e);
            throw new BusinessException("Failed to connect to Paytm POS: " + e.getMessage());
        }

        // Create PaytmTransaction record
        PaytmTransaction txn = new PaytmTransaction();
        txn.setMerchantTxnId(merchantTxnId);
        txn.setCpayId(cpayId);
        txn.setAmount(amount);
        txn.setAmountInPaisa(amountInPaisa);
        txn.setStatus(PaytmTxnStatus.INITIATED);
        txn.setTxnType(txnType);
        txn.setScid(SecurityUtils.getScid());

        Shift activeShift = shiftService.getActiveShift();
        if (activeShift != null) {
            txn.setShiftId(activeShift.getId());
        }

        // Resolve initiatedBy
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId != null) {
            userRepository.findById(userId).ifPresent(txn::setInitiatedBy);
        }

        // Link to invoice or statement
        if (invoiceBillId != null) {
            InvoiceBill bill = invoiceBillRepository.findById(invoiceBillId)
                    .orElseThrow(() -> new ResourceNotFoundException("Invoice bill not found"));
            txn.setInvoiceBill(bill);
            txn.setCustomer(bill.getCustomer());
        }
        if (statementId != null) {
            Statement stmt = statementRepository.findById(statementId)
                    .orElseThrow(() -> new ResourceNotFoundException("Statement not found"));
            txn.setStatement(stmt);
            txn.setCustomer(stmt.getCustomer());
        }

        paytmTransactionRepository.save(txn);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("merchantTxnId", merchantTxnId);
        result.put("cpayId", cpayId);
        result.put("status", "INITIATED");
        return result;
    }

    /**
     * Handle callback from Paytm after payment completion.
     */
    @Transactional
    public Map<String, Object> handleCallback(Map<String, String> params) {
        log.info("Paytm callback received: {}", params);

        // Verify checksum
        String checksumHash = params.get("CHECKSUMHASH");
        if (checksumHash != null && paytmConfig.isEnabled()) {
            if (!verifyChecksum(params)) {
                log.error("Paytm callback checksum verification failed");
                throw new BusinessException("Checksum verification failed");
            }
        }

        String orderId = params.get("ORDERID") != null ? params.get("ORDERID") : params.get("merchantTxnId");

        PaytmTransaction txn = paytmTransactionRepository.findByMerchantTxnId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found for orderId: " + orderId));

        if (txn.getStatus() != PaytmTxnStatus.INITIATED) {
            log.warn("Duplicate callback for transaction {}, current status: {}", orderId, txn.getStatus());
            return Map.of("status", txn.getStatus().name());
        }

        // Update transaction with callback data
        txn.setPaytmTxnId(params.get("TXNID"));
        txn.setBankTxnId(params.get("BANKTXNID"));
        txn.setPaytmPaymentMode(params.get("PAYMENTMODE"));
        txn.setPaytmStatus(params.get("STATUS"));
        txn.setPaytmRespCode(params.get("RESPCODE"));
        txn.setPaytmRespMsg(params.get("RESPMSG"));
        txn.setCallbackReceivedAt(LocalDateTime.now());

        String status = params.get("STATUS");

        if ("TXN_SUCCESS".equals(status)) {
            txn.setStatus(PaytmTxnStatus.SUCCESS);
            paytmTransactionRepository.save(txn);
            processSuccessfulPayment(txn);
        } else {
            txn.setStatus(PaytmTxnStatus.FAILED);
            paytmTransactionRepository.save(txn);
        }

        return Map.of("status", txn.getStatus().name(), "merchantTxnId", txn.getMerchantTxnId());
    }

    /**
     * Process a successful Paytm payment — create Payment record and/or update invoice.
     */
    private void processSuccessfulPayment(PaytmTransaction txn) {
        if ("CASH_INVOICE".equals(txn.getTxnType())) {
            // Update invoice to PAID and create EAdvance
            InvoiceBill bill = txn.getInvoiceBill();
            if (bill != null) {
                bill.setPaymentStatus(PaymentStatus.PAID);
                invoiceBillRepository.save(bill);

                // Create EAdvance (shift transaction)
                invoiceBillService.createShiftTransactionForInvoice(bill);
            }
        } else if ("CREDIT_PAYMENT".equals(txn.getTxnType())) {
            // Create Payment record via existing service
            Payment payment = new Payment();
            payment.setAmount(txn.getAmount());
            payment.setPaymentMode(PaymentMode.PAYTM);
            payment.setReferenceNo(txn.getBankTxnId());
            payment.setRemarks("Paytm POS: " + txn.getPaytmPaymentMode()
                    + (txn.getPaytmTxnId() != null ? " | TxnID: " + txn.getPaytmTxnId() : ""));

            Payment saved;
            if (txn.getInvoiceBill() != null) {
                saved = paymentService.recordBillPayment(txn.getInvoiceBill().getId(), payment);
            } else if (txn.getStatement() != null) {
                saved = paymentService.recordStatementPayment(txn.getStatement().getId(), payment);
            } else {
                log.error("PaytmTransaction {} has no linked bill or statement", txn.getMerchantTxnId());
                return;
            }

            txn.setPayment(saved);
            paytmTransactionRepository.save(txn);
        }
    }

    /**
     * Check status of a Paytm transaction.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> checkStatus(String merchantTxnId) {
        PaytmTransaction txn = paytmTransactionRepository.findByMerchantTxnId(merchantTxnId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + merchantTxnId));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("merchantTxnId", txn.getMerchantTxnId());
        result.put("status", txn.getStatus().name());
        result.put("amount", txn.getAmount());
        result.put("paytmTxnId", txn.getPaytmTxnId());
        result.put("bankTxnId", txn.getBankTxnId());
        result.put("paymentMode", txn.getPaytmPaymentMode());
        result.put("respMsg", txn.getPaytmRespMsg());
        return result;
    }

    /**
     * Compute HMAC-SHA256 hash for Paytm API authentication.
     */
    private String computeReqHash(Map<String, Object> body) {
        try {
            StringBuilder sb = new StringBuilder();
            body.forEach((key, value) -> {
                if (sb.length() > 0) sb.append("|");
                sb.append(value);
            });
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    paytmConfig.getClientSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(sb.toString().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new BusinessException("Failed to compute request hash: " + e.getMessage());
        }
    }

    /**
     * Verify checksum from Paytm callback response.
     */
    private boolean verifyChecksum(Map<String, String> params) {
        try {
            TreeMap<String, String> sortedParams = new TreeMap<>(params);
            sortedParams.remove("CHECKSUMHASH");

            StringBuilder sb = new StringBuilder();
            sortedParams.forEach((key, value) -> {
                if (sb.length() > 0) sb.append("|");
                sb.append(value);
            });

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    paytmConfig.getClientSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(sb.toString().getBytes(StandardCharsets.UTF_8));
            String computedHash = Base64.getEncoder().encodeToString(hash);

            return computedHash.equals(params.get("CHECKSUMHASH"));
        } catch (Exception e) {
            log.error("Checksum verification error", e);
            return false;
        }
    }
}
