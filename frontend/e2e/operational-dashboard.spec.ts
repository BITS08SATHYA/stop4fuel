import { test, expect } from "@playwright/test";
import { mockApiRoute } from "./fixtures/test-helpers";

const API_BASE = "http://localhost:8080/api";

const mockTankInventories = [
  {
    id: 1,
    date: "2026-03-21",
    tank: { id: 1, name: "Tank 1", product: { name: "MS Petrol" } },
    openStock: 5000,
    openDip: 120,
    closeStock: 4500,
    closeDip: 110,
    incomeStock: 0,
    sales: 500,
  },
];

const mockNozzleInventories = [
  {
    id: 1,
    date: "2026-03-21",
    nozzle: { id: 1, nozzleName: "N1", pump: { name: "Pump 1" }, tank: { product: { name: "MS Petrol" } } },
    openMeterReading: 10000,
    closeMeterReading: 10500,
    sales: 500,
  },
];

const mockProductInventories = [
  {
    id: 1,
    date: "2026-03-21",
    product: { id: 1, name: "Coolant 1L" },
    openStock: 50,
    incomeStock: 0,
    closeStock: 48,
    sales: 2,
    rate: 200,
    amount: 400,
  },
];

const mockDashboardStats = {
  tanks: [{ id: 1, name: "Tank 1", capacity: 10000, availableStock: 4500, product: { name: "MS Petrol" } }],
  pumps: [{ id: 1, name: "Pump 1", status: "ACTIVE" }],
  nozzles: [{ id: 1, nozzleName: "N1", status: "ACTIVE" }],
};

test.describe("Operational Dashboard", () => {
  test.beforeEach(async ({ page }) => {
    await page.route(`${API_BASE}/inventory/tanks*`, (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockTankInventories) })
    );
    await page.route(`${API_BASE}/inventory/nozzles*`, (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockNozzleInventories) })
    );
    await page.route(`${API_BASE}/inventory/products*`, (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockProductInventories) })
    );
    await page.route(`${API_BASE}/dashboard/**`, (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockDashboardStats) })
    );
    await page.route(`${API_BASE}/**`, (route) => {
      const url = route.request().url();
      if (url.includes("/inventory") || url.includes("/dashboard")) return;
      route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
    });
  });

  test("loads operational dashboard", async ({ page }) => {
    await page.goto("/operations/dashboard");
    await expect(page.getByText(/operational/i).first()).toBeVisible();
  });

  test("displays tank status information", async ({ page }) => {
    await page.goto("/operations/dashboard");
    await expect(page.getByText(/tank/i).first()).toBeVisible();
  });

  test("shows inventory data", async ({ page }) => {
    await page.goto("/operations/dashboard");
    // Should show some inventory-related content
    await expect(page.locator("body")).toContainText(/stock|inventory|reading/i);
  });
});
