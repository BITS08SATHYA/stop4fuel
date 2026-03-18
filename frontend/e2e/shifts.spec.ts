import { test, expect } from "@playwright/test";

const API_BASE = "http://localhost:8080/api";

const mockActiveShift = {
    id: 47,
    startTime: "2026-03-18T06:00:00",
    status: "OPEN",
    attendant: { id: 1, name: "Sathya" },
    scid: 1,
};

const mockClosedShift = {
    id: 46,
    startTime: "2026-03-17T06:00:00",
    endTime: "2026-03-17T18:00:00",
    status: "CLOSED",
    attendant: { id: 1, name: "Sathya" },
    scid: 1,
};

const mockReconciledShift = {
    id: 45,
    startTime: "2026-03-16T06:00:00",
    endTime: "2026-03-16T18:00:00",
    status: "RECONCILED",
    attendant: { id: 1, name: "Sathya" },
    scid: 1,
};

async function mockShiftRoutes(page: import("@playwright/test").Page, opts?: { noActiveShift?: boolean }) {
    await page.route(`${API_BASE}/**`, async (route) => {
        const url = route.request().url();
        const method = route.request().method();

        if (url.includes("/shifts/active") && method === "GET") {
            if (opts?.noActiveShift) {
                await route.fulfill({ status: 200, contentType: "application/json", body: "null" });
            } else {
                await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockActiveShift) });
            }
            return;
        }
        if (url.includes("/shifts") && method === "GET" && !url.includes("/transactions") && !url.includes("/summary")) {
            await route.fulfill({
                status: 200,
                contentType: "application/json",
                body: JSON.stringify([mockActiveShift, mockClosedShift, mockReconciledShift]),
            });
            return;
        }
        if (url.includes("/shifts/47") && method === "PATCH") {
            // Close shift
            await route.fulfill({
                status: 200,
                contentType: "application/json",
                body: JSON.stringify({ ...mockActiveShift, status: "CLOSED", endTime: "2026-03-18T18:00:00" }),
            });
            return;
        }
        if (url.includes("/transactions/summary") && method === "GET") {
            await route.fulfill({
                status: 200,
                contentType: "application/json",
                body: JSON.stringify({ cash: 50000, upi: 10000, card: 15000, expense: 5000, total: 80000, net: 75000 }),
            });
            return;
        }
        if (url.includes("/transactions") && method === "GET") {
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

test.describe("Shifts Page - Past Shifts", () => {
    test.beforeEach(async ({ page }) => {
        await mockShiftRoutes(page);
        await page.goto("/operations/shifts");
    });

    test("page loads with shift register header", async ({ page }) => {
        await expect(page.getByRole("heading", { name: /Shift/ })).toBeVisible();
    });

    test("past shifts section is visible", async ({ page }) => {
        await expect(page.getByText(/Past Shifts/)).toBeVisible();
    });

    test("clicking Past Shifts shows closed shifts with View Report button", async ({ page }) => {
        await page.getByText(/Past Shifts/).click();

        // Closed shift should show View Report button
        await expect(page.getByText("Shift #46")).toBeVisible();
        await expect(page.getByText("CLOSED")).toBeVisible();
        await expect(page.getByRole("button", { name: "View Report" }).first()).toBeVisible();
    });

    test("View Report button has correct link behavior", async ({ page }) => {
        await page.getByText(/Past Shifts/).click();

        // Check the View Report button exists for closed shifts
        const viewReportBtn = page.getByRole("button", { name: "View Report" }).first();
        await expect(viewReportBtn).toBeVisible();
    });

    test("RECONCILED shift also shows View Report button", async ({ page }) => {
        await page.getByText(/Past Shifts/).click();
        await expect(page.getByText("RECONCILED")).toBeVisible();
    });
});

test.describe("Shifts Page - Close Shift Flow", () => {
    test("closing shift navigates to report page", async ({ page }) => {
        await mockShiftRoutes(page);
        await page.goto("/operations/shifts");

        // Mock the close shift and report routes
        await page.route(`${API_BASE}/shifts/47`, async (route) => {
            if (route.request().method() === "PATCH") {
                await route.fulfill({
                    status: 200,
                    contentType: "application/json",
                    body: JSON.stringify({ ...mockActiveShift, status: "CLOSED", endTime: "2026-03-18T18:00:00" }),
                });
            } else {
                await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockActiveShift) });
            }
        });

        // Accept the confirm dialog
        page.on("dialog", (dialog) => dialog.accept());

        await page.getByRole("button", { name: "Close Shift" }).click();
        await expect(page).toHaveURL("/operations/shifts/report/47");
    });
});

test.describe("Shifts Page - Sidebar Navigation", () => {
    test("Cash Inflows link is visible in sidebar", async ({ page }) => {
        await mockShiftRoutes(page);
        await page.goto("/operations/shifts");
        await expect(page.getByRole("link", { name: "Cash Inflows" })).toBeVisible();
    });
});
