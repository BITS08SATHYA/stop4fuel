import { test, expect } from "@playwright/test";
import { mockApiRoutes, API_BASE, DEV_USER } from "./fixtures/test-helpers";

const mockProducts = [
    { id: 1, name: "Petrol (MS)", hsnCode: "2710", price: 109.92, category: "FUEL", unit: "L", active: true },
    { id: 2, name: "Diesel (HSD)", hsnCode: "2710", price: 100.01, category: "FUEL", unit: "L", active: true },
];

const mockNozzles = [
    { id: 1, nozzleName: "MS-A1", active: true, pump: { id: 1, name: "GVR Pump" }, tank: { id: 1, product: { id: 1, name: "Petrol (MS)" } } },
];

const mockCreatedInvoice = {
    id: 1,
    billNo: "C26/1",
    billType: "CASH",
    netAmount: 5000,
    date: "2026-03-20T10:00:00",
    status: "PAID",
    billPic: null,
    pumpBillPic: null,
    indentPic: null,
    products: [{ product: { id: 1, name: "Petrol (MS)" }, quantity: 45.5, unitPrice: 109.92, amount: 5001.36 }],
};

async function mockRoutes(page: import("@playwright/test").Page) {
    await mockApiRoutes(page);
    await page.route(`${API_BASE}/**`, async (route) => {
        const url = route.request().url();
        const method = route.request().method();

        if (url.includes("/products/active") && method === "GET") {
            await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockProducts) });
            return;
        }
        if (url.includes("/nozzles") && method === "GET") {
            await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockNozzles) });
            return;
        }
        if (url.match(/\/invoices\/\d+\/upload\//) && method === "POST") {
            await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ ...mockCreatedInvoice, billPic: "s3://bucket/bill.jpg" }) });
            return;
        }
        if (url.match(/\/invoices\/\d+\/file-url/) && method === "GET") {
            await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ url: "https://s3.example.com/test.jpg" }) });
            return;
        }
        if (url.includes("/invoices") && method === "POST") {
            await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockCreatedInvoice) });
            return;
        }
        if (url.includes("/invoices") && method === "GET") {
            await route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
            return;
        }
        if (url.includes("/customers") && method === "GET") {
            await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ content: [], totalPages: 0, totalElements: 0 }) });
            return;
        }
        // Fall through to mockApiRoutes handler for auth and other routes
        await route.fallback();
    });
}

async function navigateToUploadStep(page: import("@playwright/test").Page) {
    await page.goto("/operations/invoices");
    await page.getByRole("button", { name: "Walk-in Bill" }).click();
    await expect(page.getByText("Add Products")).toBeVisible();
    await page.getByRole("button", { name: /Add Line/ }).click();
    await page.locator("select").last().selectOption({ index: 1 });
    const qtyInput = page.locator("input[type='number']").first();
    await qtyInput.fill("45.5");
    await page.getByRole("button", { name: /Next/ }).click();
    await page.getByRole("button", { name: /Review/ }).click();
    await page.getByRole("button", { name: /Confirm & Create Invoice/ }).click();
    await expect(page.getByText("Invoice Created")).toBeVisible({ timeout: 10000 });
}

test.describe("Invoice File Upload", () => {
    test.beforeEach(async ({ page }) => {
        await mockRoutes(page);
    });

    test("after creating invoice, shows upload step with bill fields", async ({ page }) => {
        await navigateToUploadStep(page);
        await expect(page.getByText("Invoice Created")).toBeVisible();
        await expect(page.getByText(/Bill No: C26\/1/)).toBeVisible();
        await expect(page.getByText("Attach Documents")).toBeVisible();
        await expect(page.getByText("Upload Bill Photo")).toBeVisible();
        await expect(page.getByText("Upload Pump Bill Photo")).toBeVisible();
        await expect(page.getByText("Upload Indent Photo")).toBeVisible();
    });

    test("Done button navigates to history tab", async ({ page }) => {
        await navigateToUploadStep(page);
        await page.getByRole("button", { name: /Done/ }).click();
        await expect(page.getByText("Recent Invoices")).toBeVisible();
    });

    test("upload file triggers API call", async ({ page }) => {
        await navigateToUploadStep(page);

        let uploadCalled = false;
        await page.route(`${API_BASE}/invoices/1/upload/bill-pic`, async (route) => {
            uploadCalled = true;
            await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ ...mockCreatedInvoice, billPic: "s3://bucket/bill.jpg" }) });
        });

        const fileInput = page.locator("#bill-pic-upload");
        await fileInput.setInputFiles({
            name: "test-bill.jpg",
            mimeType: "image/jpeg",
            buffer: Buffer.from("fake-image-data"),
        });

        await expect(() => expect(uploadCalled).toBe(true)).toPass({ timeout: 5000 });
    });
});
