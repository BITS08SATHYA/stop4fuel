import { test, expect } from "@playwright/test";

const API_BASE = "http://localhost:8080/api";

const mockProducts = [
  { id: 1, name: "Petrol", hsnCode: "2710", price: 100, category: "Fuel", unit: "Liters", active: true },
];

const mockNozzles = [
  { id: 1, nozzleName: "N-1", active: true, pump: { id: 1, name: "Pump 1" }, tank: { id: 1, name: "Tank 1", product: { id: 1, name: "Petrol" } } },
];

const mockCustomerA = { id: 1, name: "Customer A - Owner", phoneNumbers: "9876543210", status: "ACTIVE" };
const mockCustomerB = { id: 2, name: "Customer B - Renter", phoneNumbers: "9876543211", status: "ACTIVE" };

const mockCustomers = {
  content: [mockCustomerA, mockCustomerB],
  totalPages: 1,
  totalElements: 2,
};

// Vehicle owned by Customer A
const vehicleOwnedByA = {
  id: 1, vehicleNumber: "TN01AB1234", status: "ACTIVE",
  vehicleType: { name: "Truck" }, maxLitersPerMonth: 2000, consumedLiters: 500,
  customer: mockCustomerA,
};

// Vehicle owned by Customer B
const vehicleOwnedByB = {
  id: 2, vehicleNumber: "TN02CD5678", status: "ACTIVE",
  vehicleType: { name: "Car" },
  customer: mockCustomerB,
};

// Blocked vehicle
const blockedVehicle = {
  id: 3, vehicleNumber: "TN03EF9999", status: "BLOCKED",
  vehicleType: { name: "Truck" },
  customer: mockCustomerA,
};

async function mockRoutes(page: import("@playwright/test").Page) {
  await page.route(`${API_BASE}/**`, async (route) => {
    const url = route.request().url();
    const method = route.request().method();

    // Vehicle search endpoint — returns all vehicles matching query
    if (url.includes("/vehicles/search") && method === "GET") {
      const urlObj = new URL(url);
      const q = (urlObj.searchParams.get("q") || "").toLowerCase();
      const allVehicles = [vehicleOwnedByA, vehicleOwnedByB, blockedVehicle];
      const matched = allVehicles.filter(v => v.vehicleNumber.toLowerCase().includes(q));
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(matched) });
      return;
    }

    // Customer's own vehicles
    if (url.includes("/customers/") && url.includes("/vehicles") && method === "GET") {
      // Extract customer ID from URL
      const match = url.match(/customers\/(\d+)\/vehicles/);
      const custId = match ? parseInt(match[1]) : 0;
      const custVehicles = [vehicleOwnedByA, vehicleOwnedByB, blockedVehicle].filter(v => v.customer.id === custId);
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(custVehicles) });
      return;
    }

    if (url.includes("/products") && method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockProducts) });
      return;
    }
    if (url.includes("/nozzles") && method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockNozzles) });
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

async function goToStep2AsCustomerB(page: import("@playwright/test").Page) {
  await page.goto("/operations/invoices");
  // Search and select Customer B (the renter)
  await page.getByPlaceholder("Search Customer Name or Phone...").fill("Customer B");
  await page.getByText("Customer B - Renter").click();
  await expect(page.getByText("Customer Confirmed")).toBeVisible();
  // Go to Step 2
  await page.getByRole("button", { name: "Next", exact: true }).click();
  await expect(page.getByText("Select Vehicle")).toBeVisible();
}

test.describe("Cross-Customer Vehicle Selection", () => {
  test.beforeEach(async ({ page }) => {
    await mockRoutes(page);
  });

  test("Step 2 shows customer's own vehicles", async ({ page }) => {
    await goToStep2AsCustomerB(page);
    // Customer B owns TN02CD5678
    await expect(page.getByText("TN02CD5678")).toBeVisible();
    // Customer A's vehicle should NOT be in the grid (only in search)
    await expect(page.locator(".grid").getByText("TN01AB1234")).not.toBeVisible();
  });

  test("vehicle search bar is visible in Step 2", async ({ page }) => {
    await goToStep2AsCustomerB(page);
    await expect(page.getByPlaceholder(/Search any vehicle number/)).toBeVisible();
    await expect(page.getByText("Or search for another vehicle")).toBeVisible();
  });

  test("searching shows vehicles from other customers", async ({ page }) => {
    await goToStep2AsCustomerB(page);
    await page.getByPlaceholder(/Search any vehicle number/).fill("TN01");
    // Wait for debounced search results
    await expect(page.getByText("TN01AB1234")).toBeVisible({ timeout: 5000 });
    // Should show owner name
    await expect(page.getByText("Owner: Customer A - Owner")).toBeVisible();
  });

  test("selecting non-owned vehicle shows info banner", async ({ page }) => {
    await goToStep2AsCustomerB(page);
    // Search for Customer A's vehicle
    await page.getByPlaceholder(/Search any vehicle number/).fill("TN01");
    await expect(page.getByText("TN01AB1234")).toBeVisible({ timeout: 5000 });
    // Click the vehicle card in search results
    await page.locator("button", { hasText: "TN01AB1234" }).click();
    // Info banner should appear
    await expect(page.getByText(/This vehicle belongs to.*Customer A - Owner/)).toBeVisible();
    await expect(page.getByText(/bill will be charged to.*Customer B - Renter/)).toBeVisible();
  });

  test("selecting own vehicle does NOT show info banner", async ({ page }) => {
    await goToStep2AsCustomerB(page);
    // Select Customer B's own vehicle
    await page.locator("button", { hasText: "TN02CD5678" }).click();
    // No info banner
    await expect(page.getByText(/This vehicle belongs to/)).not.toBeVisible();
  });

  test("can proceed to Step 3 with non-owned vehicle", async ({ page }) => {
    await goToStep2AsCustomerB(page);
    // Search and select Customer A's vehicle
    await page.getByPlaceholder(/Search any vehicle number/).fill("TN01");
    await expect(page.getByText("TN01AB1234")).toBeVisible({ timeout: 5000 });
    await page.locator("button", { hasText: "TN01AB1234" }).click();
    // Next button should be enabled
    const nextBtn = page.getByRole("button", { name: "Next", exact: true });
    await expect(nextBtn).toBeEnabled();
    await nextBtn.click();
    // Step 3: Products
    await expect(page.getByText("Add Products")).toBeVisible();
  });

  test("search with no results shows message", async ({ page }) => {
    await goToStep2AsCustomerB(page);
    await page.getByPlaceholder(/Search any vehicle number/).fill("ZZ99");
    // Wait for debounce
    await expect(page.getByText(/No other vehicles found matching/)).toBeVisible({ timeout: 5000 });
  });

  test("search excludes customer's own vehicles from results", async ({ page }) => {
    await goToStep2AsCustomerB(page);
    // Search for TN02 which is Customer B's own vehicle
    await page.getByPlaceholder(/Search any vehicle number/).fill("TN02");
    // TN02CD5678 is already shown in the grid above, should not appear in search results
    // The "no results" message should show since the only match is filtered out
    await expect(page.getByText(/No other vehicles found matching/)).toBeVisible({ timeout: 5000 });
  });

  test("blocked vehicle shows blocked warning when selected", async ({ page }) => {
    // Go as Customer A who owns the blocked vehicle
    await page.goto("/operations/invoices");
    await page.getByPlaceholder("Search Customer Name or Phone...").fill("Customer A");
    await page.getByText("Customer A - Owner").click();
    await page.getByRole("button", { name: "Next", exact: true }).click();
    await expect(page.getByText("Select Vehicle")).toBeVisible();
    // Select blocked vehicle TN03EF9999
    await page.locator("button", { hasText: "TN03EF9999" }).click();
    await expect(page.getByText(/This vehicle is BLOCKED/)).toBeVisible();
    // Next button should be disabled
    await expect(page.getByRole("button", { name: "Next", exact: true }).last()).toBeDisabled();
  });

  test("full flow: non-owned vehicle through to confirm step", async ({ page }) => {
    await goToStep2AsCustomerB(page);
    // Search and select Customer A's vehicle
    await page.getByPlaceholder(/Search any vehicle number/).fill("TN01");
    await expect(page.getByText("TN01AB1234")).toBeVisible({ timeout: 5000 });
    await page.locator("button", { hasText: "TN01AB1234" }).click();

    // Step 2 → Step 3
    await page.getByRole("button", { name: "Next", exact: true }).click();
    await expect(page.getByText("Add Products")).toBeVisible();

    // Add a product
    await page.getByRole("button", { name: /Add Line/ }).click();
    await page.locator("select").last().selectOption({ label: "Petrol (Fuel - Liters)" });
    await page.locator("input[placeholder='0']").fill("10");

    // Step 3 → Step 4
    await page.getByRole("button", { name: "Next", exact: true }).click();
    await expect(page.getByText("Payment & Driver")).toBeVisible();

    // Fill driver name (required for registered customer)
    await page.getByPlaceholder("Enter driver name").fill("Test Driver");

    // Step 4 → Step 5
    await page.getByRole("button", { name: /Review/ }).click();
    await expect(page.getByText("Review & Confirm")).toBeVisible();

    // Verify confirm page shows correct customer and vehicle
    await expect(page.getByText("Customer B - Renter")).toBeVisible();
    await expect(page.getByText("TN01AB1234")).toBeVisible();
  });
});
