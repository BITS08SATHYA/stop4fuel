import { test, expect } from "@playwright/test";

const API_BASE = "http://localhost:8080/api";

const mockStatements = {
    content: [
        {
            id: 1,
            statementNo: 9501,
            customer: { id: 1, name: "Kumaran Blue Metals" },
            fromDate: "2026-03-01",
            toDate: "2026-03-15",
            statementDate: "2026-03-16",
            numberOfBills: 12,
            totalAmount: 145000,
            roundingAmount: 0,
            netAmount: 145000,
            receivedAmount: 0,
            balanceAmount: 145000,
            status: "NOT_PAID",
            statementPdfUrl: "statements/1/statement.pdf",
        },
        {
            id: 2,
            statementNo: 9502,
            customer: { id: 2, name: "E,F,G,M TRUST" },
            fromDate: "2026-03-01",
            toDate: "2026-03-15",
            statementDate: "2026-03-16",
            numberOfBills: 5,
            totalAmount: 28500,
            roundingAmount: 0,
            netAmount: 28500,
            receivedAmount: 0,
            balanceAmount: 28500,
            status: "NOT_PAID",
            statementPdfUrl: null,
        },
    ],
    totalPages: 1,
    totalElements: 2,
};

const mockCustomers = [
    { id: 1, name: "Kumaran Blue Metals", phoneNumbers: "9876543210", status: "ACTIVE" },
    { id: 2, name: "E,F,G,M TRUST", phoneNumbers: "9876543211", status: "ACTIVE" },
];

async function mockRoutes(page: import("@playwright/test").Page) {
    await page.route(`${API_BASE}/**`, async (route) => {
        const url = route.request().url();
        const method = route.request().method();

        if (url.match(/\/statements\/\d+\/generate-pdf/) && method === "POST") {
            await route.fulfill({
                status: 200,
                contentType: "application/json",
                body: JSON.stringify({ ...mockStatements.content[1], statementPdfUrl: "statements/2/statement.pdf" }),
            });
            return;
        }
        if (url.match(/\/statements\/\d+\/pdf-url/) && method === "GET") {
            await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ url: "https://s3.example.com/stmt.pdf" }) });
            return;
        }
        if (url.includes("/statements") && method === "GET") {
            await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockStatements) });
            return;
        }
        if (url.includes("/customers") && method === "GET") {
            await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockCustomers) });
            return;
        }
        if (url.includes("/products") && method === "GET") {
            await route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
            return;
        }
        if (method === "GET") {
            await route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
        } else {
            await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
        }
    });
}

test.describe("Statement PDF", () => {
    test.beforeEach(async ({ page }) => {
        await mockRoutes(page);
        await page.goto("/payments/statements");
    });

    test("shows download button for statement with PDF", async ({ page }) => {
        const firstRow = page.locator("tbody tr").first();
        await expect(firstRow.getByText("9501")).toBeVisible();
        // Statement with statementPdfUrl shows Download button
        await expect(firstRow.locator("button[title='Download PDF']")).toBeVisible();
    });

    test("shows generate button for statement without PDF", async ({ page }) => {
        const secondRow = page.locator("tbody tr").nth(1);
        await expect(secondRow.getByText("9502")).toBeVisible();
        // Statement without statementPdfUrl shows Generate PDF button
        await expect(secondRow.locator("button[title='Generate PDF']")).toBeVisible();
    });

    test("clicking generate PDF calls API", async ({ page }) => {
        let generateCalled = false;
        await page.route(`${API_BASE}/statements/2/generate-pdf`, async (route) => {
            generateCalled = true;
            await route.fulfill({
                status: 200,
                contentType: "application/json",
                body: JSON.stringify({ ...mockStatements.content[1], statementPdfUrl: "statements/2/statement.pdf" }),
            });
        });

        const secondRow = page.locator("tbody tr").nth(1);
        await secondRow.locator("button[title='Generate PDF']").click();

        await expect(() => expect(generateCalled).toBe(true)).toPass({ timeout: 5000 });
    });

    test("clicking download PDF calls presigned URL endpoint", async ({ page }) => {
        let pdfUrlCalled = false;
        await page.route(`${API_BASE}/statements/1/pdf-url`, async (route) => {
            pdfUrlCalled = true;
            await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ url: "https://s3.example.com/stmt.pdf" }) });
        });

        // Intercept window.open
        await page.evaluate(() => {
            (window as any).__openedUrls = [];
            window.open = (url?: string | URL) => { (window as any).__openedUrls.push(url); return null; };
        });

        const firstRow = page.locator("tbody tr").first();
        await firstRow.locator("button[title='Download PDF']").click();

        await expect(() => expect(pdfUrlCalled).toBe(true)).toPass({ timeout: 5000 });
    });
});
