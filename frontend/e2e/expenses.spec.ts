import { test, expect } from "@playwright/test";
import { mockApiRoutes, API_BASE } from "./fixtures/test-helpers";

const mockExpenseTypes = [
  { id: 1, name: "Electricity", description: "TNEB bills" },
  { id: 2, name: "Maintenance", description: "Station maintenance" },
  { id: 3, name: "Salary", description: "Employee salaries" },
];

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
    await mockApiRoutes(page);
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
    // catch-all already handled by mockApiRoutes
  });

  test("loads expenses page", async ({ page }) => {
    await page.goto("/operations/expenses");
    await expect(page.getByText(/expense/i).first()).toBeVisible();
  });

  test("displays expense records", async ({ page }) => {
    await page.goto("/operations/expenses");
    await expect(page.getByText("Electricity").first()).toBeVisible();
  });

  test("has add expense button", async ({ page }) => {
    await page.goto("/operations/expenses");
    const addBtn = page.getByRole("button", { name: /add/i });
    await expect(addBtn.first()).toBeVisible();
  });
});

test.describe("Utility Bills Page", () => {
  test.beforeEach(async ({ page }) => {
    await mockApiRoutes(page);
  });

  test("loads utility bills page", async ({ page }) => {
    await page.goto("/operations/utility-bills");
    await expect(page.getByText(/utility|bill/i).first()).toBeVisible();
  });
});
