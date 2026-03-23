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

test.describe("Sidebar Navigation", () => {
  test.beforeEach(async ({ page }) => {
    // Mock auth first (specific route), then catch-all
    await page.route(`${API_BASE}/auth/me`, (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(DEV_USER) })
    );
    await page.route(`${API_BASE}/**`, (route) => {
      const url = route.request().url();
      if (url.includes("/auth/me")) return;
      const method = route.request().method();
      if (method === "GET") {
        route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
      } else {
        route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
      }
    });
  });

  test("shows StopForFuel branding", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByText("StopForFuel")).toBeVisible();
  });

  test("shows user info with Dev Owner", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByText("Dev Owner")).toBeVisible();
    await expect(page.getByText("OWNER")).toBeVisible();
  });

  test("navigates to customers page", async ({ page }) => {
    await page.goto("/");
    // Sidebar has "Customers" link under Customer Management
    await page.getByRole("link", { name: "Customers" }).first().click();
    await expect(page).toHaveURL(/\/customers/);
  });

  test("navigates to products page", async ({ page }) => {
    await page.goto("/");
    await page.getByRole("link", { name: "Products" }).first().click();
    await expect(page).toHaveURL(/\/operations\/products/);
  });

  test("navigates to tanks page", async ({ page }) => {
    await page.goto("/");
    await page.getByRole("link", { name: "Tanks" }).first().click();
    await expect(page).toHaveURL(/\/operations\/tanks/);
  });

  test("navigates to employees page", async ({ page }) => {
    await page.goto("/");
    await page.getByRole("link", { name: "Employees" }).first().click();
    await expect(page).toHaveURL(/\/employees/);
  });

  test("navigates to invoices page", async ({ page }) => {
    await page.goto("/");
    // The sidebar has "Invoices" link under Invoice Management
    await page.getByRole("link", { name: "Invoices", exact: true }).click();
    await expect(page).toHaveURL(/\/operations\/invoices/);
  });

  test("navigates to shift register page", async ({ page }) => {
    await page.goto("/");
    await page.getByRole("link", { name: "Shift Register" }).click();
    await expect(page).toHaveURL(/\/operations\/shifts/);
  });

  test("has theme toggle button", async ({ page }) => {
    await page.goto("/");
    // ThemeToggle has aria-label="Toggle theme"
    const themeToggle = page.getByRole("button", { name: "Toggle theme" });
    await expect(themeToggle).toBeVisible();
  });
});
