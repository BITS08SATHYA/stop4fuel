import { test, expect } from "@playwright/test";

const API_BASE = "http://localhost:8080/api";

const DEV_USER = {
  id: 1,
  cognitoId: "dev-user-001",
  username: "owner",
  name: "Dev Owner",
  email: "owner@stopforfuel.com",
  role: "OWNER",
  permissions: [
    "DASHBOARD_VIEW", "CUSTOMER_VIEW", "EMPLOYEE_VIEW", "PRODUCT_VIEW",
    "STATION_VIEW", "INVENTORY_VIEW", "SHIFT_VIEW", "INVOICE_VIEW",
    "PAYMENT_VIEW", "FINANCE_VIEW", "SETTINGS_VIEW",
  ],
};

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
      paymentMode: "CASH",
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
      paymentMode: "UPI",
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
    await page.route(`${API_BASE}/auth/me`, (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(DEV_USER) })
    );
    // The page fetches /credit/overview via fetchWithAuth
    await page.route(`${API_BASE}/credit/overview`, (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockCreditOverview) })
    );
    await page.route(`${API_BASE}/**`, (route) => {
      const url = route.request().url();
      if (url.includes("/auth/me") || url.includes("/credit/overview")) return;
      route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
    });
  });

  test("loads credit overview page", async ({ page }) => {
    await page.goto("/payments/credit");
    // Heading is "Credit Overview"
    await expect(page.getByText(/credit/i).first()).toBeVisible();
  });

  test("displays customer balances", async ({ page }) => {
    await page.goto("/payments/credit");
    // Customers are rendered via cust.customerName in the list
    await expect(page.getByText("ABC Transport")).toBeVisible();
    await expect(page.getByText("XYZ Logistics")).toBeVisible();
  });
});

test.describe("Payments Page", () => {
  test.beforeEach(async ({ page }) => {
    await page.route(`${API_BASE}/auth/me`, (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(DEV_USER) })
    );
    // The page calls getPayments (paginated)
    await page.route(`${API_BASE}/payments?*`, (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockPayments) })
    );
    await page.route(`${API_BASE}/payments`, (route) => {
      if (route.request().method() === "GET") {
        return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockPayments) });
      }
      return route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
    });
    await page.route(`${API_BASE}/**`, (route) => {
      const url = route.request().url();
      if (url.includes("/auth/me") || url.includes("/payments")) return;
      route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
    });
  });

  test("loads payments page", async ({ page }) => {
    await page.goto("/payments");
    // Heading is "Payment Tracking"
    await expect(page.getByText(/payment/i).first()).toBeVisible();
  });

  test("displays payment records", async ({ page }) => {
    await page.goto("/payments");
    // The table shows customer names and reference numbers
    await expect(page.getByText("ABC Transport")).toBeVisible();
    await expect(page.getByText("XYZ Logistics")).toBeVisible();
  });
});

test.describe("Customer Ledger Page", () => {
  test.beforeEach(async ({ page }) => {
    await page.route(`${API_BASE}/auth/me`, (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(DEV_USER) })
    );
    await page.route(`${API_BASE}/**`, (route) => {
      const url = route.request().url();
      if (url.includes("/auth/me")) return;
      route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
    });
  });

  test("loads ledger page", async ({ page }) => {
    await page.goto("/payments/ledger");
    await expect(page.getByText(/ledger/i).first()).toBeVisible();
  });
});
