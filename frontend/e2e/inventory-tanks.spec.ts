import { test, expect } from "@playwright/test";
import { expectFieldError, expectNoFieldError, clickSubmitButton } from "./fixtures/test-helpers";

const API_BASE = "http://localhost:8080/api";

const mockTanks = [
  { id: 1, name: "Tank 1", capacity: 10000, active: true, product: { id: 1, name: "Petrol" } },
];

async function mockRoutes(page: import("@playwright/test").Page) {
  await page.route(`${API_BASE}/**`, async (route) => {
    const url = route.request().url();
    const method = route.request().method();
    if (url.includes("/tanks") && !url.includes("/inventory") && method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockTanks) });
      return;
    }
    if (method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
    } else {
      await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
    }
  });
}

test.describe("Tank Dip Readings Validation", () => {
  test.beforeEach(async ({ page }) => {
    await mockRoutes(page);
    await page.goto("/operations/inventory/tanks");
    await page.getByRole("button", { name: "Record Tank Dip" }).click();
  });

  test("shows errors when submitting without required fields", async ({ page }) => {
    await clickSubmitButton(page, "Confirm Dip Reading");
    await expectFieldError(page, "Tank is required");
    await expectFieldError(page, "Open dip is required");
    await expectFieldError(page, "Open stock is required");
    await expectFieldError(page, "Close dip is required");
    await expectFieldError(page, "Close stock is required");
  });

  test("clears error on input", async ({ page }) => {
    await clickSubmitButton(page, "Confirm Dip Reading");
    await expectFieldError(page, "Open dip is required");
    await page.getByPlaceholder("e.g. 150.5").fill("120");
    await expectNoFieldError(page, "Open dip is required");
  });

  test("submits successfully with valid data", async ({ page }) => {
    await page.locator("form select").selectOption({ index: 1 });
    await page.getByPlaceholder("e.g. 150.5").fill("150");
    await page.locator("form input[type='number']").nth(0).fill("5000");
    await page.locator("form input[type='number']").nth(1).fill("0");
    await page.getByPlaceholder("e.g. 142.2").fill("140");
    await page.locator("form input[type='number']").nth(2).fill("4500");
    await clickSubmitButton(page, "Confirm Dip Reading");
    await expect(page.getByText("Tank is required")).not.toBeVisible();
  });
});
