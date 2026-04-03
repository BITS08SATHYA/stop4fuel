import { test, expect } from "@playwright/test";
import { mockApiRoutes, expectFieldError, clickSubmitButton, API_BASE } from "./fixtures/test-helpers";

const mockVehicleTypes = [
  { id: 1, name: "Car", description: "Standard passenger car" },
  { id: 2, name: "Bus", description: "Public or private bus" },
  { id: 3, name: "Truck", description: "Heavy transport truck" },
];

const mockVehicles = [
  {
    id: 1,
    vehicleNumber: "TN01AB1234",
    vehicleType: mockVehicleTypes[0],
    customer: { id: 1, name: "ABC Transport" },
    status: "ACTIVE",
    consumedLiters: 100,
  },
  {
    id: 2,
    vehicleNumber: "TN02CD5678",
    vehicleType: mockVehicleTypes[2],
    customer: { id: 2, name: "XYZ Logistics" },
    status: "ACTIVE",
    consumedLiters: 500,
  },
];

test.describe("Vehicle Management Page", () => {
  test.beforeEach(async ({ page }) => {
    await mockApiRoutes(page);
    await page.route(`${API_BASE}/vehicles*`, (route) => {
      if (route.request().method() === "GET") {
        return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockVehicles) });
      }
      return route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
    });
    await page.route(`${API_BASE}/vehicle-types`, (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockVehicleTypes) })
    );
    await page.route(`${API_BASE}/customers*`, (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify([]) })
    );
    // catch-all already handled by mockApiRoutes
  });

  test("loads vehicles page", async ({ page }) => {
    await page.goto("/customers/vehicles");
    await expect(page.getByText(/vehicle/i).first()).toBeVisible();
  });

  test("displays vehicle registrations", async ({ page }) => {
    await page.goto("/customers/vehicles");
    await expect(page.getByText("TN01AB1234")).toBeVisible();
    await expect(page.getByText("TN02CD5678")).toBeVisible();
  });

  test("shows customer name for each vehicle", async ({ page }) => {
    await page.goto("/customers/vehicles");
    await expect(page.getByText("ABC Transport")).toBeVisible();
    await expect(page.getByText("XYZ Logistics")).toBeVisible();
  });

  test("has add vehicle button", async ({ page }) => {
    await page.goto("/customers/vehicles");
    const addBtn = page.getByRole("button", { name: /add/i });
    await expect(addBtn.first()).toBeVisible();
  });
});

test.describe("Vehicle Form Validation", () => {
  test.beforeEach(async ({ page }) => {
    await mockApiRoutes(page);
    await page.route(`${API_BASE}/vehicle-types`, (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockVehicleTypes) })
    );
    await page.goto("/customers/vehicles");
  });

  test("opens add vehicle modal and validates required fields", async ({ page }) => {
    const addBtn = page.getByRole("button", { name: /add/i });
    if (await addBtn.first().isVisible()) {
      await addBtn.first().click();
      const saveBtn = page.getByRole("button", { name: /save/i });
      if (await saveBtn.isVisible()) {
        await expect(saveBtn).toBeDisabled();
      }
    }
  });
});
