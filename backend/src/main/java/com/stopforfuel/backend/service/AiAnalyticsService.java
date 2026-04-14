package com.stopforfuel.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stopforfuel.backend.controller.DashboardController.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiAnalyticsService {

    private final ChatClient chatClient;
    private final DashboardService dashboardService;
    private final ObjectMapper objectMapper;
    private final com.stopforfuel.config.BusinessMetrics metrics;

    public record ChatMessageDTO(String role, String content) {}
    public record ChatResponse(String answer) {}
    public record Insight(String category, String title, String detail, String severity) {}
    public record InsightsResponse(List<Insight> insights, String generatedAt) {}

    public ChatResponse chat(String userMessage, List<ChatMessageDTO> history) {
        List<Message> messages = new ArrayList<>();

        if (history != null) {
            for (ChatMessageDTO msg : history) {
                if ("user".equals(msg.role())) {
                    messages.add(new UserMessage(msg.content()));
                } else {
                    messages.add(new AssistantMessage(msg.content()));
                }
            }
        }

        String response = metrics.aiRequestDuration.record(() ->
                chatClient.prompt()
                        .system(s -> s.param("current_date", LocalDate.now().toString()))
                        .messages(messages)
                        .user(userMessage)
                        .call()
                        .content()
        );

        return new ChatResponse(response);
    }

    public InsightsResponse generateInsights() {
        DashboardStats stats = dashboardService.getStats();
        InvoiceAnalytics invoiceAnalytics = dashboardService.getInvoiceAnalytics(null, null);
        PaymentAnalytics paymentAnalytics = dashboardService.getPaymentAnalytics(null, null);
        SystemHealth systemHealth = dashboardService.getSystemHealth();

        String dataContext = buildDataSummary(stats, invoiceAnalytics, paymentAnalytics, systemHealth);

        String response = metrics.aiRequestDuration.record(() ->
                chatClient.prompt()
                        .system("""
                                You are a fuel station data analyst. Analyze the provided station data and return
                                exactly 5-8 key insights as a JSON array. Each insight must have these fields:
                                - category: one of "sales", "inventory", "credit", "operations"
                                - title: short summary, max 60 characters
                                - detail: 1-2 sentences with specific numbers using ₹ for currency
                                - severity: one of "positive", "info", "warning", "critical"

                                Focus on actionable observations: revenue trends, low tank stock, overdue credit,
                                unusual patterns, top performers. Today's date is """ + LocalDate.now() + """
                                .
                                Return ONLY a valid JSON array, no markdown fencing, no extra text.
                                """)
                        .user(dataContext)
                        .call()
                        .content()
        );

        List<Insight> insights = parseInsightsJson(response);
        return new InsightsResponse(insights,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
    }

    private String buildDataSummary(DashboardStats stats, InvoiceAnalytics inv,
                                     PaymentAnalytics pay, SystemHealth health) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== TODAY'S STATS ===\n");
        sb.append("Revenue: ₹").append(stats.getTodayRevenue()).append("\n");
        sb.append("Fuel Volume: ").append(stats.getTodayFuelVolume()).append(" litres\n");
        sb.append("Invoices: ").append(stats.getTodayInvoiceCount())
                .append(" (Cash: ").append(stats.getTodayCashInvoices())
                .append(", Credit: ").append(stats.getTodayCreditInvoices()).append(")\n");
        sb.append("Active Shift: ").append(stats.getActiveShiftId() != null ? "ID " + stats.getActiveShiftId() : "None").append("\n");
        sb.append("Total Outstanding: ₹").append(stats.getTotalOutstanding()).append("\n");
        sb.append("Credit Customers: ").append(stats.getTotalCreditCustomers()).append("\n");
        sb.append("Credit Aging: 0-30d=₹").append(stats.getCreditAging0to30())
                .append(", 31-60d=₹").append(stats.getCreditAging31to60())
                .append(", 61-90d=₹").append(stats.getCreditAging61to90())
                .append(", 90+d=₹").append(stats.getCreditAging90Plus()).append("\n");

        if (stats.getTankStatuses() != null && !stats.getTankStatuses().isEmpty()) {
            sb.append("\n=== TANK STATUS ===\n");
            for (TankStatus t : stats.getTankStatuses()) {
                double pct = t.getCapacity() > 0 ? (t.getCurrentStock() / t.getCapacity()) * 100 : 0;
                sb.append(t.getTankName()).append(" (").append(t.getProductName()).append("): ")
                        .append(String.format("%.0f/%.0f (%.0f%%)", t.getCurrentStock(), t.getCapacity(), pct));
                if (t.getThresholdStock() != null && t.getCurrentStock() < t.getThresholdStock()) {
                    sb.append(" ⚠ BELOW THRESHOLD");
                }
                sb.append("\n");
            }
        }

        if (stats.getDailyRevenue() != null && !stats.getDailyRevenue().isEmpty()) {
            sb.append("\n=== 7-DAY REVENUE TREND ===\n");
            for (DailyRevenue dr : stats.getDailyRevenue()) {
                sb.append(dr.getDate()).append(": ₹").append(dr.getRevenue())
                        .append(" (").append(dr.getInvoiceCount()).append(" invoices)\n");
            }
        }

        sb.append("\n=== INVOICE ANALYTICS (").append(inv.getFromDate()).append(" to ").append(inv.getToDate()).append(") ===\n");
        sb.append("Total: ").append(inv.getTotalInvoices()).append(" invoices, ₹").append(inv.getTotalRevenue()).append("\n");
        sb.append("Cash: ").append(inv.getCashCount()).append(" (₹").append(inv.getCashAmount()).append(")\n");
        sb.append("Credit: ").append(inv.getCreditCount()).append(" (₹").append(inv.getCreditAmount()).append(")\n");
        sb.append("Unpaid: ").append(inv.getUnpaidCount()).append(" (₹").append(inv.getUnpaidAmount()).append(")\n");

        if (inv.getTopCustomers() != null && !inv.getTopCustomers().isEmpty()) {
            sb.append("Top Customers: ");
            for (NameCountAmount c : inv.getTopCustomers()) {
                sb.append(c.getName()).append("=₹").append(c.getAmount()).append(", ");
            }
            sb.append("\n");
        }

        sb.append("\n=== PAYMENT ANALYTICS (").append(pay.getFromDate()).append(" to ").append(pay.getToDate()).append(") ===\n");
        sb.append("Collected: ₹").append(pay.getTotalCollected()).append(" (").append(pay.getTotalPayments()).append(" payments)\n");
        sb.append("Outstanding: ₹").append(pay.getTotalOutstanding()).append("\n");
        sb.append("Collection Rate: ").append(pay.getCollectionRate()).append("%\n");

        sb.append("\n=== SYSTEM ===\n");
        sb.append("Customers: ").append(health.getTotalCustomers())
                .append(", Employees: ").append(health.getTotalEmployees())
                .append(", Products: ").append(health.getTotalProducts()).append("\n");

        return sb.toString();
    }

    private List<Insight> parseInsightsJson(String json) {
        try {
            String cleaned = json.strip();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceFirst("```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
            }
            return objectMapper.readValue(cleaned, new TypeReference<List<Insight>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse AI insights JSON: {}", e.getMessage());
            return List.of(new Insight("operations", "Insights unavailable",
                    "Could not generate insights at this time. Please try again.", "info"));
        }
    }
}
