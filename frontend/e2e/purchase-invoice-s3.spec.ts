import { test, expect } from "@playwright/test";

const API_BASE = "http://localhost:8080/api";

const mockSuppliers = [
    { id: 1, name: "IOCL Chennai Terminal", contactPerson: "Murugan", phone: "9876543210", email: "iocl@test.com", active: true },
];

const mockProducts = [
    { id: 1, name: "Petrol (MS)", hsnCode: "2710", price: 109.92, category: "FUEL", unit: "L", active: true },
];

const mockPurchaseInvoices = [
    {
        id: 1,
        invoiceNumber: "IOCL-2026-001",
        invoiceType: "FUEL",
        supplier: { id: 1, name: "IOCL Chennai Terminal" },
        invoiceDate: "2026-03-18",
        deliveryDate: "2026-03-19",
        status: "PENDING",
        totalAmount: 549950,
        pdfFilePath: "purchase-invoices/1/invoice.pdf",
        items: [{ product: { id: 1, name: "Petrol (MS)" }, quantity: 5000, unitPrice: 109.99, totalPrice: 549950 }],
    },
    {
        id: 2,
        invoiceNumber: "IOCL-2026-002",
        invoiceType: "FUEL",
        supplier: { id: 1, name: "IOCL Chennai Terminal" },
        invoiceDate: "2026-03-20",
        deliveryDate: null,
        status: "PENDING",
        totalAmount: 200020,
        pdfFilePath: null,
        items: [{ product: { id: 1, name: "Petrol (MS)" }, quantity: 2000, unitPrice: 100.01, totalPrice: 200020 }],
    },
];

async function mockRoutes(page: import("@playwright/test").Page) {
    await page.route(`${API_BASE}/**`, async (route) => {
        const url = route.request().url();
        const method = route.request().method();

        if (url.includes("/purchase-invoices") && url.includes("/pdf-url") && method === "GET") {
            await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ url: "https://s3.example.com/test.pdf" }) });
            return;
        }
        if (url.includes("/purchase-invoices") && method === "GET") {
            await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockPurchaseInvoices) });
            return;
        }
        if (url.includes("/products") && method === "GET") {
            await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockProducts) });
            return;
        }
        if (url.includes("/suppliers") && method === "GET") {
            await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockSuppliers) });
            return;
        }
        if (method === "GET") {
            await route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
        } else {
            await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
        }
    });
}

test.describe("Purchase Invoice PDF (S3)", () => {
    test.beforeEach(async ({ page }) => {
        await mockRoutes(page);
        await page.goto("/operations/inventory/purchase-invoices");
    });

    test("shows PDF icon for invoices with uploaded PDF", async ({ page }) => {
        // First invoice has pdfFilePath set — should show the ExternalLink icon button
        const firstRow = page.locator("tbody tr").first();
        await expect(firstRow.getByText("IOCL-2026-001")).toBeVisible();
        await expect(firstRow.locator("button[title='View PDF']")).toBeVisible();
    });

    test("hides PDF icon for invoices without PDF", async ({ page }) => {
        // Second invoice has no pdfFilePath — should show "—"
        const secondRow = page.locator("tbody tr").nth(1);
        await expect(secondRow.getByText("IOCL-2026-002")).toBeVisible();
        await expect(secondRow.locator("button[title='View PDF']")).not.toBeVisible();
        await expect(secondRow.getByText("—")).toBeVisible();
    });

    test("clicking PDF icon calls presigned URL endpoint", async ({ page }) => {
        let pdfUrlCalled = false;
        await page.route(`${API_BASE}/purchase-invoices/1/pdf-url`, async (route) => {
            pdfUrlCalled = true;
            await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ url: "https://s3.example.com/test.pdf" }) });
        });

        // Intercept window.open
        await page.evaluate(() => {
            (window as any).__openedUrls = [];
            window.open = (url?: string | URL) => { (window as any).__openedUrls.push(url); return null; };
        });

        const firstRow = page.locator("tbody tr").first();
        await firstRow.locator("button[title='View PDF']").click();

        await expect(() => expect(pdfUrlCalled).toBe(true)).toPass({ timeout: 5000 });
    });
});
