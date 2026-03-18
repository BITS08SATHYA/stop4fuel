import { test, expect } from "@playwright/test";
import { expectFieldError, expectNoFieldError, clickAddButton, clickSubmitButton } from "./fixtures/test-helpers";

const API_BASE = "http://localhost:8080/api";

const mockTanks = [
  { id: 1, name: "Tank 1", capacity: 10000, active: true, product: { id: 1, name: "Petrol" } },
];

const mockPumps = [
  { id: 1, name: "Pump 1", active: true },
];

async function mockNozzleApiRoutes(page: import("@playwright/test").Page) {
  await page.route(`${API_BASE}/**`, async (route) => {
    const url = route.request().url();
    const method = route.request().method();
    if (url.includes("/tanks") && method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockTanks) });
      return;
    }
    if (url.includes("/pumps") && method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockPumps) });
      return;
    }
    if (method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
    } else {
      await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
    }
  });
}

test.describe("Nozzles Form Validation", () => {
  test.beforeEach(async ({ page }) => {
    await mockNozzleApiRoutes(page);
    await page.goto("/operations/nozzles");
    await clickAddButton(page, "Add New Nozzle");
  });

  test("shows errors when submitting empty form", async ({ page }) => {
    await clickSubmitButton(page, "Save Nozzle");
    await expectFieldError(page, "Nozzle name is required");
    await expectFieldError(page, "Pump is required");
    await expectFieldError(page, "Tank is required");
  });

  test("clears error on typing", async ({ page }) => {
    await clickSubmitButton(page, "Save Nozzle");
    await expectFieldError(page, "Nozzle name is required");
    await page.getByPlaceholder("e.g. N-1").fill("N");
    await expectNoFieldError(page, "Nozzle name is required");
  });

  test("clears pump error on selection", async ({ page }) => {
    await clickSubmitButton(page, "Save Nozzle");
    await expectFieldError(page, "Pump is required");
    await page.locator("form select").first().selectOption({ index: 1 });
    await expectNoFieldError(page, "Pump is required");
  });

  test("submits successfully with valid data", async ({ page }) => {
    await page.getByPlaceholder("e.g. N-1").fill("Nozzle 1");
    // Select pump (first select) and tank (second select)
    await page.locator("form select").nth(0).selectOption({ index: 1 });
    await page.locator("form select").nth(1).selectOption({ index: 1 });
    await clickSubmitButton(page, "Save Nozzle");
    await expect(page.getByText("Nozzle name is required")).not.toBeVisible();
  });
});
