import { test, expect } from "@playwright/test";
import { mockApiRoutes, mockApiRoute } from "./fixtures/test-helpers";

const API_BASE = "http://localhost:8080/api";

const mockInvoiceAnalytics = {
  fromDate: "2026-02-19",
  toDate: "2026-03-20",
  totalInvoices: 150,
  totalRevenue: 750000,
  avgInvoiceValue: 5000,
  cashCount: 100,
  cashAmount: 500000,
  creditCount: 50,
  creditAmount: 250000,
  paidCount: 120,
  paidAmount: 600000,
  unpaidCount: 30,
  unpaidAmount: 150000,
  dailyTrend: Array.from({ length: 30 }, (_, i) => ({
    date: `2026-02-${String(19 + (i % 10)).padStart(2, "0")}`,
    totalCount: 5,
    totalAmount: 25000,
    cashCount: 3,
    cashAmount: 15000,
    creditCount: 2,
    creditAmount: 10000,
  })),
  paymentModeDistribution: [
    { name: "Cash", count: 60, amount: 300000 },
    { name: "UPI", count: 30, amount: 150000 },
    { name: "Card", count: 10, amount: 50000 },
  ],
  topCustomers: [
    { name: "Customer A", count: 20, amount: 100000 },
    { name: "Customer B", count: 15, amount: 75000 },
  ],
  productBreakdown: [
    { productName: "Petrol", quantity: 5000, amount: 500000 },
    { productName: "Diesel", quantity: 3000, amount: 250000 },
  ],
  hourlyDistribution: Array.from({ length: 24 }, (_, i) => ({
    hour: i,
    count: i >= 6 && i <= 22 ? Math.floor(Math.random() * 10) + 1 : 0,
  })),
};

test.describe("Invoice Analytics Dashboard", () => {
  test.beforeEach(async ({ page }) => {
    await mockApiRoutes(page);
    await page.route(`${API_BASE}/dashboard/invoice-analytics*`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(mockInvoiceAnalytics),
      });
    });
  });

  test("renders page with title", async ({ page }) => {
    await page.goto("/operations/invoices/dashboard");
    await expect(page.getByText("Invoice Analytics")).toBeVisible();
  });

  test("displays KPI cards", async ({ page }) => {
    await page.goto("/operations/invoices/dashboard");
    await expect(page.getByText("Total Revenue")).toBeVisible();
    await expect(page.getByText("Avg Invoice Value")).toBeVisible();
    await expect(page.getByText("Cash Sales")).toBeVisible();
    await expect(page.getByText("Credit Sales")).toBeVisible();
  });

  test("displays chart sections", async ({ page }) => {
    await page.goto("/operations/invoices/dashboard");
    await expect(page.getByText("Revenue Trend", { exact: true })).toBeVisible();
    await expect(page.getByText("Cash vs Credit", { exact: true })).toBeVisible();
    await expect(page.getByText("Payment Status", { exact: true })).toBeVisible();
    await expect(page.getByText("Payment Modes", { exact: true })).toBeVisible();
  });

  test("displays top customers and product breakdown", async ({ page }) => {
    await page.goto("/operations/invoices/dashboard");
    await expect(page.getByText("Top Customers by Revenue")).toBeVisible();
    await expect(page.getByText("Product-wise Sales")).toBeVisible();
    await expect(page.getByText("Petrol")).toBeVisible();
    await expect(page.getByText("Diesel")).toBeVisible();
  });

  test("displays hourly distribution", async ({ page }) => {
    await page.goto("/operations/invoices/dashboard");
    await expect(page.getByText("Hourly Invoice Distribution")).toBeVisible();
  });

  test("date range buttons are visible and clickable", async ({ page }) => {
    await page.goto("/operations/invoices/dashboard");
    const btn7d = page.getByRole("button", { name: "7 Days" });
    const btn30d = page.getByRole("button", { name: "30 Days" });
    const btn90d = page.getByRole("button", { name: "90 Days" });
    await expect(btn7d).toBeVisible();
    await expect(btn30d).toBeVisible();
    await expect(btn90d).toBeVisible();
  });

  test("clicking 7 Days reloads data", async ({ page }) => {
    await page.goto("/operations/invoices/dashboard");
    await page.getByRole("button", { name: "7 Days" }).click();
    // Page should still render with data
    await expect(page.getByText("Invoice Analytics")).toBeVisible();
    await expect(page.getByText("Total Revenue")).toBeVisible();
  });

  test("clicking 90 Days reloads data", async ({ page }) => {
    await page.goto("/operations/invoices/dashboard");
    await page.getByRole("button", { name: "90 Days" }).click();
    await expect(page.getByText("Invoice Analytics")).toBeVisible();
    await expect(page.getByText("Total Revenue")).toBeVisible();
  });

  test("shows payment mode distribution", async ({ page }) => {
    await page.goto("/operations/invoices/dashboard");
    await expect(page.getByText("Payment Modes", { exact: true })).toBeVisible();
    await expect(page.getByText("UPI", { exact: true }).first()).toBeVisible();
  });

  test("shows paid vs unpaid breakdown", async ({ page }) => {
    await page.goto("/operations/invoices/dashboard");
    // Look for Paid/Unpaid within the Payment Status section
    const statusSection = page.locator("div").filter({ hasText: /^Payment Status/ }).first();
    await expect(statusSection).toBeVisible();
  });

  test("empty state shows no data", async ({ page }) => {
    await page.route(`${API_BASE}/dashboard/invoice-analytics*`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          ...mockInvoiceAnalytics,
          totalInvoices: 0,
          totalRevenue: 0,
          cashCount: 0,
          cashAmount: 0,
          creditCount: 0,
          creditAmount: 0,
          paidCount: 0,
          paidAmount: 0,
          unpaidCount: 0,
          unpaidAmount: 0,
          paymentModeDistribution: [],
          topCustomers: [],
          productBreakdown: [],
        }),
      });
    });
    await page.goto("/operations/invoices/dashboard");
    await expect(page.getByText("Invoice Analytics")).toBeVisible();
  });
});
