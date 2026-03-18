import { test, expect } from "@playwright/test";
import { expectFieldError, expectNoFieldError, clickSubmitButton } from "./fixtures/test-helpers";

const API_BASE = "http://localhost:8080/api";

async function mockEmployeeApiRoutes(page: import("@playwright/test").Page) {
  await page.route(`${API_BASE}/**`, async (route) => {
    const method = route.request().method();
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
    await page.locator("form input[type='number']").fill("25000");
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
    await expectFieldError(page, "Invalid phone number");
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
    await page.locator("form input[type='number']").fill("25000");
    // Join Date
    await page.locator("form input[type='date']").fill("2026-01-01");

    await clickSubmitButton(page, "Save");
    // Modal should close — no validation errors visible
    await expect(page.getByText("Employee name is required")).not.toBeVisible();
  });
});
