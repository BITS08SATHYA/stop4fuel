import { test, expect } from "@playwright/test";
import { mockApiRoutes, mockApiRoute } from "./fixtures/test-helpers";

const API_BASE = "http://localhost:8080/api";

const mockPaymentAnalytics = {
  fromDate: "2026-02-19",
  toDate: "2026-03-20",
  totalCollected: 500000,
  totalPayments: 80,
  avgPaymentAmount: 6250,
  totalOutstanding: 200000,
  creditCustomers: 15,
  collectionRate: 71.4,
  aging0to30: 80000,
  aging31to60: 60000,
  aging61to90: 40000,
  aging90Plus: 20000,
  dailyTrend: Array.from({ length: 30 }, (_, i) => ({
    date: `2026-02-${String(19 + (i % 10)).padStart(2, "0")}`,
    count: 3,
    amount: 18000,
  })),
  paymentModeBreakdown: [
    { name: "Cash", count: 40, amount: 250000 },
    { name: "UPI", count: 25, amount: 150000 },
    { name: "Bank Transfer", count: 15, amount: 100000 },
  ],
  topCustomers: [
    { name: "Big Corp", count: 10, amount: 80000 },
    { name: "Small Biz", count: 8, amount: 60000 },
  ],
};

test.describe("Payment Analytics Dashboard", () => {
  test.beforeEach(async ({ page }) => {
    await mockApiRoutes(page);
    await page.route(`${API_BASE}/dashboard/payment-analytics*`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(mockPaymentAnalytics),
      });
    });
  });

  test("renders page with title", async ({ page }) => {
    await page.goto("/payments/dashboard");
    await expect(page.getByText("Payment Analytics")).toBeVisible();
  });

  test("displays KPI cards", async ({ page }) => {
    await page.goto("/payments/dashboard");
    await expect(page.getByText("Total Collected", { exact: true })).toBeVisible();
    await expect(page.getByText("Outstanding", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Collection Rate", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Avg Payment", { exact: true })).toBeVisible();
    await expect(page.getByText("Total Payments", { exact: true })).toBeVisible();
  });

  test("shows collection rate percentage", async ({ page }) => {
    await page.goto("/payments/dashboard");
    await expect(page.getByRole("heading", { name: "71.4%" })).toBeVisible();
  });

  test("displays collection trend chart", async ({ page }) => {
    await page.goto("/payments/dashboard");
    await expect(page.getByText("Collection Trend")).toBeVisible();
    await expect(page.getByText("Daily payment collections")).toBeVisible();
  });

  test("displays payment mode breakdown", async ({ page }) => {
    await page.goto("/payments/dashboard");
    await expect(page.getByText("Payment Modes", { exact: true })).toBeVisible();
    await expect(page.getByText("UPI", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Bank Transfer", { exact: true }).first()).toBeVisible();
  });

  test("displays collected vs outstanding section", async ({ page }) => {
    await page.goto("/payments/dashboard");
    await expect(page.getByText("Collected vs Outstanding", { exact: true })).toBeVisible();
  });

  test("displays credit aging", async ({ page }) => {
    await page.goto("/payments/dashboard");
    await expect(page.getByText("Credit Aging")).toBeVisible();
    await expect(page.getByText("0-30 days")).toBeVisible();
    await expect(page.getByText("31-60 days")).toBeVisible();
    await expect(page.getByText("61-90 days")).toBeVisible();
    await expect(page.getByText("90+ days")).toBeVisible();
  });

  test("displays top paying customers", async ({ page }) => {
    await page.goto("/payments/dashboard");
    await expect(page.getByText("Top Paying Customers")).toBeVisible();
  });

  test("date range buttons work", async ({ page }) => {
    await page.goto("/payments/dashboard");
    const btn7d = page.getByRole("button", { name: "7 Days" });
    const btn30d = page.getByRole("button", { name: "30 Days" });
    const btn90d = page.getByRole("button", { name: "90 Days" });
    await expect(btn7d).toBeVisible();
    await expect(btn30d).toBeVisible();
    await expect(btn90d).toBeVisible();
  });

  test("switching to 7 Days reloads data", async ({ page }) => {
    await page.goto("/payments/dashboard");
    await page.getByRole("button", { name: "7 Days" }).click();
    await expect(page.getByText("Payment Analytics")).toBeVisible();
    await expect(page.getByText("Total Collected", { exact: true })).toBeVisible();
  });

  test("switching to 90 Days reloads data", async ({ page }) => {
    await page.goto("/payments/dashboard");
    await page.getByRole("button", { name: "90 Days" }).click();
    await expect(page.getByText("Payment Analytics")).toBeVisible();
    await expect(page.getByText("Total Collected", { exact: true })).toBeVisible();
  });

  test("shows total outstanding in aging section", async ({ page }) => {
    await page.goto("/payments/dashboard");
    await expect(page.getByText("Total Outstanding")).toBeVisible();
  });

  test("empty state renders without errors", async ({ page }) => {
    await page.route(`${API_BASE}/dashboard/payment-analytics*`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          ...mockPaymentAnalytics,
          totalCollected: 0,
          totalPayments: 0,
          totalOutstanding: 0,
          collectionRate: 0,
          paymentModeBreakdown: [],
          topCustomers: [],
        }),
      });
    });
    await page.goto("/payments/dashboard");
    await expect(page.getByText("Payment Analytics")).toBeVisible();
  });
});
