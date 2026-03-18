import { test, expect } from "@playwright/test";

const API_BASE = "http://localhost:8080/api";

const mockEmployees = [
  { id: 1, name: "Ravi Kumar", designation: "Pump Operator", email: "ravi@test.com", phone: "9876543210", salary: 15000, joinDate: "2025-01-01", status: "Active" },
  { id: 2, name: "Priya S", designation: "Cashier", email: "priya@test.com", phone: "9876543211", salary: 12000, joinDate: "2025-03-01", status: "Active" },
];

async function mockAttendanceApiRoutes(page: import("@playwright/test").Page) {
  await page.route(`${API_BASE}/**`, async (route) => {
    const url = route.request().url();
    const method = route.request().method();
    if (url.includes("/employees") && !url.includes("/attendance") && method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockEmployees) });
      return;
    }
    if (method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
    } else {
      await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
    }
  });
}

test.describe("Daily Attendance", () => {
  test.beforeEach(async ({ page }) => {
    await mockAttendanceApiRoutes(page);
    await page.goto("/employees/attendance");
  });

  test("page loads with date picker and stats cards", async ({ page }) => {
    await expect(page.getByRole("heading", { name: "Daily Attendance" })).toBeVisible();
    await expect(page.locator("input[type='date']")).toBeVisible();
    // Stats cards
    await expect(page.getByText("Present")).toBeVisible();
    await expect(page.getByText("Absent")).toBeVisible();
    await expect(page.getByText("Half Day")).toBeVisible();
    await expect(page.getByText("On Leave")).toBeVisible();
    await expect(page.getByText("Unmarked")).toBeVisible();
  });

  test("displays employee list in table", async ({ page }) => {
    await expect(page.getByText("Ravi Kumar")).toBeVisible();
    await expect(page.getByText("Priya S")).toBeVisible();
  });

  test("status buttons are visible for employees", async ({ page }) => {
    // Each employee row should have P, A, H, L buttons
    const firstRow = page.locator("tbody tr").first();
    await expect(firstRow.getByTitle("Present")).toBeVisible();
    await expect(firstRow.getByTitle("Absent")).toBeVisible();
    await expect(firstRow.getByTitle("Half Day")).toBeVisible();
    await expect(firstRow.getByTitle("On Leave")).toBeVisible();
  });
});
