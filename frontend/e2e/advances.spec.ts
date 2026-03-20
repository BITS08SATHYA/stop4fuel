import { test, expect } from "@playwright/test";
import { mockApiRoutes, mockApiRoute } from "./fixtures/test-helpers";

const API_BASE = "http://localhost:8080/api";

const mockShift = { id: 1, startTime: "2026-03-20T08:00:00", status: "OPEN" };

const mockAdvances = [
  {
    id: 1,
    advanceDate: "2026-03-20T10:00:00",
    amount: 5000,
    advanceType: "CASH_ADVANCE",
    recipientName: "John",
    recipientPhone: "9876543210",
    purpose: "Petrol purchase",
    remarks: "Urgent",
    status: "GIVEN",
    returnedAmount: 0,
    returnDate: null,
    returnRemarks: null,
    shiftId: 1,
    utilizedAmount: 0,
    employee: null,
    statement: null,
    invoiceBills: [],
  },
  {
    id: 2,
    advanceDate: "2026-03-20T11:00:00",
    amount: 3000,
    advanceType: "SALARY_ADVANCE",
    recipientName: "Kumar",
    recipientPhone: "",
    purpose: "Salary",
    remarks: "",
    status: "RETURNED",
    returnedAmount: 3000,
    returnDate: "2026-03-20T15:00:00",
    returnRemarks: "Returned full",
    shiftId: 1,
    utilizedAmount: 0,
    employee: { id: 1, name: "Kumar", phone: "1234567890", designation: "Cashier" },
    statement: null,
    invoiceBills: [],
  },
];

const mockEmployees = [
  { id: 1, name: "Kumar", phone: "1234567890", designation: "Cashier" },
  { id: 2, name: "Ravi", phone: "9876543210", designation: "Attendant" },
];

test.describe("Cash Advances Page", () => {
  test.beforeEach(async ({ page }) => {
    await mockApiRoutes(page);
    await mockApiRoute(page, `${API_BASE}/advances`, mockAdvances);
    await mockApiRoute(page, `${API_BASE}/shifts/active`, mockShift);
    await mockApiRoute(page, `${API_BASE}/employees`, mockEmployees);
  });

  test("renders page with title and summary cards", async ({ page }) => {
    await page.goto("/operations/advances");
    await expect(page.locator("h1")).toContainText("Advances");
    await expect(page.getByText("Total Given", { exact: true })).toBeVisible();
    await expect(page.getByText("Bill Settled", { exact: true })).toBeVisible();
    await expect(page.getByText("Cash Returned", { exact: true })).toBeVisible();
    await expect(page.getByText("Count", { exact: true })).toBeVisible();
  });

  test("displays advances in table", async ({ page }) => {
    await page.goto("/operations/advances");
    await expect(page.locator("table").getByText("John")).toBeVisible();
    await expect(page.locator("table").getByText("Kumar")).toBeVisible();
  });

  test("shows status badges correctly", async ({ page }) => {
    await page.goto("/operations/advances");
    // Status badges are inside table rows
    await expect(page.locator("table tbody span").filter({ hasText: "Given" }).first()).toBeVisible();
    await expect(page.locator("table tbody span").filter({ hasText: "Returned" }).first()).toBeVisible();
  });

  test("record advance button is enabled with active shift", async ({ page }) => {
    await page.goto("/operations/advances");
    const btn = page.getByRole("button", { name: /record advance/i });
    await expect(btn).toBeEnabled();
  });

  test("record advance button is disabled without active shift", async ({ page }) => {
    await mockApiRoute(page, `${API_BASE}/shifts/active`, null);
    await page.goto("/operations/advances");
    const btn = page.getByRole("button", { name: /record advance/i });
    await expect(btn).toBeDisabled();
  });

  test("opens record advance modal", async ({ page }) => {
    await page.goto("/operations/advances");
    await page.getByRole("button", { name: /record advance/i }).click();
    await expect(page.getByText("Record Cash Advance")).toBeVisible();
    await expect(page.getByText("Advance Type")).toBeVisible();
  });

  test("record advance modal has advance type selector", async ({ page }) => {
    await page.goto("/operations/advances");
    await page.getByRole("button", { name: /record advance/i }).click();
    await expect(page.getByText("Advance Type", { exact: true })).toBeVisible();
    await expect(page.getByText("Amount", { exact: true })).toBeVisible();
  });

  test("filters by status", async ({ page }) => {
    await page.goto("/operations/advances");
    // Status filter is the first select with "All Status" option
    const statusSelect = page.locator("select").filter({ hasText: "All Status" });
    await statusSelect.selectOption("GIVEN");
    await expect(page.locator("table").getByText("John")).toBeVisible();
  });

  test("filters by type", async ({ page }) => {
    await page.goto("/operations/advances");
    const typeSelect = page.locator("select").filter({ hasText: "All Types" });
    await typeSelect.selectOption("SALARY_ADVANCE");
    await expect(page.locator("table").getByText("Kumar")).toBeVisible();
  });

  test("search filters by recipient name", async ({ page }) => {
    await page.goto("/operations/advances");
    await page.getByPlaceholder(/search/i).fill("John");
    await expect(page.getByText("John")).toBeVisible();
  });

  test("shows no advances found when filter matches nothing", async ({ page }) => {
    await page.goto("/operations/advances");
    await page.getByPlaceholder(/search/i).fill("NonExistentPerson");
    await expect(page.getByText("No advances found")).toBeVisible();
  });

  test("empty state when no advances", async ({ page }) => {
    await mockApiRoute(page, `${API_BASE}/advances`, []);
    await page.goto("/operations/advances");
    await expect(page.getByText("No advances found")).toBeVisible();
  });
});
