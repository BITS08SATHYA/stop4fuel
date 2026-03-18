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
    if (url.includes("/products") && method === "GET") {
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

test.describe("Stock Transfer Validation", () => {
  test.beforeEach(async ({ page }) => {
    await mockRoutes(page);
    await page.goto("/operations/inventory/transfers");
    await page.getByRole("button", { name: "New Transfer" }).click();
  });

  test("shows errors when submitting empty form", async ({ page }) => {
    await clickSubmitButton(page, "Transfer Stock");
    await expectFieldError(page, "Product is required");
    await expectFieldError(page, "Quantity is required");
  });

  test("clears error on selection", async ({ page }) => {
    await clickSubmitButton(page, "Transfer Stock");
    await expectFieldError(page, "Product is required");
    await page.locator("form select").selectOption({ index: 1 });
    await expectNoFieldError(page, "Product is required");
  });

  test("submits successfully with valid data", async ({ page }) => {
    await page.locator("form select").selectOption({ index: 1 });
    await page.locator("form input[type='number']").fill("5");
    await clickSubmitButton(page, "Transfer Stock");
    await expect(page.getByText("Product is required")).not.toBeVisible();
  });
});
