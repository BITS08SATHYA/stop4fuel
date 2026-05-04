package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.ApplicationSetting;
import com.stopforfuel.backend.repository.ApplicationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * GET/PUT for the next-run-override on the scheduled statement auto-gen job.
 * Setting key: {@code auto_gen.next_run_override_date} (ISO date string, or absent).
 *
 * <p>Companion to {@code StatementAutoGenerationService.runScheduledAutoGen}, which fires
 * automatically on the 1st and 16th at 02:00 IST. The override lets an admin pin a
 * different date for the next run when reality drifts (e.g. a long shift overrun or a
 * holiday). Override is consumed on first match (cleared after firing).
 */
@RestController
@RequestMapping("/api/admin/auto-gen")
@RequiredArgsConstructor
public class AutoGenSettingsController {

    public static final String NEXT_RUN_OVERRIDE_KEY = "auto_gen.next_run_override_date";

    private final ApplicationSettingRepository settingsRepo;

    @GetMapping("/next-run")
    @PreAuthorize("hasPermission(null, 'PAYMENT_UPDATE')")
    public Map<String, Object> getNextRun() {
        Map<String, Object> body = new HashMap<>();
        Optional<ApplicationSetting> override = settingsRepo.findById(NEXT_RUN_OVERRIDE_KEY);
        body.put("overrideDate", override.map(ApplicationSetting::getValue).orElse(null));
        body.put("nextScheduledDate", computeNextScheduledRun(LocalDate.now()).toString());
        return body;
    }

    @PutMapping("/next-run")
    @PreAuthorize("hasPermission(null, 'PAYMENT_UPDATE')")
    public Map<String, Object> setNextRun(@RequestBody Map<String, String> body) {
        String dateStr = body == null ? null : body.get("date");
        if (dateStr == null || dateStr.isBlank()) {
            settingsRepo.deleteById(NEXT_RUN_OVERRIDE_KEY);
        } else {
            // Validate parseable
            LocalDate parsed = LocalDate.parse(dateStr);
            if (parsed.isBefore(LocalDate.now())) {
                throw new IllegalArgumentException("Override date must be today or future");
            }
            ApplicationSetting setting = settingsRepo.findById(NEXT_RUN_OVERRIDE_KEY)
                    .orElseGet(() -> {
                        ApplicationSetting s = new ApplicationSetting();
                        s.setKey(NEXT_RUN_OVERRIDE_KEY);
                        return s;
                    });
            setting.setValue(parsed.toString());
            settingsRepo.save(setting);
        }
        return getNextRun();
    }

    /** Compute the next 1st-or-16th from given date (inclusive of today). */
    static LocalDate computeNextScheduledRun(LocalDate from) {
        int day = from.getDayOfMonth();
        if (day <= 1) return from.withDayOfMonth(1);
        if (day <= 16) return from.withDayOfMonth(16);
        // After the 16th — next is the 1st of next month
        return from.plusMonths(1).withDayOfMonth(1);
    }

    /** Helper for tests: ensures the helper signature is exposed as expected. */
    @SuppressWarnings("unused")
    static YearMonth nextMonth(LocalDate from) { return YearMonth.from(from).plusMonths(1); }
}
