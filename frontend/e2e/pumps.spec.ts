import { test, expect } from "@playwright/test";
import { mockApiRoutes, expectFieldError, expectNoFieldError, clickAddButton, clickSubmitButton } from "./fixtures/test-helpers";

test.describe("Pumps Form Validation", () => {
  test.beforeEach(async ({ page }) => {
    await mockApiRoutes(page);
    await page.goto("/operations/pumps");
    await clickAddButton(page, "Add New Pump");
  });

  test("shows error when submitting empty name", async ({ page }) => {
    await clickSubmitButton(page, "Save Pump");
    await expectFieldError(page, "Pump name is required");
  });

  test("clears error on typing", async ({ page }) => {
    await clickSubmitButton(page, "Save Pump");
    await expectFieldError(page, "Pump name is required");
    await page.getByPlaceholder("e.g. Pump 1").fill("P");
    await expectNoFieldError(page, "Pump name is required");
  });

  test("submits successfully with valid data", async ({ page }) => {
    await page.getByPlaceholder("e.g. Pump 1").fill("Pump 1");
    await clickSubmitButton(page, "Save Pump");
    await expect(page.getByText("Pump name is required")).not.toBeVisible();
  });
});
