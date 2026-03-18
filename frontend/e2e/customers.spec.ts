import { test, expect } from "@playwright/test";
import { mockApiRoutes, expectFieldError, expectNoFieldError } from "./fixtures/test-helpers";

test.describe("Customer Form Validation", () => {
  test.beforeEach(async ({ page }) => {
    await mockApiRoutes(page);
    await page.goto("/customers");
    // Click "Add New Customer" button
    await page.getByRole("button", { name: /add new customer/i }).click();
  });

  test("shows errors for empty required fields", async ({ page }) => {
    await page.getByRole("button", { name: "Save Customer" }).click();
    await expectFieldError(page, "Customer name is required");
    await expectFieldError(page, "Username is required");
    await expectFieldError(page, "Password is required");
  });

  test("shows error for short username", async ({ page }) => {
    await page.getByPlaceholder("John Doe").fill("Test Customer");
    await page.getByPlaceholder("johndoe").fill("ab");
    await page.getByPlaceholder("********").fill("password123");
    await page.getByRole("button", { name: "Save Customer" }).click();
    await expectFieldError(page, "Username must be at least 3 characters");
  });

  test("shows error for short password", async ({ page }) => {
    await page.getByPlaceholder("John Doe").fill("Test Customer");
    await page.getByPlaceholder("johndoe").fill("testuser");
    await page.getByPlaceholder("********").fill("12345");
    await page.getByRole("button", { name: "Save Customer" }).click();
    await expectFieldError(page, "Password must be at least 6 characters");
  });

  test("shows error for invalid email in array", async ({ page }) => {
    await page.getByPlaceholder("John Doe").fill("Test Customer");
    await page.getByPlaceholder("johndoe").fill("testuser");
    await page.getByPlaceholder("********").fill("password123");
    await page.getByPlaceholder("john@example.com").fill("not-an-email");
    await page.getByRole("button", { name: "Save Customer" }).click();
    await expectFieldError(page, "Invalid email address");
  });

  test("shows error for invalid phone in array", async ({ page }) => {
    await page.getByPlaceholder("John Doe").fill("Test Customer");
    await page.getByPlaceholder("johndoe").fill("testuser");
    await page.getByPlaceholder("********").fill("password123");
    await page.getByPlaceholder("+1 555 000 0000").fill("abc");
    await page.getByRole("button", { name: "Save Customer" }).click();
    await expectFieldError(page, "Invalid phone number");
  });

  test("clears error on typing", async ({ page }) => {
    await page.getByRole("button", { name: "Save Customer" }).click();
    await expectFieldError(page, "Customer name is required");
    await page.getByPlaceholder("John Doe").fill("A");
    await expectNoFieldError(page, "Customer name is required");
  });
});
