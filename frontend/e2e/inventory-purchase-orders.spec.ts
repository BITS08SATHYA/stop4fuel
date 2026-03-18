import { test, expect } from "@playwright/test";
import { expectFieldError, expectNoFieldError, clickSubmitButton } from "./fixtures/test-helpers";

const API_BASE = "http://localhost:8080/api";

const mockProducts = [
  { id: 1, name: "Engine Oil 5W30", hsnCode: "2710", price: 350, category: "Lubricant", unit: "Bottles", active: true },
];

const mockSuppliers = [
  { id: 1, name: "ABC Suppliers", contactPerson: "John", phone: "9876543210", email: "abc@test.com", active: true },
];

async function mockRoutes(page: import("@playwright/test").Page) {
  await page.route(`${API_BASE}/**`, async (route) => {
    const url = route.request().url();
    const method = route.request().method();
    if (url.includes("/products") && method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockProducts) });
      return;
    }
    if (url.includes("/suppliers") && method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockSuppliers) });
      return;
    }
    if (method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
    } else {
      await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
    }
  });
}

test.describe("Purchase Orders Validation", () => {
  test.beforeEach(async ({ page }) => {
    await mockRoutes(page);
    await page.goto("/operations/inventory/purchase-orders");
    await page.getByRole("button", { name: "Create PO" }).first().click();
  });

  test("shows error when submitting without supplier", async ({ page }) => {
    // Fill line items to bypass HTML required on those fields
    await page.locator("form select").nth(1).selectOption({ index: 1 });
    await page.getByPlaceholder("Qty").fill("10");
    await page.locator("form").getByRole("button", { name: "Create PO" }).click();
    await expectFieldError(page, "Supplier is required");
  });

  test("clears error on selection", async ({ page }) => {
    await page.locator("form select").nth(1).selectOption({ index: 1 });
    await page.getByPlaceholder("Qty").fill("10");
    await page.locator("form").getByRole("button", { name: "Create PO" }).click();
    await expectFieldError(page, "Supplier is required");
    await page.locator("form select").first().selectOption({ index: 1 });
    await expectNoFieldError(page, "Supplier is required");
  });

  test("submits successfully with valid data", async ({ page }) => {
    // Supplier
    await page.locator("form select").first().selectOption({ index: 1 });
    // Line item product and qty (these have HTML required still)
    await page.locator("form select").nth(1).selectOption({ index: 1 });
    await page.getByPlaceholder("Qty").fill("10");
    await page.locator("form").getByRole("button", { name: "Create PO" }).click();
    await expect(page.getByText("Supplier is required")).not.toBeVisible();
  });
});
