import { test, expect } from "@playwright/test";
import { expectFieldError, expectNoFieldError, clickSubmitButton } from "./fixtures/test-helpers";

const API_BASE = "http://localhost:8080/api";

const mockNozzles = [
  { id: 1, nozzleName: "N-1", active: true, pump: { id: 1, name: "Pump 1" }, tank: { id: 1, name: "Tank 1", product: { id: 1, name: "Petrol" } } },
];

async function mockRoutes(page: import("@playwright/test").Page) {
  await page.route(`${API_BASE}/**`, async (route) => {
    const url = route.request().url();
    const method = route.request().method();
    if (url.includes("/nozzles") && !url.includes("/inventory") && method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockNozzles) });
      return;
    }
    if (method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
    } else {
      await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
    }
  });
}

test.describe("Nozzle Meter Readings Validation", () => {
  test.beforeEach(async ({ page }) => {
    await mockRoutes(page);
    await page.goto("/operations/inventory/nozzles");
    await page.getByRole("button", { name: "Add Daily Reading" }).click();
  });

  test("shows errors when submitting without required fields", async ({ page }) => {
    await clickSubmitButton(page, "Save Reading");
    await expectFieldError(page, "Nozzle is required");
    await expectFieldError(page, "Open reading is required");
    await expectFieldError(page, "Close reading is required");
  });

  test("clears error on input", async ({ page }) => {
    await clickSubmitButton(page, "Save Reading");
    await expectFieldError(page, "Open reading is required");
    await page.getByPlaceholder("0.00").first().fill("100");
    await expectNoFieldError(page, "Open reading is required");
  });

  test("submits successfully with valid data", async ({ page }) => {
    await page.locator("form select").selectOption({ index: 1 });
    await page.getByPlaceholder("0.00").first().fill("1000");
    await page.getByPlaceholder("0.00").last().fill("1500");
    await clickSubmitButton(page, "Save Reading");
    await expect(page.getByText("Nozzle is required")).not.toBeVisible();
  });
});
