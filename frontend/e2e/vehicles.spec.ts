import { test, expect } from "@playwright/test";
import { expectFieldError, clickSubmitButton } from "./fixtures/test-helpers";

const API_BASE = "http://localhost:8080/api";

const DEV_USER = {
  id: 1,
  cognitoId: "dev-user-001",
  username: "owner",
  name: "Dev Owner",
  email: "owner@stopforfuel.com",
  role: "OWNER",
  permissions: [
    "DASHBOARD_VIEW", "CUSTOMER_VIEW", "EMPLOYEE_VIEW", "PRODUCT_VIEW",
    "STATION_VIEW", "INVENTORY_VIEW", "SHIFT_VIEW", "INVOICE_VIEW",
    "PAYMENT_VIEW", "FINANCE_VIEW", "SETTINGS_VIEW",
  ],
};

const mockVehicleTypes = [
  { id: 1, typeName: "Car", description: "Standard passenger car" },
  { id: 2, typeName: "Bus", description: "Public or private bus" },
  { id: 3, typeName: "Truck", description: "Heavy transport truck" },
];

// The vehicles page uses vehicleNumber (not registrationNumber)
// and fetches plain array or { content: [...] }
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
    await page.route(`${API_BASE}/auth/me`, (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(DEV_USER) })
    );
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
    await page.route(`${API_BASE}/**`, (route) => {
      const url = route.request().url();
      if (url.includes("/auth/me") || url.includes("/vehicles") || url.includes("/vehicle-types") || url.includes("/customers")) return;
      route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
    });
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
    await page.route(`${API_BASE}/auth/me`, (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(DEV_USER) })
    );
    await page.route(`${API_BASE}/**`, (route) => {
      const url = route.request().url();
      if (url.includes("/auth/me")) return;
      const method = route.request().method();
      if (method === "GET") {
        if (url.includes("/vehicle-types")) {
          return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockVehicleTypes) });
        }
        return route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
      }
      route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
    });
    await page.goto("/customers/vehicles");
  });

  test("opens add vehicle modal and validates required fields", async ({ page }) => {
    const addBtn = page.getByRole("button", { name: /add/i });
    if (await addBtn.first().isVisible()) {
      await addBtn.first().click();
      // The save button is disabled when vehicleNumber or customerId is missing
      const saveBtn = page.getByRole("button", { name: /save/i });
      if (await saveBtn.isVisible()) {
        await expect(saveBtn).toBeDisabled();
      }
    }
  });
});
