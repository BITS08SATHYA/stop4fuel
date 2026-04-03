import { test, expect } from "@playwright/test";
import { API_BASE, DEV_USER } from "./fixtures/mock-data/users";

const mockDashboardStats = {
  todayRevenue: 45000,
  todayFuelVolume: 1200,
  todayInvoiceCount: 5,
  todayCashInvoices: 3,
  todayCreditInvoices: 2,
  activeShiftId: 1,
  activeShiftStartTime: "2026-03-21T06:00:00",
  shiftCash: 20000,
  shiftUpi: 10000,
  shiftCard: 5000,
  shiftExpense: 2000,
  shiftTotal: 35000,
  shiftNet: 33000,
  totalTanks: 2,
  activeTanks: 2,
  totalPumps: 2,
  activePumps: 2,
  totalNozzles: 10,
  activeNozzles: 8,
  totalOutstanding: 125000,
  totalCreditCustomers: 5,
  creditAging0to30: 50000,
  creditAging31to60: 30000,
  creditAging61to90: 25000,
  creditAging90Plus: 20000,
  dailyRevenue: [
    { date: "2026-03-15", revenue: 40000, fuelVolume: 1000, invoiceCount: 10 },
    { date: "2026-03-16", revenue: 42000, fuelVolume: 1100, invoiceCount: 12 },
    { date: "2026-03-17", revenue: 38000, fuelVolume: 950, invoiceCount: 8 },
  ],
  productSales: [
    { productName: "MS Petrol", quantity: 500, amount: 50000 },
    { productName: "HSD Diesel", quantity: 700, amount: 60000 },
  ],
  tankStatuses: [
    { tankId: 1, tankName: "Tank 1", capacity: 10000, currentStock: 7500, productName: "MS Petrol", active: true },
    { tankId: 2, tankName: "Tank 2", capacity: 12000, currentStock: 3000, productName: "HSD Diesel", active: true },
  ],
  recentInvoices: [
    {
      id: 1,
      date: "2026-03-21T10:00:00",
      customerName: "ABC Transport",
      billType: "CREDIT",
      amount: 5000,
      paymentStatus: "UNPAID",
    },
  ],
};

test.describe("Dashboard Page", () => {
  test.beforeEach(async ({ page }) => {
    // Set fake auth token so AuthProvider loads the user
    await page.addInitScript(() => {
      localStorage.setItem("sff-token", "mock-test-token");
      document.cookie = "sff-auth-session=mock-test-token; path=/";
    });
    // Single catch-all handler for all API routes
    await page.route(`${API_BASE}/**`, async (route) => {
      const url = route.request().url();
      const method = route.request().method();
      if (url.includes("/auth/me") && method === "GET") {
        return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(DEV_USER) });
      }
      if (url.includes("/dashboard/stats") && method === "GET") {
        return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockDashboardStats) });
      }
      if (method === "GET") {
        return route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
      }
      return route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
    });
  });

  test("loads dashboard page with heading", async ({ page }) => {
    await page.goto("/dashboard");
    await expect(page.getByRole("heading", { name: /dashboard/i })).toBeVisible();
  });

  test("displays KPI cards", async ({ page }) => {
    await page.goto("/dashboard");
    await expect(page.getByText(/today's revenue/i).first()).toBeVisible();
    await expect(page.getByText(/fuel volume sold/i).first()).toBeVisible();
  });

  test("shows active shift banner when shift is open", async ({ page }) => {
    await page.goto("/dashboard");
    await expect(page.getByText(/Shift #1/)).toBeVisible();
  });

  test("displays recent invoices table", async ({ page }) => {
    await page.goto("/dashboard");
    await expect(page.getByText("ABC Transport")).toBeVisible();
  });
});
