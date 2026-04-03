import { test, expect } from "@playwright/test";
import { mockApiRoutes } from "./fixtures/test-helpers";

test.describe("Sidebar Navigation", () => {
  test.beforeEach(async ({ page }) => {
    await mockApiRoutes(page);
    await page.goto("/customers");
    // Wait for sidebar to be visible
    await expect(page.getByText("StopForFuel")).toBeVisible();
  });

  test("shows StopForFuel branding", async ({ page }) => {
    await expect(page.getByText("StopForFuel")).toBeVisible();
  });

  test("shows user info in sidebar", async ({ page }) => {
    await expect(page.getByText("Dev Owner")).toBeVisible();
  });

  test("navigates to customers page", async ({ page }) => {
    await page.getByRole("link", { name: "Customers" }).first().click();
    await expect(page).toHaveURL(/\/customers/);
  });

  test("navigates to products page", async ({ page }) => {
    await page.getByRole("link", { name: "Products" }).first().click();
    await expect(page).toHaveURL(/\/operations\/products/);
  });

  test("navigates to tanks page", async ({ page }) => {
    await page.getByRole("link", { name: "Tanks" }).first().click();
    await expect(page).toHaveURL(/\/operations\/tanks/);
  });

  test("navigates to employees page", async ({ page }) => {
    await page.getByRole("link", { name: "Employees" }).first().click();
    await expect(page).toHaveURL(/\/employees/);
  });

  test("navigates to invoices page", async ({ page }) => {
    await page.getByRole("link", { name: "Invoices", exact: true }).click();
    await expect(page).toHaveURL(/\/operations\/invoices/);
  });

  test("navigates to shift register page", async ({ page }) => {
    await page.getByRole("link", { name: "Shift Register" }).click();
    await expect(page).toHaveURL(/\/operations\/shifts/);
  });

  test("has theme toggle button", async ({ page }) => {
    const themeToggle = page.getByRole("button", { name: "Toggle theme" });
    await expect(themeToggle).toBeVisible();
  });
});
