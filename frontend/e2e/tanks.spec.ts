import { test, expect } from "@playwright/test";
import { mockApiRoutes, mockApiRoute, expectFieldError, expectNoFieldError, clickAddButton, clickSubmitButton } from "./fixtures/test-helpers";

test.describe("Tanks Form Validation", () => {
  test.beforeEach(async ({ page }) => {
    await mockApiRoutes(page);
    // Mock fuel products for the dropdown
    await mockApiRoute(page, "**/api/products/fuel", [
      { id: 1, name: "Petrol", hsnCode: "2710", price: 100, category: "Fuel", unit: "Liters", active: true },
    ]);
    await page.goto("/operations/tanks");
    await clickAddButton(page, "Add New Tank");
  });

  test("shows errors when submitting empty form", async ({ page }) => {
    await clickSubmitButton(page, "Save Tank");
    await expectFieldError(page, "Tank name is required");
    await expectFieldError(page, "Capacity is required");
    await expectFieldError(page, "Fuel product is required");
  });

  test("shows error for capacity less than 1", async ({ page }) => {
    await page.getByPlaceholder("e.g. Tank 1").fill("Tank 1");
    await page.getByPlaceholder("e.g. 10000").fill("0");
    await clickSubmitButton(page, "Save Tank");
    await expectFieldError(page, "Capacity must be at least 1");
  });

  test("clears errors on typing", async ({ page }) => {
    await clickSubmitButton(page, "Save Tank");
    await expectFieldError(page, "Tank name is required");
    await page.getByPlaceholder("e.g. Tank 1").fill("T");
    await expectNoFieldError(page, "Tank name is required");
  });

  test("submits successfully with valid data", async ({ page }) => {
    await page.getByPlaceholder("e.g. Tank 1").fill("Tank 1");
    await page.getByPlaceholder("e.g. 10000").fill("5000");
    // Select the fuel product dropdown inside the modal form
    await page.locator("form select").selectOption({ index: 1 });
    await clickSubmitButton(page, "Save Tank");
    await expect(page.getByText("Tank name is required")).not.toBeVisible();
  });
});
