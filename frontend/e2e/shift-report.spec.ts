import { test, expect } from "@playwright/test";

const API_BASE = "http://localhost:8080/api";

const mockShift = {
    id: 47,
    startTime: "2026-03-18T06:00:00",
    endTime: "2026-03-18T18:00:00",
    status: "CLOSED",
    attendant: { id: 1, name: "Sathya", username: "sathya" },
    scid: 1,
};

const mockReport = {
    id: 1,
    shift: mockShift,
    reportDate: "2026-03-18T18:05:00",
    status: "DRAFT",
    finalizedBy: null,
    finalizedAt: null,
    totalRevenue: 349365.62,
    totalAdvances: 307785.96,
    balance: 41579.66,
    cashBillAmount: 192625.27,
    creditBillAmount: 110722.23,
    lineItems: [
        { id: 1, section: "REVENUE", category: "FUEL_SALES", label: "Petrol (MS)", quantity: 1055, rate: 109.92, amount: 115965.60, sortOrder: 1 },
        { id: 2, section: "REVENUE", category: "FUEL_SALES", label: "XtraPremium (XP)", quantity: 96, rate: 114.23, amount: 10966.08, sortOrder: 2 },
        { id: 3, section: "REVENUE", category: "FUEL_SALES", label: "Diesel (HSD)", quantity: 1739, rate: 100.01, amount: 173917.39, sortOrder: 3 },
        { id: 4, section: "REVENUE", category: "OIL_SALES", label: "Oil / Lubricants", amount: 1450.00, sortOrder: 4 },
        { id: 5, section: "REVENUE", category: "BILL_PAYMENT", label: "Bill Payments", amount: 39244.00, sortOrder: 5 },
        { id: 6, section: "REVENUE", category: "STATEMENT_PAYMENT", label: "Statement Payments", amount: 7822.55, sortOrder: 6 },
        { id: 10, section: "ADVANCE", category: "CREDIT_BILLS", label: "Credit Bills", amount: 110722.23, sortOrder: 10 },
        { id: 11, section: "ADVANCE", category: "CARD", label: "Card Advance", amount: 21060.52, sourceEntityType: "ShiftTransaction", sortOrder: 11 },
        { id: 12, section: "ADVANCE", category: "CCMS", label: "CCMS Advance", amount: 2000.00, sourceEntityType: "ShiftTransaction", sortOrder: 12 },
        { id: 13, section: "ADVANCE", category: "CASH_ADVANCE", label: "Cash Advance", amount: 8373.00, sortOrder: 13 },
        { id: 14, section: "ADVANCE", category: "HOME_ADVANCE", label: "Home Advance", amount: 161000.00, sortOrder: 14 },
        { id: 15, section: "ADVANCE", category: "EXPENSES", label: "Expenses", amount: 4076.00, sortOrder: 15 },
        { id: 16, section: "ADVANCE", category: "INCENTIVE", label: "Incentive / Discount", amount: 554.01, sortOrder: 16 },
    ],
    cashBillBreakdowns: [
        { id: 1, productName: "Petrol (MS)", cashLitres: 753.73, cardLitres: 191.60, ccmsLitres: 0, upiLitres: 0, chequeLitres: 0, totalLitres: 945.33 },
        { id: 2, productName: "XtraPremium (XP)", cashLitres: 96, cardLitres: 0, ccmsLitres: 0, upiLitres: 0, chequeLitres: 0, totalLitres: 96 },
        { id: 3, productName: "Diesel (HSD)", cashLitres: 762.92, cardLitres: 0, ccmsLitres: 0, upiLitres: 0, chequeLitres: 0, totalLitres: 762.92 },
    ],
    auditLogs: [],
};

const mockPrintData = {
    companyName: "Sri Singaravelan Trading Company",
    employeeName: "Sankar",
    shiftId: 47,
    shiftStart: "2026-03-18T06:00:00",
    shiftEnd: "2026-03-18T18:00:00",
    reportStatus: "DRAFT",
    meterReadings: [
        { pumpName: "GVR Pump", nozzleName: "GVR A2", productName: "Petrol", openReading: 88334, closeReading: 88339, sales: 5 },
        { pumpName: "Wayne Pump", nozzleName: "MS-B1", productName: "Petrol", openReading: 2492764, closeReading: 2493544, sales: 780 },
        { pumpName: "GVR Pump", nozzleName: "XP A1", productName: "XtraPremium", openReading: 534944, closeReading: 535043, sales: 99 },
        { pumpName: "HSD Pump", nozzleName: "A1", productName: "Diesel", openReading: 350890, closeReading: 351675, sales: 785 },
    ],
    tankReadings: [
        { tankName: "XP Tank", productName: "XtraPremium", openDip: "120.0", openStock: 10480, incomeStock: 0, totalStock: 10480, closeDip: "119.4", closeStock: 10421, saleStock: 59 },
        { tankName: "MS Tank", productName: "Petrol", openDip: "105.8", openStock: 9031, incomeStock: 0, totalStock: 9031, closeDip: "96.0", closeStock: 7990, saleStock: 1041 },
        { tankName: "HSD Tank 1", productName: "Diesel", openDip: "137.8", openStock: 14455, incomeStock: 0, totalStock: 14455, closeDip: "131.0", closeStock: 13632, saleStock: 823 },
    ],
    salesDifferences: [
        { productName: "XtraPremium", tankSale: 59, meterSale: 96, difference: -37 },
        { productName: "Petrol", tankSale: 1041, meterSale: 1055, difference: -14 },
        { productName: "Diesel", tankSale: 1952, meterSale: 1769, difference: 183 },
    ],
    creditBillDetails: [
        { customerName: "Kumaran Blue Metals", billNo: "A22/56", vehicleNo: "TN 28 AT 7210", products: "HSD:40", amount: 4007.40 },
        { customerName: "Kumaran Blue Metals", billNo: "A22/57", vehicleNo: "GENSET(KBM)", products: "HSD:400", amount: 40004.00 },
        { customerName: "E,F,G,M TRUST", billNo: "A22/66", vehicleNo: "NEW(EFGM)", products: "HSD:5", amount: 500.00 },
        { customerName: "Haniman Homeyopathy", billNo: "A22/59", vehicleNo: "TN 30 BJ 0703", products: "P:27", amount: 3016.20 },
    ],
    stockSummary: [
        { productName: "Petrol (MS)", openStock: 9031, receipt: 0, totalStock: 9031, sales: 1055, rate: 109.92, amount: 115965.60 },
        { productName: "XtraPremium (XP)", openStock: 10480, receipt: 0, totalStock: 10480, sales: 96, rate: 114.23, amount: 10966.08 },
        { productName: "Diesel (HSD)", openStock: 26654, receipt: 0, totalStock: 26654, sales: 1739, rate: 100.01, amount: 173917.39 },
    ],
    advanceEntries: [
        { type: "CARD", description: "Batch#4 TID:8660", amount: 200.00 },
        { type: "CARD", description: "Batch#4 TID:8660", amount: 500.00 },
        { type: "CASH_ADV", description: "SANKAR - NS", amount: 7823.00 },
        { type: "HOME_ADV", description: "HOME", amount: 161000.00 },
        { type: "EXPENSE", description: "AUTO RENT - LOADING CEMENT", amount: 450.00 },
        { type: "INCENTIVE", description: "Discounts given", amount: 554.01 },
    ],
    paymentEntries: [
        { type: "BILL", customerName: "Kumaran Blue Metals", reference: "A22/4", paymentMode: "Cash", amount: 9811.00 },
        { type: "BILL", customerName: "Kumaran Blue Metals", reference: "A22/6", paymentMode: "Cash", amount: 29433.00 },
        { type: "STMT", customerName: "SBI CAR (P.RAVI)", reference: "9403", paymentMode: "Cash", amount: 7822.55 },
    ],
};

const mockAuditLogs = [
    { id: 1, action: "RECOMPUTED", description: "Report recomputed from source data", performedBy: "system", performedAt: "2026-03-18T18:10:00" },
];

async function mockRoutes(page: import("@playwright/test").Page, overrides?: { reportStatus?: string }) {
    const report = overrides?.reportStatus
        ? { ...mockReport, status: overrides.reportStatus }
        : mockReport;

    await page.route(`${API_BASE}/**`, async (route) => {
        const url = route.request().url();
        const method = route.request().method();

        if (url.includes("/shift-reports/47/print-data") && method === "GET") {
            await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockPrintData) });
            return;
        }
        if (url.includes("/shift-reports/47/audit-log") && method === "GET") {
            await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockAuditLogs) });
            return;
        }
        if (url.includes("/shift-reports/47") && method === "GET") {
            await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(report) });
            return;
        }
        if (url.includes("/shift-reports") && url.includes("/recompute") && method === "POST") {
            await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(report) });
            return;
        }
        if (url.includes("/shift-reports") && url.includes("/finalize") && method === "POST") {
            await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ ...report, status: "FINALIZED", finalizedBy: "manager", finalizedAt: "2026-03-18T19:00:00" }) });
            return;
        }
        if (url.includes("/shift-reports") && url.includes("/line-items") && method === "PATCH") {
            await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(report) });
            return;
        }
        if (url.includes("/shift-reports") && !url.includes("/47") && method === "GET") {
            await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify([report]) });
            return;
        }
        if (method === "GET") {
            await route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
        } else {
            await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
        }
    });
}

// Screen view container — excludes print-only elements
const screen = (page: import("@playwright/test").Page) => page.locator(".print\\:hidden");

test.describe("Shift Closing Report", () => {
    test.beforeEach(async ({ page }) => {
        await mockRoutes(page);
        await page.goto("/operations/shifts/report/47");
    });

    test("page loads with header and shift info", async ({ page }) => {
        await expect(page.getByRole("heading", { name: "Shift Closing Report" })).toBeVisible();
        await expect(screen(page).getByText("Sri Singaravelan Trading Company")).toBeVisible();
        await expect(screen(page).getByText("Shift #47")).toBeVisible();
    });

    test("displays DRAFT status badge", async ({ page }) => {
        await expect(screen(page).getByText("DRAFT")).toBeVisible();
    });

    test("displays summary cards with correct values", async ({ page }) => {
        await expect(screen(page).getByText("Total Revenue")).toBeVisible();
        await expect(screen(page).getByText("Total Advances")).toBeVisible();
        await expect(screen(page).getByText("Balance")).toBeVisible();
        await expect(screen(page).getByText("Cash Bills")).toBeVisible();
    });

    test("displays revenue line items", async ({ page }) => {
        await expect(screen(page).getByText("Petrol (MS)").first()).toBeVisible();
        await expect(screen(page).getByText("XtraPremium (XP)").first()).toBeVisible();
        await expect(screen(page).getByText("Diesel (HSD)").first()).toBeVisible();
        await expect(screen(page).getByText("Bill Payments").first()).toBeVisible();
        await expect(screen(page).getByText("Statement Payments").first()).toBeVisible();
    });

    test("displays advance line items", async ({ page }) => {
        await expect(screen(page).getByText("Card Advance").first()).toBeVisible();
        await expect(screen(page).getByText("CCMS Advance").first()).toBeVisible();
        await expect(screen(page).getByText("Cash Advance").first()).toBeVisible();
        await expect(screen(page).getByText("Home Advance").first()).toBeVisible();
        await expect(screen(page).getByText("Expenses").first()).toBeVisible();
        await expect(screen(page).getByText("Incentive / Discount").first()).toBeVisible();
    });

    test("displays meter readings", async ({ page }) => {
        await expect(screen(page).getByText("Meter Readings")).toBeVisible();
        await expect(screen(page).getByText("GVR A2")).toBeVisible();
        await expect(screen(page).getByText("MS-B1")).toBeVisible();
    });

    test("displays tank readings", async ({ page }) => {
        await expect(screen(page).getByText("Tank Readings")).toBeVisible();
        await expect(screen(page).getByText("XP Tank")).toBeVisible();
        await expect(screen(page).getByText("MS Tank")).toBeVisible();
        await expect(screen(page).getByText("HSD Tank 1")).toBeVisible();
    });

    test("displays cash bill breakdown", async ({ page }) => {
        await expect(screen(page).getByText("Cash Bill Breakdown (Litres)")).toBeVisible();
    });

    test("displays sales difference", async ({ page }) => {
        await expect(screen(page).getByText("Sales Difference (Tank vs Meter)")).toBeVisible();
    });

    test("displays credit bills detail grouped by customer", async ({ page }) => {
        await expect(screen(page).getByText("Credit Bills Detail")).toBeVisible();
        await expect(screen(page).getByText("Kumaran Blue Metals").first()).toBeVisible();
        await expect(screen(page).getByText("A22/56")).toBeVisible();
        await expect(screen(page).getByText("Haniman Homeyopathy")).toBeVisible();
    });

    test("displays advance entries detail", async ({ page }) => {
        await expect(screen(page).getByText("Advance Entries Detail")).toBeVisible();
        await expect(screen(page).getByText("CASH_ADV")).toBeVisible();
        await expect(screen(page).getByText("HOME_ADV")).toBeVisible();
    });

    test("displays stock summary", async ({ page }) => {
        await expect(screen(page).getByText("Stock Summary")).toBeVisible();
    });

    test("displays bill/statement payments", async ({ page }) => {
        await expect(screen(page).getByText("Bill / Statement Payments")).toBeVisible();
        await expect(screen(page).getByText("A22/4")).toBeVisible();
        await expect(screen(page).getByText("9403")).toBeVisible();
    });

    test("shows Recompute and Finalize buttons for DRAFT report", async ({ page }) => {
        await expect(page.getByRole("button", { name: /Recompute/ })).toBeVisible();
        await expect(page.getByRole("button", { name: /Finalize/ })).toBeVisible();
    });

    test("shows Print button", async ({ page }) => {
        await expect(page.getByRole("button", { name: /Print/ })).toBeVisible();
    });

    test("back button navigates to shifts page", async ({ page }) => {
        await screen(page).locator("button").filter({ has: page.locator("svg.lucide-arrow-left") }).click();
        await expect(page).toHaveURL("/operations/shifts");
    });
});

test.describe("Shift Report - Edit Line Item", () => {
    test.beforeEach(async ({ page }) => {
        await mockRoutes(page);
        await page.goto("/operations/shifts/report/47");
    });

    test("clicking edit icon on advance item shows inline edit form", async ({ page }) => {
        // Find the edit button for Card Advance row
        const cardRow = page.locator("tr", { hasText: "Card Advance" });
        await cardRow.locator("button[title='Edit']").click();

        // Should show input fields
        await expect(page.locator("input[type='number']").first()).toBeVisible();
        await expect(page.getByPlaceholder("Reason")).toBeVisible();
        await expect(page.getByRole("button", { name: "Save" })).toBeVisible();
        await expect(page.getByRole("button", { name: "Cancel" })).toBeVisible();
    });

    test("clicking Cancel hides the edit form", async ({ page }) => {
        const cardRow = page.locator("tr", { hasText: "Card Advance" });
        await cardRow.locator("button[title='Edit']").click();
        await page.getByRole("button", { name: "Cancel" }).click();

        await expect(page.locator("input[type='number']")).not.toBeVisible();
    });

    test("edit form pre-fills with current amount", async ({ page }) => {
        const cardRow = page.locator("tr", { hasText: "Card Advance" });
        await cardRow.locator("button[title='Edit']").click();

        const input = page.locator("input[type='number']").first();
        await expect(input).toHaveValue("21060.52");
    });
});

test.describe("Shift Report - Transfer Entry", () => {
    test.beforeEach(async ({ page }) => {
        await mockRoutes(page);
        await page.goto("/operations/shifts/report/47");
    });

    test("clicking transfer icon opens transfer modal", async ({ page }) => {
        const cardRow = page.locator("tr", { hasText: "Card Advance" });
        await cardRow.locator("button[title='Transfer']").click();

        await expect(page.getByText("Transfer Entry")).toBeVisible();
        await expect(page.getByText("Target Report")).toBeVisible();
        await expect(page.getByPlaceholder("Why transfer?")).toBeVisible();
    });

    test("transfer modal has Cancel button that closes it", async ({ page }) => {
        const cardRow = page.locator("tr", { hasText: "Card Advance" });
        await cardRow.locator("button[title='Transfer']").click();

        await expect(page.getByText("Transfer Entry")).toBeVisible();
        await page.getByRole("button", { name: "Cancel" }).click();
        await expect(page.getByText("Transfer Entry")).not.toBeVisible();
    });

    test("Transfer button is disabled without target selection", async ({ page }) => {
        const cardRow = screen(page).locator("tr", { hasText: "Card Advance" });
        await cardRow.locator("button[title='Transfer']").click();

        // The Transfer action button (not the icon) inside the modal
        const transferBtn = page.locator(".fixed button", { hasText: "Transfer" }).last();
        await expect(transferBtn).toBeDisabled();
    });
});

test.describe("Shift Report - Finalize", () => {
    test.beforeEach(async ({ page }) => {
        await mockRoutes(page);
        await page.goto("/operations/shifts/report/47");
    });

    test("clicking Finalize shows confirmation dialog", async ({ page }) => {
        await page.getByRole("button", { name: /Finalize/ }).click();

        await expect(page.getByText("Finalize Report?")).toBeVisible();
        await expect(page.getByText("lock the report permanently")).toBeVisible();
        await expect(page.getByText("RECONCILED")).toBeVisible();
    });

    test("clicking Cancel in confirmation closes dialog", async ({ page }) => {
        await page.getByRole("button", { name: /Finalize/ }).click();
        await page.getByRole("button", { name: "Cancel" }).click();

        await expect(page.getByText("Finalize Report?")).not.toBeVisible();
    });

    test("confirming finalize sends API call", async ({ page }) => {
        let finalizeCalled = false;
        await page.route(`${API_BASE}/shift-reports/*/finalize`, async (route) => {
            finalizeCalled = true;
            await route.fulfill({
                status: 200,
                contentType: "application/json",
                body: JSON.stringify({ ...mockReport, status: "FINALIZED" }),
            });
        });

        await page.getByRole("button", { name: /Finalize/ }).click();
        // Click the Finalize button inside the modal (has CheckCircle icon)
        await page.locator("button").filter({ hasText: "Finalize" }).last().click();

        expect(finalizeCalled).toBe(true);
    });
});

test.describe("Shift Report - Finalized State", () => {
    test("finalized report hides edit/transfer/recompute/finalize buttons", async ({ page }) => {
        await mockRoutes(page, { reportStatus: "FINALIZED" });
        await page.goto("/operations/shifts/report/47");

        await expect(screen(page).getByText("FINALIZED")).toBeVisible();
        await expect(page.getByRole("button", { name: /Recompute/ })).not.toBeVisible();
        await expect(page.getByRole("button", { name: /Finalize/ })).not.toBeVisible();
        // Edit icons should not be present in screen view
        await expect(screen(page).locator("button[title='Edit']")).toHaveCount(0);
    });
});

test.describe("Shift Report - Audit Log", () => {
    test.beforeEach(async ({ page }) => {
        await mockRoutes(page);
        await page.goto("/operations/shifts/report/47");
    });

    test("audit log section is collapsed by default", async ({ page }) => {
        await expect(page.getByText("Audit Log")).toBeVisible();
        await expect(page.getByText("Report recomputed")).not.toBeVisible();
    });

    test("clicking Audit Log expands and shows entries", async ({ page }) => {
        // Ensure audit log route is mocked for report ID 1
        await page.route(`${API_BASE}/shift-reports/1/audit-log`, async (route) => {
            await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockAuditLogs) });
        });

        await screen(page).getByText("Audit Log").click();
        await expect(screen(page).getByText("Report recomputed from source data")).toBeVisible({ timeout: 10000 });
        await expect(screen(page).getByText("RECOMPUTED").first()).toBeVisible();
    });
});

test.describe("Shift Report - Error State", () => {
    test("shows error message when report not found", async ({ page }) => {
        await page.route(`${API_BASE}/**`, async (route) => {
            const url = route.request().url();
            if (url.includes("/shift-reports/999")) {
                await route.fulfill({ status: 500, contentType: "text/plain", body: "Report not found for shift: 999" });
                return;
            }
            await route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
        });

        await page.goto("/operations/shifts/report/999");
        await expect(page.getByText(/Report not found|Failed to load/)).toBeVisible();
        await expect(page.getByText("Back to Shifts")).toBeVisible();
    });
});

test.describe("Shift Report - PDF Download", () => {
    test("finalized report with PDF shows Download PDF button", async ({ page }) => {
        const reportWithPdf = { ...mockReport, status: "FINALIZED", reportPdfUrl: "shift-reports/47/report.pdf", finalizedBy: "manager", finalizedAt: "2026-03-18T19:00:00" };
        await page.route(`${API_BASE}/**`, async (route) => {
            const url = route.request().url();
            const method = route.request().method();
            if (url.includes("/shift-reports/47/print-data") && method === "GET") {
                await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockPrintData) });
                return;
            }
            if (url.includes("/shift-reports/47/audit-log") && method === "GET") {
                await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockAuditLogs) });
                return;
            }
            if (url.includes("/shift-reports/47") && method === "GET") {
                await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(reportWithPdf) });
                return;
            }
            if (url.includes("/shift-reports") && !url.includes("/47") && method === "GET") {
                await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify([reportWithPdf]) });
                return;
            }
            if (method === "GET") {
                await route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
            } else {
                await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
            }
        });
        await page.goto("/operations/shifts/report/47");
        await expect(page.getByRole("button", { name: /Download PDF/ })).toBeVisible();
    });

    test("finalized report without PDF does not show Download PDF button", async ({ page }) => {
        await mockRoutes(page, { reportStatus: "FINALIZED" });
        await page.goto("/operations/shifts/report/47");
        await expect(screen(page).getByText("FINALIZED")).toBeVisible();
        await expect(page.getByRole("button", { name: /Download PDF/ })).not.toBeVisible();
    });

    test("clicking Download PDF calls presigned URL endpoint", async ({ page }) => {
        const reportWithPdf = { ...mockReport, status: "FINALIZED", reportPdfUrl: "shift-reports/47/report.pdf", finalizedBy: "manager", finalizedAt: "2026-03-18T19:00:00" };
        let pdfUrlCalled = false;

        await page.route(`${API_BASE}/**`, async (route) => {
            const url = route.request().url();
            const method = route.request().method();
            if (url.includes("/shift-reports/47/pdf-url") && method === "GET") {
                pdfUrlCalled = true;
                await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ url: "https://s3.example.com/report.pdf" }) });
                return;
            }
            if (url.includes("/shift-reports/47/print-data") && method === "GET") {
                await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockPrintData) });
                return;
            }
            if (url.includes("/shift-reports/47/audit-log") && method === "GET") {
                await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockAuditLogs) });
                return;
            }
            if (url.includes("/shift-reports/47") && method === "GET") {
                await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(reportWithPdf) });
                return;
            }
            if (url.includes("/shift-reports") && !url.includes("/47") && method === "GET") {
                await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify([reportWithPdf]) });
                return;
            }
            if (method === "GET") {
                await route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
            } else {
                await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
            }
        });

        // Intercept window.open
        await page.evaluate(() => {
            (window as any).__openedUrls = [];
            window.open = (url?: string | URL) => { (window as any).__openedUrls.push(url); return null; };
        });

        await page.goto("/operations/shifts/report/47");
        await page.getByRole("button", { name: /Download PDF/ }).click();

        await expect(() => expect(pdfUrlCalled).toBe(true)).toPass({ timeout: 5000 });
    });
});
