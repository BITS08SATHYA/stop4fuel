package com.stopforfuel.backend.service;

import com.stopforfuel.backend.controller.DashboardController.*;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Exposes DashboardService methods as Spring AI tools for Claude function calling.
 * All methods are read-only.
 */
@Service
@RequiredArgsConstructor
public class DashboardToolService {

    private final DashboardService dashboardService;

    @Tool(description = "Get today's dashboard statistics including: today's revenue, fuel volume sold, " +
            "invoice counts (total/cash/credit), active shift info, tank statuses (stock levels, capacity), " +
            "credit aging breakdown (0-30/31-60/61-90/90+ days), 7-day daily revenue trend, " +
            "product sales breakdown, and recent invoices")
    public DashboardStats getDashboardStats() {
        return dashboardService.getStats();
    }

    @Tool(description = "Get invoice analytics for a date range: total invoices, total revenue, " +
            "average invoice value, cash vs credit breakdown (count and amount), paid vs unpaid stats, " +
            "daily revenue trend, payment mode distribution, top customers by revenue, " +
            "product breakdown by quantity and amount, hourly transaction distribution. " +
            "Pass null for both dates to get last 30 days.")
    public InvoiceAnalytics getInvoiceAnalytics(
            @ToolParam(description = "Start date in YYYY-MM-DD format, or null for default") String fromDate,
            @ToolParam(description = "End date in YYYY-MM-DD format, or null for default") String toDate) {
        LocalDate from = fromDate != null && !fromDate.isBlank() ? LocalDate.parse(fromDate) : null;
        LocalDate to = toDate != null && !toDate.isBlank() ? LocalDate.parse(toDate) : null;
        return dashboardService.getInvoiceAnalytics(from, to);
    }

    @Tool(description = "Get payment analytics for a date range: total collected amount, payment count, " +
            "average payment, total outstanding balance, credit customer count, collection rate percentage, " +
            "credit aging breakdown (0-30/31-60/61-90/90+ days overdue), daily collection trend, " +
            "payment mode breakdown, top paying customers. Pass null for both dates to get last 30 days.")
    public PaymentAnalytics getPaymentAnalytics(
            @ToolParam(description = "Start date in YYYY-MM-DD format, or null for default") String fromDate,
            @ToolParam(description = "End date in YYYY-MM-DD format, or null for default") String toDate) {
        LocalDate from = fromDate != null && !fromDate.isBlank() ? LocalDate.parse(fromDate) : null;
        LocalDate to = toDate != null && !toDate.isBlank() ? LocalDate.parse(toDate) : null;
        return dashboardService.getPaymentAnalytics(from, to);
    }

    @Tool(description = "Get system health overview: total customers, total employees, total users, " +
            "total products, count of active shifts, today's attendance count")
    public SystemHealth getSystemHealth() {
        return dashboardService.getSystemHealth();
    }
}
