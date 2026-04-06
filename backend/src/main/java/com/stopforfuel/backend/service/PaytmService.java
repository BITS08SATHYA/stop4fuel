package com.stopforfuel.backend.service;

import com.stopforfuel.backend.config.PaytmConfig;
import com.stopforfuel.backend.dto.PaytmReconSummaryDTO;
import com.stopforfuel.backend.dto.PaytmSyncResultDTO;
import com.stopforfuel.backend.entity.EAdvance;
import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.entity.PaytmTransaction;
import com.stopforfuel.backend.enums.MatchConfidence;
import com.stopforfuel.backend.enums.ReconStatus;
import com.stopforfuel.backend.enums.SettlementStatus;
import com.stopforfuel.backend.repository.EAdvanceRepository;
import com.stopforfuel.backend.repository.InvoiceBillRepository;
import com.stopforfuel.backend.repository.PaytmTransactionRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaytmService {

    private final PaytmConfig config;
    private final PaytmTransactionRepository paytmTxnRepository;
    private final EAdvanceRepository eAdvanceRepository;
    private final InvoiceBillRepository invoiceBillRepository;
    private final WebClient.Builder webClientBuilder;

    private static final DateTimeFormatter PAYTM_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ========================
    // Checksum Generation
    // ========================

    /**
     * Generates HMAC-SHA256 checksum for PayTM API authentication.
     */
    private String generateChecksum(Map<String, String> params) {
        try {
            TreeMap<String, String> sorted = new TreeMap<>(params);
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : sorted.entrySet()) {
                if (sb.length() > 0) sb.append("|");
                sb.append(entry.getKey()).append("=").append(entry.getValue());
            }

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    config.getMerchantKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(sb.toString().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PayTM checksum", e);
        }
    }

    // ========================
    // PayTM API Calls
    // ========================

    /**
     * Fetches transaction list from PayTM API for a specific date.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchTransactionsFromApi(LocalDate date) {
        if (config.getMerchantId() == null || config.getMerchantId().isEmpty()) {
            throw new IllegalStateException("PayTM merchant ID is not configured");
        }

        Map<String, String> params = new LinkedHashMap<>();
        params.put("MID", config.getMerchantId());
        params.put("startDate", date.atStartOfDay().format(PAYTM_DATE_FORMAT));
        params.put("endDate", date.atTime(LocalTime.MAX).format(PAYTM_DATE_FORMAT));

        String checksum = generateChecksum(params);
        params.put("checksumhash", checksum);

        WebClient client = webClientBuilder.baseUrl(config.getBaseUrl()).build();

        Map<String, Object> response = client.post()
                .uri("/merchant-status/api/v1/getTransactionList")
                .bodyValue(params)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if (response == null || !"SUCCESS".equals(response.get("status"))) {
            log.warn("PayTM API returned non-success: {}", response);
            return Collections.emptyList();
        }

        Object txnList = response.get("txnList");
        if (txnList instanceof List) {
            return (List<Map<String, Object>>) txnList;
        }
        return Collections.emptyList();
    }

    /**
     * Fetches status of a single transaction by order ID.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchTransactionStatus(String orderId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("MID", config.getMerchantId());
        params.put("ORDERID", orderId);

        String checksum = generateChecksum(params);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mid", config.getMerchantId());
        body.put("orderId", orderId);

        Map<String, Object> head = new LinkedHashMap<>();
        head.put("signature", checksum);

        Map<String, Object> request = Map.of("body", body, "head", head);

        WebClient client = webClientBuilder.baseUrl(config.getBaseUrl()).build();

        return client.post()
                .uri("/v3/order/status")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }

    // ========================
    // Sync Operations
    // ========================

    @Transactional
    public PaytmSyncResultDTO syncTransactions(LocalDate date) {
        Long scid = SecurityUtils.getScid();
        List<Map<String, Object>> apiTxns = fetchTransactionsFromApi(date);

        int fetched = apiTxns.size();
        int newlyStored = 0;
        int skipped = 0;

        for (Map<String, Object> apiTxn : apiTxns) {
            String orderId = String.valueOf(apiTxn.get("ORDERID"));

            if (paytmTxnRepository.existsByScidAndPaytmOrderId(scid, orderId)) {
                skipped++;
                continue;
            }

            PaytmTransaction txn = new PaytmTransaction();
            txn.setScid(scid);
            txn.setPaytmOrderId(orderId);
            txn.setPaytmTxnId(getStr(apiTxn, "TXNID"));
            txn.setTxnAmount(new BigDecimal(String.valueOf(apiTxn.get("TXNAMOUNT"))));
            txn.setTxnStatus(getStr(apiTxn, "STATUS"));
            txn.setPaytmPaymentMode(getStr(apiTxn, "PAYMENTMODE"));
            txn.setGatewayName(getStr(apiTxn, "GATEWAYNAME"));
            txn.setBankName(getStr(apiTxn, "BANKNAME"));
            txn.setBankTxnId(getStr(apiTxn, "BANKTXNID"));
            txn.setCurrency(getStr(apiTxn, "CURRENCY"));
            txn.setResponseCode(getStr(apiTxn, "RESPCODE"));
            txn.setResponseMessage(getStr(apiTxn, "RESPMSG"));
            txn.setReconStatus(ReconStatus.UNMATCHED);
            txn.setSettlementStatus(SettlementStatus.PENDING);
            txn.setFetchedAt(LocalDateTime.now());

            String txnDate = getStr(apiTxn, "TXNDATE");
            if (txnDate != null) {
                txn.setTxnDate(LocalDateTime.parse(txnDate, PAYTM_DATE_FORMAT));
            }

            paytmTxnRepository.save(txn);
            newlyStored++;
        }

        // Auto-reconcile after sync
        int matched = runReconciliation(date, date);

        return PaytmSyncResultDTO.builder()
                .fetched(fetched)
                .newlyStored(newlyStored)
                .skippedDuplicates(skipped)
                .matched(matched)
                .syncDate(date)
                .build();
    }

    @Transactional
    public PaytmSyncResultDTO syncTransactionRange(LocalDate fromDate, LocalDate toDate) {
        int totalFetched = 0, totalNew = 0, totalSkipped = 0, totalMatched = 0;

        LocalDate current = fromDate;
        while (!current.isAfter(toDate)) {
            PaytmSyncResultDTO result = syncTransactions(current);
            totalFetched += result.getFetched();
            totalNew += result.getNewlyStored();
            totalSkipped += result.getSkippedDuplicates();
            totalMatched += result.getMatched();
            current = current.plusDays(1);
        }

        return PaytmSyncResultDTO.builder()
                .fetched(totalFetched)
                .newlyStored(totalNew)
                .skippedDuplicates(totalSkipped)
                .matched(totalMatched)
                .syncDate(toDate)
                .build();
    }

    // ========================
    // Reconciliation
    // ========================

    @Transactional
    public int runReconciliation(LocalDate fromDate, LocalDate toDate) {
        Long scid = SecurityUtils.getScid();
        LocalDateTime from = fromDate.atStartOfDay();
        LocalDateTime to = toDate.atTime(LocalTime.MAX);

        List<PaytmTransaction> unmatched = paytmTxnRepository.findByReconStatus(scid, ReconStatus.UNMATCHED);
        int matchCount = 0;

        for (PaytmTransaction txn : unmatched) {
            if (!"TXN_SUCCESS".equals(txn.getTxnStatus())) continue;
            if (txn.getTxnDate() != null && (txn.getTxnDate().isBefore(from) || txn.getTxnDate().isAfter(to))) continue;

            // Strategy 1: Exact match by reference (EAdvance.tid = paytmOrderId)
            List<EAdvance> exactMatches = eAdvanceRepository.findByPaytmReference(scid, txn.getPaytmOrderId());
            if (exactMatches.size() == 1) {
                applyMatch(txn, exactMatches.get(0), MatchConfidence.AUTO_EXACT);
                matchCount++;
                continue;
            }

            // Strategy 2: Amount + time proximity (±30 minutes)
            if (txn.getTxnDate() != null) {
                LocalDateTime windowStart = txn.getTxnDate().minusMinutes(30);
                LocalDateTime windowEnd = txn.getTxnDate().plusMinutes(30);
                List<EAdvance> amountTimeMatches = eAdvanceRepository.findUnmatchedPaytmByAmountAndTimeRange(
                        scid, txn.getTxnAmount(), windowStart, windowEnd);
                if (amountTimeMatches.size() == 1) {
                    applyMatch(txn, amountTimeMatches.get(0), MatchConfidence.AUTO_AMOUNT_TIME);
                    matchCount++;
                    continue;
                }
            }

            // Strategy 3: Through InvoiceBill (same amount, same day)
            if (txn.getTxnDate() != null) {
                LocalDateTime dayStart = txn.getTxnDate().toLocalDate().atStartOfDay();
                LocalDateTime dayEnd = txn.getTxnDate().toLocalDate().atTime(LocalTime.MAX);
                List<InvoiceBill> invoiceMatches = invoiceBillRepository.findUnmatchedPaytmByAmountAndDateRange(
                        scid, txn.getTxnAmount(), dayStart, dayEnd);
                if (invoiceMatches.size() == 1) {
                    txn.setMatchedInvoiceId(invoiceMatches.get(0).getId());
                    txn.setReconStatus(ReconStatus.MATCHED);
                    txn.setMatchConfidence(MatchConfidence.AUTO_AMOUNT_TIME);
                    paytmTxnRepository.save(txn);
                    matchCount++;
                }
            }
        }

        return matchCount;
    }

    private void applyMatch(PaytmTransaction txn, EAdvance eAdvance, MatchConfidence confidence) {
        txn.setReconStatus(ReconStatus.MATCHED);
        txn.setMatchedEAdvanceId(eAdvance.getId());
        if (eAdvance.getInvoiceBill() != null) {
            txn.setMatchedInvoiceId(eAdvance.getInvoiceBill().getId());
        }
        txn.setMatchConfidence(confidence);
        paytmTxnRepository.save(txn);
    }

    // ========================
    // Manual Reconciliation
    // ========================

    @Transactional
    public PaytmTransaction manualMatch(Long paytmTxnId, Long invoiceBillId) {
        Long scid = SecurityUtils.getScid();
        PaytmTransaction txn = paytmTxnRepository.findById(paytmTxnId)
                .orElseThrow(() -> new RuntimeException("PayTM transaction not found: " + paytmTxnId));

        InvoiceBill invoice = invoiceBillRepository.findById(invoiceBillId)
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + invoiceBillId));

        txn.setReconStatus(ReconStatus.MATCHED);
        txn.setMatchedInvoiceId(invoice.getId());
        txn.setMatchConfidence(MatchConfidence.MANUAL);
        return paytmTxnRepository.save(txn);
    }

    @Transactional
    public PaytmTransaction ignoreTransaction(Long paytmTxnId, String reason) {
        PaytmTransaction txn = paytmTxnRepository.findById(paytmTxnId)
                .orElseThrow(() -> new RuntimeException("PayTM transaction not found: " + paytmTxnId));
        txn.setReconStatus(ReconStatus.IGNORED);
        txn.setReconNote(reason);
        return paytmTxnRepository.save(txn);
    }

    @Transactional
    public PaytmTransaction disputeTransaction(Long paytmTxnId, String reason) {
        PaytmTransaction txn = paytmTxnRepository.findById(paytmTxnId)
                .orElseThrow(() -> new RuntimeException("PayTM transaction not found: " + paytmTxnId));
        txn.setReconStatus(ReconStatus.DISPUTED);
        txn.setReconNote(reason);
        return paytmTxnRepository.save(txn);
    }

    // ========================
    // Settlement Tracking
    // ========================

    @SuppressWarnings("unchecked")
    @Transactional
    public int syncSettlements(LocalDate date) {
        Long scid = SecurityUtils.getScid();
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.atTime(LocalTime.MAX);

        // Fetch settled transactions from PayTM
        Map<String, String> params = new LinkedHashMap<>();
        params.put("MID", config.getMerchantId());
        params.put("settlementDate", date.toString());
        String checksum = generateChecksum(params);
        params.put("checksumhash", checksum);

        WebClient client = webClientBuilder.baseUrl(config.getBaseUrl()).build();

        Map<String, Object> response = client.post()
                .uri("/merchant-status/api/v1/getSettlementReport")
                .bodyValue(params)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if (response == null || !"SUCCESS".equals(response.get("status"))) {
            log.warn("PayTM Settlement API returned non-success: {}", response);
            return 0;
        }

        int updated = 0;
        Object settlementList = response.get("settlementList");
        if (settlementList instanceof List) {
            for (Map<String, Object> settlement : (List<Map<String, Object>>) settlementList) {
                String orderId = getStr(settlement, "ORDERID");
                Optional<PaytmTransaction> optTxn = paytmTxnRepository.findByScidAndPaytmOrderId(scid, orderId);
                if (optTxn.isPresent()) {
                    PaytmTransaction txn = optTxn.get();
                    txn.setSettlementStatus(SettlementStatus.SETTLED);
                    txn.setSettlementDate(date);
                    String settleAmt = getStr(settlement, "SETTLEMENT_AMOUNT");
                    if (settleAmt != null) {
                        txn.setSettlementAmount(new BigDecimal(settleAmt));
                    }
                    paytmTxnRepository.save(txn);
                    updated++;
                }
            }
        }

        return updated;
    }

    // ========================
    // Query Methods
    // ========================

    @Transactional(readOnly = true)
    public Optional<PaytmTransaction> getById(Long id) {
        return paytmTxnRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<PaytmTransaction> getByDateRange(LocalDate from, LocalDate to) {
        Long scid = SecurityUtils.getScid();
        return paytmTxnRepository.findByDateRange(scid, from.atStartOfDay(), to.atTime(LocalTime.MAX));
    }

    @Transactional(readOnly = true)
    public List<PaytmTransaction> getByReconStatus(ReconStatus status) {
        return paytmTxnRepository.findByReconStatus(SecurityUtils.getScid(), status);
    }

    @Transactional(readOnly = true)
    public List<InvoiceBill> getUnmatchedInvoices(LocalDate from, LocalDate to) {
        return invoiceBillRepository.findUnmatchedPaytmInvoices(
                SecurityUtils.getScid(), from.atStartOfDay(), to.atTime(LocalTime.MAX));
    }

    @Transactional(readOnly = true)
    public PaytmReconSummaryDTO getReconSummary(LocalDate from, LocalDate to) {
        Long scid = SecurityUtils.getScid();
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.atTime(LocalTime.MAX);

        List<PaytmTransaction> txns = paytmTxnRepository.findByDateRange(scid, fromDt, toDt);
        long total = txns.size();
        long matched = txns.stream().filter(t -> t.getReconStatus() == ReconStatus.MATCHED).count();
        long unmatchedPaytm = txns.stream().filter(t -> t.getReconStatus() == ReconStatus.UNMATCHED).count();
        long disputed = txns.stream().filter(t -> t.getReconStatus() == ReconStatus.DISPUTED).count();

        List<InvoiceBill> unmatchedInvoices = invoiceBillRepository.findUnmatchedPaytmInvoices(scid, fromDt, toDt);

        BigDecimal totalPaytmAmt = paytmTxnRepository.sumSuccessfulByDateRange(scid, fromDt, toDt);
        BigDecimal settledAmt = paytmTxnRepository.sumSettledByDateRange(scid, fromDt, toDt);
        BigDecimal pendingAmt = paytmTxnRepository.sumPendingSettlementByDateRange(scid, fromDt, toDt);

        BigDecimal totalInvoiceAmt = unmatchedInvoices.stream()
                .map(InvoiceBill::getNetAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return PaytmReconSummaryDTO.builder()
                .totalTransactions(total)
                .matchedCount(matched)
                .unmatchedPaytmCount(unmatchedPaytm)
                .unmatchedInvoiceCount(unmatchedInvoices.size())
                .disputedCount(disputed)
                .totalPaytmAmount(totalPaytmAmt)
                .totalInvoiceAmount(totalInvoiceAmt)
                .settledAmount(settledAmt)
                .pendingSettlementAmount(pendingAmt)
                .build();
    }

    // ========================
    // Helpers
    // ========================

    private String getStr(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : null;
    }
}
