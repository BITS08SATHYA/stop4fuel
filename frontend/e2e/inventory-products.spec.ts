import { test, expect } from "@playwright/test";
import { expectFieldError, expectNoFieldError, clickSubmitButton } from "./fixtures/test-helpers";

const API_BASE = "http://localhost:8080/api";

const mockProducts = [
  { id: 1, name: "Engine Oil 5W30", hsnCode: "2710", price: 350, category: "Lubricant", unit: "Bottles", active: true },
];

async function mockRoutes(page: import("@playwright/test").Page) {
  await page.route(`${API_BASE}/**`, async (route) => {
    const url = route.request().url();
    const method = route.request().method();
    if (url.includes("/products") && !url.includes("/inventory") && method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockProducts) });
      return;
    }
    if (method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
    } else {
      await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
    }
  });
}

test.describe("Product Stock Validation", () => {
  test.beforeEach(async ({ page }) => {
    await mockRoutes(page);
    await page.goto("/operations/inventory/products");
    await page.getByRole("button", { name: "Record Inventory" }).click();
  });

  test("shows errors when submitting without required fields", async ({ page }) => {
    await clickSubmitButton(page, "Log Inventory Check");
    await expectFieldError(page, "Product is required");
    await expectFieldError(page, "Opening stock is required");
    await expectFieldError(page, "Closing stock is required");
  });

  test("clears error on selection", async ({ page }) => {
    await clickSubmitButton(page, "Log Inventory Check");
    await expectFieldError(page, "Product is required");
    await page.locator("form select").selectOption({ index: 1 });
    await expectNoFieldError(page, "Product is required");
  });

  test("submits successfully with valid data", async ({ page }) => {
    await page.locator("form select").selectOption({ index: 1 });
    await page.locator("form input[type='number']").nth(0).fill("10");
    await page.locator("form input[type='number']").nth(1).fill("0");
    await page.getByPlaceholder("Count them now").fill("8");
    await clickSubmitButton(page, "Log Inventory Check");
    await expect(page.getByText("Product is required")).not.toBeVisible();
  });
});
