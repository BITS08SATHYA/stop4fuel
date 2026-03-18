import { test, expect } from "@playwright/test";

const API_BASE = "http://localhost:8080/api";

const mockInvoices = {
  content: [
    {
      id: 1,
      billNo: "INV-001",
      date: "2026-03-15T10:30:00",
      billType: "CASH",
      paymentStatus: "PAID",
      netAmount: 5000,
      grossAmount: 5000,
      totalDiscount: 0,
      customer: { id: 1, name: "ABC Transport" },
      vehicle: { id: 1, vehicleNumber: "TN01AB1234" },
      products: [
        { id: 1, product: { id: 1, name: "Petrol" }, nozzle: { id: 1, nozzleName: "N-1" }, quantity: 50, unitPrice: 100, amount: 5000, grossAmount: 5000, discountAmount: 0, discountRate: 0 },
      ],
    },
    {
      id: 2,
      billNo: "INV-002",
      date: "2026-03-15T14:00:00",
      billType: "CREDIT",
      paymentStatus: "NOT_PAID",
      netAmount: 3500,
      grossAmount: 3500,
      totalDiscount: 0,
      customer: { id: 2, name: "XYZ Logistics" },
      vehicle: { id: 2, vehicleNumber: "TN02CD5678" },
      products: [],
    },
  ],
  totalPages: 1,
  totalElements: 2,
};

const mockSummary = [
  { productId: 1, productName: "Petrol", totalQuantity: 50, totalAmount: 5000, totalDiscount: 0 },
];

async function mockRoutes(page: import("@playwright/test").Page) {
  await page.route(`${API_BASE}/**`, async (route) => {
    const url = route.request().url();
    const method = route.request().method();
    if (url.includes("/invoices/history") && method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockInvoices) });
      return;
    }
    if (url.includes("/invoices/product-sales-summary") && method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockSummary) });
      return;
    }
    if (method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
    } else {
      await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
    }
  });
}

test.describe("Invoice History", () => {
  test.beforeEach(async ({ page }) => {
    await mockRoutes(page);
    await page.goto("/operations/invoices/history");
  });

  test("page loads with header and filter bar", async ({ page }) => {
    await expect(page.getByRole("heading", { name: "Invoice History" })).toBeVisible();
    await expect(page.getByRole("button", { name: /Apply/ })).toBeVisible();
    await expect(page.getByRole("button", { name: /Reset/ })).toBeVisible();
  });

  test("displays invoice data in table", async ({ page }) => {
    await expect(page.getByText("INV-001")).toBeVisible();
    await expect(page.getByText("INV-002")).toBeVisible();
    await expect(page.getByText("ABC Transport")).toBeVisible();
    await expect(page.getByText("XYZ Logistics")).toBeVisible();
  });

  test("displays invoice amounts", async ({ page }) => {
    await expect(page.getByText("5,000.00")).toBeVisible();
    await expect(page.getByText("3,500.00")).toBeVisible();
  });

  test("expanding row shows product details", async ({ page }) => {
    await page.getByText("INV-001").click();
    await expect(page.getByText("Product Details")).toBeVisible();
  });

  test("edit and delete buttons are visible", async ({ page }) => {
    await expect(page.locator("[title='Edit']").first()).toBeVisible();
    await expect(page.locator("[title='Delete']").first()).toBeVisible();
  });

  test("search input is present", async ({ page }) => {
    await expect(page.getByPlaceholder("Bill no, customer, vehicle...")).toBeVisible();
  });
});
