import { test, expect } from "@playwright/test";
import { expectFieldError, expectNoFieldError, clickSubmitButton } from "./fixtures/test-helpers";

const API_BASE = "http://localhost:8080/api";

// Owner user so EMPLOYEE_*-gated actions render (PermissionGate checks role/permissions).
const OWNER_USER = {
  id: 1,
  cognitoId: "dev-owner-001",
  username: "owner",
  name: "Dev Owner",
  email: "owner@stopforfuel.com",
  role: "OWNER",
  permissions: [],
};

async function mockEmployeeApiRoutes(page: import("@playwright/test").Page) {
  await page.route(`${API_BASE}/**`, async (route) => {
    const url = route.request().url();
    const method = route.request().method();
    // Owner user so the EMPLOYEE_CREATE-gated "Add Employee" button renders.
    if (url.includes("/auth/me") && method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(OWNER_USER) });
      return;
    }
    if (method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
    } else {
      await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
    }
  });
}

test.describe("Employee Form Validation", () => {
  test.beforeEach(async ({ page }) => {
    await mockEmployeeApiRoutes(page);
    await page.goto("/employees");
    await page.getByRole("button", { name: "Add Employee" }).click();
  });

  test("shows errors when submitting empty form", async ({ page }) => {
    await clickSubmitButton(page, "Save");
    await expectFieldError(page, "Employee name is required");
    await expectFieldError(page, "Designation is required");
    await expectFieldError(page, "Email is required");
    await expectFieldError(page, "Phone is required");
    await expectFieldError(page, "Join date is required");
  });

  test("shows error for invalid email", async ({ page }) => {
    // Fill name so we can focus on email validation
    await page.locator("form input").first().fill("John Doe");
    // Fill designation too
    await page.locator("form .grid input").nth(1).fill("Manager");
    // Fill phone
    await page.locator("form .grid input").nth(3).fill("9876543210");
    // Fill salary
    await page.locator("form input[type='number']").first().fill("25000");
    // Fill join date
    await page.locator("form input[type='date']").fill("2026-01-01");
    // Enter invalid email (3rd input in grid = index 2)
    await page.locator("form .grid input").nth(2).fill("not-an-email");
    await clickSubmitButton(page, "Save");
    await expectFieldError(page, "Invalid email address");
  });

  test("shows error for invalid phone", async ({ page }) => {
    await page.locator("form input").first().fill("John Doe");
    // Find the phone field — it's the 4th input in the personal tab grid
    const phoneInput = page.locator("form .grid input").nth(3);
    await phoneInput.fill("abc");
    await clickSubmitButton(page, "Save");
    await expectFieldError(page, "Must be a valid 10-digit Indian mobile number");
  });

  test("clears error on typing", async ({ page }) => {
    await clickSubmitButton(page, "Save");
    await expectFieldError(page, "Employee name is required");
    await page.locator("form input").first().fill("J");
    await expectNoFieldError(page, "Employee name is required");
  });

  test("submits successfully with valid data", async ({ page }) => {
    // Fill all required fields
    const inputs = page.locator("form .grid input");
    // Name
    await inputs.nth(0).fill("John Doe");
    // Designation
    await inputs.nth(1).fill("Manager");
    // Email (index 2)
    await inputs.nth(2).fill("john@example.com");
    // Phone
    await inputs.nth(3).fill("9876543210");
    // Salary
    await page.locator("form input[type='number']").first().fill("25000");
    // Join Date
    await page.locator("form input[type='date']").fill("2026-01-01");

    await clickSubmitButton(page, "Save");
    // Modal should close — no validation errors visible
    await expect(page.getByText("Employee name is required")).not.toBeVisible();
  });
});

const EMP = {
  id: 19,
  name: "Senthil Nathan",
  designation: "Manager",
  email: "senthil@example.com",
  phone: "9965993050",
  salary: 25000,
  salaryDay: 1,
  joinDate: "2026-01-01",
  status: "ACTIVE",
  employeeCode: "emp019",
};

test.describe("Employee Edit & Status", () => {
  let lastPut: any;

  test.beforeEach(async ({ page }) => {
    lastPut = null;
    await page.route(`${API_BASE}/**`, async (route) => {
      const req = route.request();
      const url = req.url();
      const method = req.method();

      // Auth: owner so the gated Edit/Delete actions render.
      if (url.includes("/auth/me") && method === "GET") {
        await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(OWNER_USER) });
        return;
      }
      // Single-employee fetch performed by openEdit (must precede the list check).
      if (method === "GET" && /\/employees\/\d+(\?|$)/.test(url)) {
        await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(EMP) });
        return;
      }
      // Paged employee list.
      if (method === "GET" && url.includes("/employees")) {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({ content: [EMP], totalPages: 1, totalElements: 1 }),
        });
        return;
      }
      // Capture the update payload.
      if (method === "PUT") {
        lastPut = req.postDataJSON();
        await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ ...EMP, ...lastPut }) });
        return;
      }
      if (method === "GET") {
        await route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
      } else {
        await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
      }
    });

    await page.goto("/employees");
    await expect(page.getByText("Senthil Nathan")).toBeVisible();
  });

  test("edit action opens the modal pre-populated", async ({ page }) => {
    await page.getByRole("row", { name: /Senthil Nathan/ }).getByTitle("Edit").click();

    await expect(page.getByRole("heading", { name: "Edit Employee" })).toBeVisible();
    // Name field is the first form input and carries the existing value.
    await expect(page.locator("form input").first()).toHaveValue("Senthil Nathan");
  });

  test("set Status to Inactive and save sends INACTIVE", async ({ page }) => {
    await page.getByRole("row", { name: /Senthil Nathan/ }).getByTitle("Edit").click();
    await expect(page.getByRole("heading", { name: "Edit Employee" })).toBeVisible();

    const statusSelect = page
      .locator("form .space-y-2")
      .filter({ has: page.getByText("Status", { exact: true }) })
      .locator("select");

    await expect(statusSelect).toHaveValue("ACTIVE");
    await statusSelect.selectOption("INACTIVE");
    await expect(statusSelect).toHaveValue("INACTIVE");

    await clickSubmitButton(page, "Save");

    // The PUT payload must carry the new status.
    await expect.poll(() => lastPut?.status).toBe("INACTIVE");
    // Modal closes on success.
    await expect(page.getByRole("heading", { name: "Edit Employee" })).not.toBeVisible();
  });
});
