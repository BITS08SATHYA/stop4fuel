package com.stopforfuel.backend.service;

import com.stopforfuel.backend.dto.StockShiftClosePayload;
import com.stopforfuel.backend.dto.StockShiftClosePayload.ProductRow;
import com.stopforfuel.backend.dto.StockShiftClosePayload.TankRow;
import com.stopforfuel.backend.entity.Company;
import com.stopforfuel.backend.entity.NotificationConfig;
import com.stopforfuel.backend.entity.ProductInventory;
import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.entity.Tank;
import com.stopforfuel.backend.entity.User;
import com.stopforfuel.backend.enums.EntityStatus;
import com.stopforfuel.backend.repository.CashierStockRepository;
import com.stopforfuel.backend.repository.CompanyRepository;
import com.stopforfuel.backend.repository.GodownStockRepository;
import com.stopforfuel.backend.repository.NotificationConfigRepository;
import com.stopforfuel.backend.repository.ProductInventoryRepository;
import com.stopforfuel.backend.repository.ShiftRepository;
import com.stopforfuel.backend.repository.TankRepository;
import com.stopforfuel.backend.repository.UserRepository;
import com.stopforfuel.backend.service.ShiftSalesCalculationService.FuelSaleAggregate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds the today's-stock payload at shift close and fans it out via SSE, Android push,
 * and email PDF — each channel guarded so a failure on one never affects the others.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockNotificationService {

    public static final String ALERT_TYPE = NotificationConfigService.SHIFT_CLOSE_STOCK;

    private static final String CHANNEL_SSE = "SSE";
    private static final String CHANNEL_DASHBOARD = "DASHBOARD";
    private static final String CHANNEL_PUSH = "PUSH";
    private static final String CHANNEL_EMAIL = "EMAIL";

    private static final DateTimeFormatter PDF_FILE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private final NotificationConfigRepository configRepository;
    private final ShiftRepository shiftRepository;
    private final CompanyRepository companyRepository;
    private final TankRepository tankRepository;
    private final ProductInventoryRepository productInventoryRepository;
    private final CashierStockRepository cashierStockRepository;
    private final GodownStockRepository godownStockRepository;
    private final UserRepository userRepository;
    private final ShiftSalesCalculationService shiftSalesCalculationService;
    private final NotificationBroadcaster notificationBroadcaster;
    private final PushNotificationService pushNotificationService;
    private final EmailService emailService;
    private final ShiftCloseStockPdfGenerator pdfGenerator;

    @Transactional(readOnly = true)
    public void notifyShiftClose(Long shiftId) {
        Shift shift = shiftRepository.findById(shiftId).orElse(null);
        if (shift == null) {
            log.warn("Stock notify skipped — shift {} not found", shiftId);
            return;
        }
        Long scid = shift.getScid();
        if (scid == null) {
            log.warn("Stock notify skipped — shift {} has no scid", shiftId);
            return;
        }

        Optional<NotificationConfig> configOpt = configRepository.findByAlertTypeAndScid(ALERT_TYPE, scid);
        if (configOpt.isEmpty() || !configOpt.get().isEnabled()) {
            log.debug("Stock notify skipped — no enabled config for scid {}", scid);
            return;
        }
        NotificationConfig config = configOpt.get();

        StockShiftClosePayload payload = buildPayload(shift, config);

        // Resolve recipients per configured role.
        Set<Long> userIds = new LinkedHashSet<>();
        Set<String> emails = new LinkedHashSet<>();
        if (config.getEmailRecipients() != null) emails.addAll(config.getEmailRecipients());
        for (String role : safe(config.getNotifyRoles())) {
            List<User> users = userRepository.findByRoleRoleTypeAndScidAndStatus(role, scid, EntityStatus.ACTIVE);
            for (User u : users) {
                if (u.getId() != null) userIds.add(u.getId());
                if (u.getEmails() != null) emails.addAll(u.getEmails());
            }
        }

        Set<String> channels = safe(config.getChannels());
        if (channels.contains(CHANNEL_SSE) || channels.contains(CHANNEL_DASHBOARD)) {
            sendSse(userIds, payload);
        }
        if (channels.contains(CHANNEL_PUSH)) {
            sendPush(userIds, payload);
        }
        if (channels.contains(CHANNEL_EMAIL)) {
            sendEmail(emails, payload);
        }
    }

    private StockShiftClosePayload buildPayload(Shift shift, NotificationConfig config) {
        Long scid = shift.getScid();
        Double threshold = config.getLowStockThreshold();

        StockShiftClosePayload payload = new StockShiftClosePayload();
        payload.setShiftId(shift.getId());
        payload.setScid(scid);
        payload.setClosedAt(shift.getEndTime() != null ? shift.getEndTime() : java.time.LocalDateTime.now());
        payload.setCompanyName(companyRepository.findByScid(scid).stream()
                .findFirst().map(Company::getName).orElse("StopForFuel"));

        // Fuel tanks
        Map<Long, FuelSaleAggregate> fuelByProduct;
        try {
            fuelByProduct = shiftSalesCalculationService.computeFuelSalesByProduct(shift.getId());
        } catch (Exception e) {
            log.warn("Stock notify: fuel sales calc failed for shift {}: {}", shift.getId(), e.getMessage());
            fuelByProduct = Map.of();
        }
        List<TankRow> tankRows = new ArrayList<>();
        int lowCount = 0;
        for (Tank t : tankRepository.findByActiveAndScid(true, scid)) {
            double current = t.getAvailableStock() != null ? t.getAvailableStock() : 0d;
            FuelSaleAggregate agg = t.getProduct() != null ? fuelByProduct.get(t.getProduct().getId()) : null;
            double sold = agg != null ? agg.getNetLitres() : 0d;
            double price = t.getProduct() != null && t.getProduct().getPrice() != null
                    ? t.getProduct().getPrice().doubleValue() : 0d;
            boolean low = isLow(current, threshold, t.getThresholdStock());
            if (low) lowCount++;
            tankRows.add(new TankRow(
                    t.getName(),
                    t.getProduct() != null ? t.getProduct().getName() : "—",
                    current, sold, price, low));
        }
        payload.setTanks(tankRows);

        // Non-fuel products (from this shift's ProductInventory rows).
        List<ProductRow> productRows = new ArrayList<>();
        for (ProductInventory pi : productInventoryRepository.findByShiftId(shift.getId())) {
            if (pi.getProduct() == null) continue;
            double current = currentNonFuelStock(pi.getProduct().getId(), scid, pi.getCloseStock());
            double sold = pi.getSales() != null ? pi.getSales() : 0d;
            double price = pi.getRate() != null ? pi.getRate().doubleValue()
                    : (pi.getProduct().getPrice() != null ? pi.getProduct().getPrice().doubleValue() : 0d);
            boolean low = isLow(current, threshold, null);
            if (low) lowCount++;
            productRows.add(new ProductRow(
                    pi.getProduct().getName(),
                    pi.getProduct().getUnit(),
                    current, sold, price, low));
        }
        payload.setProducts(productRows);
        payload.setLowStockCount(lowCount);
        return payload;
    }

    private double currentNonFuelStock(Long productId, Long scid, Double fallback) {
        double cashier = cashierStockRepository.findByProductIdAndScid(productId, scid)
                .map(cs -> cs.getCurrentStock() != null ? cs.getCurrentStock() : 0d).orElse(0d);
        double godown = godownStockRepository.findByProductIdAndScid(productId, scid)
                .map(gs -> gs.getCurrentStock() != null ? gs.getCurrentStock() : 0d).orElse(0d);
        double live = cashier + godown;
        if (live > 0) return live;
        return fallback != null ? fallback : 0d;
    }

    private boolean isLow(double current, Double threshold, Double fallback) {
        if (threshold != null && threshold > 0) return current <= threshold;
        if (fallback != null && fallback > 0) return current <= fallback;
        return false;
    }

    private void sendSse(Collection<Long> userIds, StockShiftClosePayload payload) {
        try {
            for (Long uid : userIds) {
                notificationBroadcaster.sendToUser(uid, "stock", payload);
            }
        } catch (Exception e) {
            log.warn("Stock SSE broadcast failed for shift {}: {}", payload.getShiftId(), e.getMessage());
        }
    }

    private void sendPush(Collection<Long> userIds, StockShiftClosePayload payload) {
        try {
            String title = "Shift #" + payload.getShiftId() + " closed";
            String body = summaryLine(payload);
            pushNotificationService.notifyShiftCloseStock(userIds, title, body,
                    Map.of("shiftId", payload.getShiftId(), "lowStockCount", payload.getLowStockCount()));
        } catch (Exception e) {
            log.warn("Stock push failed for shift {}: {}", payload.getShiftId(), e.getMessage());
        }
    }

    private void sendEmail(Collection<String> emails, StockShiftClosePayload payload) {
        try {
            byte[] pdf = pdfGenerator.generate(payload);
            String subject = "Shift #" + payload.getShiftId() + " — Stock Summary";
            String body = summaryLine(payload) + "\n\nSee attached PDF for the full breakdown.";
            String filename = "stock-shift-" + payload.getShiftId()
                    + "-" + payload.getClosedAt().format(PDF_FILE_FMT) + ".pdf";
            emailService.sendWithAttachment(emails, subject, body, filename, pdf);
        } catch (Exception e) {
            log.warn("Stock email failed for shift {}: {}", payload.getShiftId(), e.getMessage());
        }
    }

    private String summaryLine(StockShiftClosePayload payload) {
        int tanks = payload.getTanks() != null ? payload.getTanks().size() : 0;
        int products = payload.getProducts() != null ? payload.getProducts().size() : 0;
        return String.format("Stock as of close — %d tank(s), %d product(s)%s.",
                tanks, products,
                payload.getLowStockCount() > 0 ? "; " + payload.getLowStockCount() + " low" : "");
    }

    private <T> Set<T> safe(Set<T> in) {
        return in == null ? new HashSet<>() : in;
    }
}
