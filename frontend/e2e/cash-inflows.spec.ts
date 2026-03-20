import { test, expect } from "@playwright/test";
import { mockApiRoutes, mockApiRoute } from "./fixtures/test-helpers";

const API_BASE = "http://localhost:8080/api";

const mockShift = { id: 1, startTime: "2026-03-20T08:00:00", status: "OPEN" };

const mockInflows = [
  {
    id: 1,
    amount: 50000,
    inflowDate: "2026-03-20T09:00:00",
    source: "Owner",
    purpose: "Working capital",
    remarks: "Monthly fund",
    status: "ACTIVE",
    repaidAmount: 0,
    shiftId: 1,
  },
  {
    id: 2,
    amount: 20000,
    inflowDate: "2026-03-19T14:00:00",
    source: "Partner",
    purpose: "Emergency",
    remarks: "",
    status: "PARTIALLY_REPAID",
    repaidAmount: 8000,
    shiftId: 1,
  },
  {
    id: 3,
    amount: 10000,
    inflowDate: "2026-03-18T10:00:00",
    source: "Bank Withdrawal",
    purpose: "Change money",
    remarks: "",
    status: "FULLY_REPAID",
    repaidAmount: 10000,
    shiftId: 1,
  },
];

test.describe("Cash Inflows Page", () => {
  test.beforeEach(async ({ page }) => {
    await mockApiRoutes(page);
    await mockApiRoute(page, `${API_BASE}/cash-inflows`, mockInflows);
    await mockApiRoute(page, `${API_BASE}/shifts/active`, mockShift);
  });

  test("renders page title and description", async ({ page }) => {
    await page.goto("/operations/cash-inflows");
    await expect(page.locator("h1")).toContainText("Inflows");
    await expect(page.getByText("Track external cash brought into the station")).toBeVisible();
  });

  test("displays summary cards", async ({ page }) => {
    await page.goto("/operations/cash-inflows");
    await expect(page.getByText("Total Inflow", { exact: true })).toBeVisible();
    await expect(page.getByText("Outstanding", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Total Repaid", { exact: true })).toBeVisible();
    await expect(page.getByText("Active", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Count", { exact: true })).toBeVisible();
  });

  test("displays inflows in table", async ({ page }) => {
    await page.goto("/operations/cash-inflows");
    await expect(page.locator("table").getByText("Owner")).toBeVisible();
    await expect(page.locator("table").getByText("Partner")).toBeVisible();
    await expect(page.locator("table").getByText("Bank Withdrawal")).toBeVisible();
  });

  test("shows status badges", async ({ page }) => {
    await page.goto("/operations/cash-inflows");
    await expect(page.locator("table tbody span").filter({ hasText: "Active" }).first()).toBeVisible();
    await expect(page.locator("table tbody span").filter({ hasText: "Partial" }).first()).toBeVisible();
    await expect(page.locator("table tbody span").filter({ hasText: "Repaid" }).first()).toBeVisible();
  });

  test("record inflow button enabled with active shift", async ({ page }) => {
    await page.goto("/operations/cash-inflows");
    const btn = page.getByRole("button", { name: /record inflow/i });
    await expect(btn).toBeEnabled();
  });

  test("record inflow button disabled without active shift", async ({ page }) => {
    await mockApiRoute(page, `${API_BASE}/shifts/active`, null);
    await page.goto("/operations/cash-inflows");
    const btn = page.getByRole("button", { name: /record inflow/i });
    await expect(btn).toBeDisabled();
  });

  test("shows no active shift warning", async ({ page }) => {
    await mockApiRoute(page, `${API_BASE}/shifts/active`, null);
    await page.goto("/operations/cash-inflows");
    await expect(page.getByText("No active shift")).toBeVisible();
  });

  test("opens record inflow modal", async ({ page }) => {
    await page.goto("/operations/cash-inflows");
    await page.getByRole("button", { name: /record inflow/i }).click();
    await expect(page.getByRole("heading", { name: "Record Cash Inflow" })).toBeVisible();
    await expect(page.getByText("Amount", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Source", { exact: true }).first()).toBeVisible();
  });

  test("save button disabled without required fields", async ({ page }) => {
    await page.goto("/operations/cash-inflows");
    await page.getByRole("button", { name: /record inflow/i }).click();
    const saveBtn = page.getByRole("button", { name: /^save$/i });
    await expect(saveBtn).toBeDisabled();
  });

  test("filters by status", async ({ page }) => {
    await page.goto("/operations/cash-inflows");
    const statusSelect = page.locator("select").filter({ hasText: "All Status" });
    await statusSelect.selectOption("FULLY_REPAID");
    await expect(page.locator("table").getByText("Bank Withdrawal")).toBeVisible();
  });

  test("search filters by source", async ({ page }) => {
    await page.goto("/operations/cash-inflows");
    await page.getByPlaceholder(/search/i).fill("Owner");
    await expect(page.locator("table").getByText("Owner")).toBeVisible();
  });

  test("shows empty state when no matches", async ({ page }) => {
    await page.goto("/operations/cash-inflows");
    await page.getByPlaceholder(/search/i).fill("NonExistent");
    await expect(page.getByText("No cash inflows found")).toBeVisible();
  });

  test("empty state when no inflows", async ({ page }) => {
    await mockApiRoute(page, `${API_BASE}/cash-inflows`, []);
    await page.goto("/operations/cash-inflows");
    await expect(page.getByText("No cash inflows found")).toBeVisible();
  });

  test("view detail opens modal", async ({ page }) => {
    // Mock repayments endpoint
    await page.route(`${API_BASE}/cash-inflows/*/repayments`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([
          { id: 1, amount: 5000, repaymentDate: "2026-03-20T12:00:00", remarks: "First repayment" },
        ]),
      });
    });
    await page.goto("/operations/cash-inflows");
    // Click eye icon for first row
    await page.locator("table tbody tr").first().getByTitle("View details").click();
    await expect(page.getByText("Cash Inflow Details")).toBeVisible();
    await expect(page.getByText("Repayment History")).toBeVisible();
  });

  test("repay button opens repayment modal", async ({ page }) => {
    await page.goto("/operations/cash-inflows");
    // Click repay icon for first active inflow
    await page.locator("table tbody tr").first().getByTitle("Record repayment").click();
    await expect(page.getByRole("heading", { name: "Record Repayment" })).toBeVisible();
    await expect(page.getByText("Repayment Amount", { exact: false }).first()).toBeVisible();
  });

  test("repayment modal shows inflow summary", async ({ page }) => {
    await page.goto("/operations/cash-inflows");
    await page.locator("table tbody tr").first().getByTitle("Record repayment").click();
    await expect(page.getByText("Total Amount")).toBeVisible();
    await expect(page.getByText("Already Repaid")).toBeVisible();
  });
});
