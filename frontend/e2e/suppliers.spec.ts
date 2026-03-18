import { test, expect } from "@playwright/test";
import { mockApiRoutes, expectFieldError, expectNoFieldError, clickAddButton, clickSubmitButton } from "./fixtures/test-helpers";

test.describe("Suppliers Form Validation", () => {
  test.beforeEach(async ({ page }) => {
    await mockApiRoutes(page);
    await page.goto("/operations/suppliers");
    await clickAddButton(page, "Add New Supplier");
  });

  test("shows error when submitting empty name", async ({ page }) => {
    await clickSubmitButton(page, "Save Supplier");
    await expectFieldError(page, "Supplier name is required");
  });

  test("shows error for invalid email", async ({ page }) => {
    await page.getByPlaceholder("e.g. Acme Petroleum Ltd.").fill("Test Supplier");
    await page.getByPlaceholder("order@supplier.com").fill("not-an-email");
    await clickSubmitButton(page, "Save Supplier");
    await expectFieldError(page, "Invalid email address");
  });

  test("shows error for invalid phone", async ({ page }) => {
    await page.getByPlaceholder("e.g. Acme Petroleum Ltd.").fill("Test Supplier");
    await page.getByPlaceholder("+91 00000 00000").fill("abc");
    await clickSubmitButton(page, "Save Supplier");
    await expectFieldError(page, "Invalid phone number");
  });

  test("clears error on typing", async ({ page }) => {
    await clickSubmitButton(page, "Save Supplier");
    await expectFieldError(page, "Supplier name is required");
    await page.getByPlaceholder("e.g. Acme Petroleum Ltd.").fill("A");
    await expectNoFieldError(page, "Supplier name is required");
  });

  test("submits successfully with valid data", async ({ page }) => {
    await page.getByPlaceholder("e.g. Acme Petroleum Ltd.").fill("Valid Supplier");
    await page.getByPlaceholder("order@supplier.com").fill("valid@email.com");
    await page.getByPlaceholder("+91 00000 00000").fill("+91 98765 43210");
    await clickSubmitButton(page, "Save Supplier");
    // Modal should close (no validation errors)
    await expect(page.getByText("Supplier name is required")).not.toBeVisible();
  });
});
