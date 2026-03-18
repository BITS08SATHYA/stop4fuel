import { test, expect } from "@playwright/test";
import { mockApiRoutes, expectFieldError, expectNoFieldError, clickAddButton, clickSubmitButton } from "./fixtures/test-helpers";

test.describe("Products Form Validation", () => {
  test.beforeEach(async ({ page }) => {
    await mockApiRoutes(page);
    await page.goto("/operations/products");
    await clickAddButton(page, "Add New Product");
  });

  test("shows error when submitting empty name and HSN", async ({ page }) => {
    // Clear the price field too (it starts empty)
    await clickSubmitButton(page, "Create Product");
    await expectFieldError(page, "Product name is required");
    await expectFieldError(page, "HSN code is required");
    await expectFieldError(page, "Price is required");
  });

  test("shows error for negative price", async ({ page }) => {
    await page.getByPlaceholder("e.g. Premium Petrol").fill("Test");
    await page.getByPlaceholder("e.g. 2710").fill("2710");
    await page.getByPlaceholder("0.00").fill("-5");
    await clickSubmitButton(page, "Create Product");
    await expectFieldError(page, "Price must be at least 0");
  });

  test("clears error on typing", async ({ page }) => {
    await clickSubmitButton(page, "Create Product");
    await expectFieldError(page, "Product name is required");
    await page.getByPlaceholder("e.g. Premium Petrol").fill("P");
    await expectNoFieldError(page, "Product name is required");
  });

  test("submits successfully with valid data", async ({ page }) => {
    await page.getByPlaceholder("e.g. Premium Petrol").fill("Petrol");
    await page.getByPlaceholder("e.g. 2710").fill("2710");
    await page.getByPlaceholder("0.00").fill("100");
    await clickSubmitButton(page, "Create Product");
    await expect(page.getByText("Product name is required")).not.toBeVisible();
  });
});
