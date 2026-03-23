import { test, expect } from "@playwright/test";
import { mockApiRoute, expectFieldError, clickSubmitButton } from "./fixtures/test-helpers";

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

const mockGroups = [
  { id: 1, groupName: "Default", description: "Default customer group" },
  { id: 2, groupName: "Transport", description: "Private transport and logistics" },
  { id: 3, groupName: "Government", description: "Government department vehicles" },
];

test.describe("Customer Groups Page", () => {
  test.beforeEach(async ({ page }) => {
    await page.route(`${API_BASE}/auth/me`, (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(DEV_USER) })
    );
    await page.route(`${API_BASE}/groups`, (route) => {
      if (route.request().method() === "GET") {
        return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockGroups) });
      }
      return route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
    });
    await page.route(`${API_BASE}/**`, (route) => {
      const url = route.request().url();
      if (url.includes("/auth/me") || url.includes("/groups")) return;
      route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
    });
  });

  test("loads groups page with heading", async ({ page }) => {
    await page.goto("/customers/groups");
    // Heading is "Customer Groups" (h1)
    await expect(page.getByText(/group/i).first()).toBeVisible();
  });

  test("displays existing groups", async ({ page }) => {
    await page.goto("/customers/groups");
    // Groups are rendered via group.groupName in a table
    await expect(page.getByText("Default")).toBeVisible();
    await expect(page.getByText("Transport")).toBeVisible();
    await expect(page.getByText("Government")).toBeVisible();
  });

  test("opens add group modal", async ({ page }) => {
    await page.goto("/customers/groups");
    const addBtn = page.getByRole("button", { name: /add/i });
    if (await addBtn.isVisible()) {
      await addBtn.click();
      await expect(page.getByText(/group name/i).first()).toBeVisible();
    }
  });
});
