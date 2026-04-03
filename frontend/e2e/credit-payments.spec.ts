import { test, expect } from "@playwright/test";
import { mockApiRoutes, API_BASE } from "./fixtures/test-helpers";

const mockCreditOverview = {
  totalOutstanding: 250000,
  totalAging0to30: 100000,
  totalAging31to60: 75000,
  totalAging61to90: 45000,
  totalAging90Plus: 30000,
  totalCreditCustomers: 2,
  totalCustomers: 15,
  customers: [
    {
      customerId: 1,
      customerName: "ABC Transport",
      phoneNumbers: ["9876543210"],
      groupName: "Transport",
      status: "ACTIVE",
      creditLimitAmount: 100000,
      ledgerBalance: 25000,
      totalBilled: 100000,
      totalPaid: 75000,
      totalOutstanding: 25000,
      aging0to30: 15000,
      aging31to60: 10000,
      aging61to90: 0,
      aging90Plus: 0,
      totalBillCount: 5,
      totalPaymentCount: 3,
      pendingStatementCount: 1,
    },
    {
      customerId: 2,
      customerName: "XYZ Logistics",
      phoneNumbers: ["9876543211"],
      groupName: "Corporate",
      status: "BLOCKED",
      creditLimitAmount: 200000,
      ledgerBalance: 50000,
      totalBilled: 200000,
      totalPaid: 150000,
      totalOutstanding: 50000,
      aging0to30: 20000,
      aging31to60: 15000,
      aging61to90: 10000,
      aging90Plus: 5000,
      totalBillCount: 10,
      totalPaymentCount: 5,
      pendingStatementCount: 2,
    },
  ],
};

const mockPayments = {
  content: [
    {
      id: 1,
      paymentDate: "2026-03-20T14:00:00",
      amount: 10000,
      paymentMode: { id: 1, name: "CASH" },
      customer: { id: 1, name: "ABC Transport" },
      referenceNo: "PAY-001",
      remarks: null,
      proofImageKey: null,
      statement: null,
      invoiceBill: null,
    },
    {
      id: 2,
      paymentDate: "2026-03-21T10:00:00",
      amount: 25000,
      paymentMode: { id: 2, name: "UPI" },
      customer: { id: 2, name: "XYZ Logistics" },
      referenceNo: "PAY-002",
      remarks: null,
      proofImageKey: null,
      statement: null,
      invoiceBill: null,
    },
  ],
  totalPages: 1,
  totalElements: 2,
  size: 10,
  number: 0,
  first: true,
  last: true,
  empty: false,
};

test.describe("Credit Overview Page", () => {
  test.beforeEach(async ({ page }) => {
    await mockApiRoutes(page);
    await page.route(`${API_BASE}/credit/overview`, (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockCreditOverview) })
    );
    // catch-all already handled by mockApiRoutes
  });

  test("loads credit overview page", async ({ page }) => {
    await page.goto("/payments/credit");
    await expect(page.getByText(/credit/i).first()).toBeVisible();
  });

  test("displays customer balances", async ({ page }) => {
    await page.goto("/payments/credit");
    await expect(page.getByText("ABC Transport")).toBeVisible();
    await expect(page.getByText("XYZ Logistics")).toBeVisible();
  });
});

test.describe("Payments Page", () => {
  test.beforeEach(async ({ page }) => {
    await mockApiRoutes(page);
    await page.route(`${API_BASE}/payments?*`, (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockPayments) })
    );
    await page.route(`${API_BASE}/payments`, (route) => {
      if (route.request().method() === "GET") {
        return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockPayments) });
      }
      return route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
    });
    await page.route(`${API_BASE}/payment-modes`, (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify([
        { id: 1, name: "CASH" },
        { id: 2, name: "UPI" },
      ]) })
    );
    // catch-all already handled by mockApiRoutes
  });

  test("loads payments page", async ({ page }) => {
    await page.goto("/payments");
    await expect(page.getByText(/payment/i).first()).toBeVisible();
  });

  test("displays payment records", async ({ page }) => {
    await page.goto("/payments");
    await expect(page.getByText("ABC Transport")).toBeVisible();
    await expect(page.getByText("XYZ Logistics")).toBeVisible();
  });
});

test.describe("Customer Ledger Page", () => {
  test.beforeEach(async ({ page }) => {
    await mockApiRoutes(page);
    // catch-all already handled by mockApiRoutes
  });

  test("loads ledger page", async ({ page }) => {
    await page.goto("/payments/ledger");
    await expect(page.getByText(/ledger/i).first()).toBeVisible();
  });
});
