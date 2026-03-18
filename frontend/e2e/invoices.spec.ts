import { test, expect } from "@playwright/test";

const API_BASE = "http://localhost:8080/api";

const mockProducts = [
  { id: 1, name: "Petrol", hsnCode: "2710", price: 100, category: "Fuel", unit: "Liters", active: true },
  { id: 2, name: "Engine Oil", hsnCode: "2711", price: 350, category: "Lubricant", unit: "Bottles", active: true },
];

const mockNozzles = [
  { id: 1, nozzleName: "N-1", active: true, pump: { id: 1, name: "Pump 1" }, tank: { id: 1, name: "Tank 1", product: { id: 1, name: "Petrol" } } },
];

const mockCustomers = {
  content: [
    { id: 1, name: "ABC Transport", phoneNumbers: "9876543210", status: "ACTIVE", creditLimitLiters: 5000, consumedLiters: 1000 },
  ],
  totalPages: 1,
  totalElements: 1,
};

const mockVehicles = [
  { id: 1, vehicleNumber: "TN01AB1234", status: "ACTIVE", vehicleType: { name: "Truck" }, maxLitersPerMonth: 2000, consumedLiters: 500 },
];

async function mockRoutes(page: import("@playwright/test").Page) {
  await page.route(`${API_BASE}/**`, async (route) => {
    const url = route.request().url();
    const method = route.request().method();
    if (url.includes("/products") && method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockProducts) });
      return;
    }
    if (url.includes("/nozzles") && method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockNozzles) });
      return;
    }
    if (url.includes("/customers") && url.includes("/vehicles") && method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockVehicles) });
      return;
    }
    if (url.includes("/customers") && method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockCustomers) });
      return;
    }
    if (url.includes("/incentives") && method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
      return;
    }
    if (url.includes("/invoices") && method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
      return;
    }
    if (method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
    } else {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ id: 1, billNo: "INV-001" }) });
    }
  });
}

test.describe("Invoice Creation", () => {
  test.beforeEach(async ({ page }) => {
    await mockRoutes(page);
    await page.goto("/operations/invoices");
  });

  test("page loads with stepper and New Bill tab active", async ({ page }) => {
    await expect(page.getByRole("heading", { name: /Billing.*POS/ })).toBeVisible();
    await expect(page.getByText("Select Customer")).toBeVisible();
    await expect(page.getByRole("button", { name: "Walk-in Bill" })).toBeVisible();
  });

  test("walk-in button skips to products step", async ({ page }) => {
    await page.getByRole("button", { name: "Walk-in Bill" }).click();
    await expect(page.getByText("Add Products")).toBeVisible();
  });

  test("customer search shows suggestions", async ({ page }) => {
    await page.getByPlaceholder("Search Customer Name or Phone...").fill("ABC");
    await expect(page.getByText("ABC Transport")).toBeVisible();
  });

  test("selecting customer shows confirmation", async ({ page }) => {
    await page.getByPlaceholder("Search Customer Name or Phone...").fill("ABC");
    await page.getByText("ABC Transport").click();
    await expect(page.getByText("Customer Confirmed")).toBeVisible();
  });

  test("walk-in flow: add product shows product form", async ({ page }) => {
    await page.getByRole("button", { name: "Walk-in Bill" }).click();
    await expect(page.getByText("Add Products")).toBeVisible();
    await page.getByRole("button", { name: /Add Line/ }).click();
    // Product line form should appear with a select dropdown
    await expect(page.locator("select").last()).toBeVisible();
  });

  test("history tab switches view", async ({ page }) => {
    await page.getByRole("button", { name: "History" }).click();
    await expect(page.getByRole("heading", { name: "Recent Invoices" })).toBeVisible();
  });
});
