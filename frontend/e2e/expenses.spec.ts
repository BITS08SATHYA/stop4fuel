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

const mockExpenseTypes = [
  { id: 1, typeName: "Electricity", description: "TNEB bills" },
  { id: 2, typeName: "Maintenance", description: "Station maintenance" },
  { id: 3, typeName: "Salary", description: "Employee salaries" },
];

// The page fetches from /station-expenses (not /expenses)
const mockExpenses = [
  {
    id: 1,
    expenseDate: "2026-03-20",
    expenseType: mockExpenseTypes[0],
    amount: 15000,
    description: "March TNEB bill",
    paidTo: "TNEB",
    paymentMode: "CASH",
    recurringType: "MONTHLY",
  },
  {
    id: 2,
    expenseDate: "2026-03-18",
    expenseType: mockExpenseTypes[1],
    amount: 5000,
    description: "Pump repair",
    paidTo: "Local mechanic",
    paymentMode: "UPI",
    recurringType: "ONE_TIME",
  },
];

const mockSummary = {
  totalAmount: 20000,
  count: 2,
  byCategory: { "Electricity": 15000, "Maintenance": 5000 },
  from: "2026-03-01",
  to: "2026-03-21",
};

test.describe("Expenses Page", () => {
  test.beforeEach(async ({ page }) => {
    await page.route(`${API_BASE}/auth/me`, (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(DEV_USER) })
    );
    // The page calls getStationExpenses, getExpenseTypes, and getExpenseSummary
    await page.route(`${API_BASE}/station-expenses/summary*`, (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockSummary) })
    );
    await page.route(`${API_BASE}/station-expenses*`, (route) => {
      if (route.request().method() === "GET") {
        return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockExpenses) });
      }
      return route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
    });
    await page.route(`${API_BASE}/expense-types*`, (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockExpenseTypes) })
    );
    await page.route(`${API_BASE}/**`, (route) => {
      const url = route.request().url();
      if (url.includes("/auth/me") || url.includes("/station-expenses") || url.includes("/expense-types")) return;
      route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
    });
  });

  test("loads expenses page", async ({ page }) => {
    await page.goto("/operations/expenses");
    // Heading is "Station Expenses"
    await expect(page.getByText(/expense/i).first()).toBeVisible();
  });

  test("displays expense records", async ({ page }) => {
    await page.goto("/operations/expenses");
    // Table shows expense type names and descriptions
    await expect(page.getByText("Electricity").first()).toBeVisible();
  });

  test("has add expense button", async ({ page }) => {
    await page.goto("/operations/expenses");
    // Button text is "Add Expense"
    const addBtn = page.getByRole("button", { name: /add/i });
    await expect(addBtn.first()).toBeVisible();
  });
});

test.describe("Utility Bills Page", () => {
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

  test("loads utility bills page", async ({ page }) => {
    await page.goto("/operations/utility-bills");
    await expect(page.getByText(/utility|bill/i).first()).toBeVisible();
  });
});
