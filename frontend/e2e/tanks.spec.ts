import { test, expect } from "@playwright/test";
import { expectFieldError, expectNoFieldError, clickAddButton, clickSubmitButton } from "./fixtures/test-helpers";

const API_BASE = "http://localhost:8080/api";

const fuelProducts = [
  { id: 1, name: "Petrol", hsnCode: "2710", price: 100, category: "Fuel", unit: "Liters", active: true },
];

async function mockTankApiRoutes(page: import("@playwright/test").Page) {
  await page.route(`${API_BASE}/**`, async (route) => {
    const url = route.request().url();
    const method = route.request().method();
    // Return fuel products for the dropdown
    if (url.includes("/products/category/Fuel") && method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(fuelProducts) });
      return;
    }
    if (method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
    } else {
      await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
    }
  });
}

test.describe("Tanks Form Validation", () => {
  test.beforeEach(async ({ page }) => {
    await mockTankApiRoutes(page);
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
    await page.locator("form select").selectOption({ index: 1 });
    await clickSubmitButton(page, "Save Tank");
    await expect(page.getByText("Tank name is required")).not.toBeVisible();
  });
});
