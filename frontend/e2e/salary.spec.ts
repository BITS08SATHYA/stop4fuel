import { test, expect } from "@playwright/test";

const API_BASE = "http://localhost:8080/api";

const mockPayments = [
  {
    id: 1,
    employee: { id: 1, name: "Ravi Kumar", designation: "Pump Operator" },
    month: 3,
    year: 2026,
    baseSalary: 15000,
    advanceDeduction: 2000,
    incentiveAmount: 500,
    otherDeductions: 0,
    netPayable: 13500,
    status: "DRAFT",
    paymentDate: null,
  },
];

async function mockSalaryApiRoutes(page: import("@playwright/test").Page, withData = false) {
  await page.route(`${API_BASE}/**`, async (route) => {
    const url = route.request().url();
    const method = route.request().method();
    if (url.includes("/salary/process") && method === "POST") {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockPayments) });
      return;
    }
    if (url.includes("/salary") && method === "GET") {
      const data = withData ? mockPayments : [];
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(data) });
      return;
    }
    if (method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
    } else {
      await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
    }
  });
}

test.describe("Salary Processing", () => {
  test("page loads with month/year selectors", async ({ page }) => {
    await mockSalaryApiRoutes(page);
    await page.goto("/employees/salary");
    await expect(page.getByRole("heading", { name: /Salary.*Processing/ })).toBeVisible();
    // Month selector
    await expect(page.locator("select")).toBeVisible();
    // Process button
    await expect(page.getByRole("button", { name: "Process Payroll" })).toBeVisible();
  });

  test("shows empty state when no payroll data", async ({ page }) => {
    await mockSalaryApiRoutes(page);
    await page.goto("/employees/salary");
    await expect(page.getByText("No Payroll Data")).toBeVisible();
  });

  test("process payroll button triggers processing and shows data", async ({ page }) => {
    await mockSalaryApiRoutes(page);
    await page.goto("/employees/salary");
    await page.getByRole("button", { name: "Process Payroll" }).click();
    // After processing, employee data should appear
    await expect(page.getByText("Ravi Kumar")).toBeVisible();
    await expect(page.getByText("DRAFT", { exact: true })).toBeVisible();
  });
});
