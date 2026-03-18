import { test, expect } from "@playwright/test";
import { expectFieldError, clickSubmitButton } from "./fixtures/test-helpers";

const API_BASE = "http://localhost:8080/api";

const mockEmployees = [
  { id: 1, name: "Ravi Kumar", designation: "Pump Operator", email: "ravi@test.com", phone: "9876543210", salary: 15000, joinDate: "2025-01-01", status: "Active" },
];

const mockLeaveTypes = [
  { id: 1, typeName: "Casual Leave", maxDaysPerYear: 12, carryForward: false, maxCarryForwardDays: 0 },
];

async function mockLeaveApiRoutes(page: import("@playwright/test").Page) {
  await page.route(`${API_BASE}/**`, async (route) => {
    const url = route.request().url();
    const method = route.request().method();
    if (url.includes("/employees") && method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockEmployees) });
      return;
    }
    if (url.includes("/leave-types") && method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockLeaveTypes) });
      return;
    }
    if (method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
    } else {
      await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
    }
  });
}

test.describe("Leave Management", () => {
  test.beforeEach(async ({ page }) => {
    await mockLeaveApiRoutes(page);
    await page.goto("/employees/leaves");
  });

  test("tab switching works", async ({ page }) => {
    // Default is Requests tab
    await expect(page.getByRole("button", { name: "Leave Requests" })).toBeVisible();

    // Switch to Types tab
    await page.getByRole("button", { name: "Leave Types" }).click();
    await expect(page.getByText("No Leave Types").or(page.getByText("Casual Leave"))).toBeVisible();

    // Switch to Balances tab
    await page.getByRole("button", { name: "Balances" }).click();
    await expect(page.getByText("Employee").first()).toBeVisible();
  });

  test("leave request: empty submit shows errors", async ({ page }) => {
    await page.getByRole("button", { name: "New Request" }).click();
    await clickSubmitButton(page, "Submit Request");
    await expectFieldError(page, "Employee is required");
    await expectFieldError(page, "Leave type is required");
    await expectFieldError(page, "From date is required");
    await expectFieldError(page, "To date is required");
  });

  test("leave request: valid submit closes modal", async ({ page }) => {
    await page.getByRole("button", { name: "New Request" }).click();
    // Fill required fields
    await page.locator("form select").nth(0).selectOption({ index: 1 });
    await page.locator("form select").nth(1).selectOption({ index: 1 });
    await page.locator("form input[type='date']").nth(0).fill("2026-03-20");
    await page.locator("form input[type='date']").nth(1).fill("2026-03-21");
    await clickSubmitButton(page, "Submit Request");
    // Modal should close
    await expect(page.getByText("Employee is required")).not.toBeVisible();
  });

  test("leave type: empty submit shows errors", async ({ page }) => {
    // Switch to Types tab
    await page.getByRole("button", { name: "Leave Types" }).click();
    await page.getByRole("button", { name: "Add Type" }).click();
    await clickSubmitButton(page, "Create");
    await expectFieldError(page, "Type name is required");
    await expectFieldError(page, "Max days is required");
  });

  test("leave type: valid submit closes modal", async ({ page }) => {
    await page.getByRole("button", { name: "Leave Types" }).click();
    await page.getByRole("button", { name: "Add Type" }).click();
    await page.locator("form input[type='text']").fill("Sick Leave");
    await page.locator("form input[type='number']").first().fill("10");
    await clickSubmitButton(page, "Create");
    await expect(page.getByText("Type name is required")).not.toBeVisible();
  });
});
