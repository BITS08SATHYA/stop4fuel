import { test, expect } from "@playwright/test";

test.describe("Login Page (mock)", () => {
  test("renders login form with branding", async ({ page }) => {
    await page.goto("/login");
    await expect(page.getByText("StopForFuel")).toBeVisible();
    await expect(page.getByText("Fuel Station Management System")).toBeVisible();
    await expect(page.locator("#phone")).toBeVisible();
    await expect(page.locator("#passcode")).toBeVisible();
    await expect(page.getByRole("button", { name: "Sign In" })).toBeVisible();
  });

  test("validates short phone number", async ({ page }) => {
    await page.goto("/login");
    // Remove HTML required attributes so JS validation runs
    await page.locator("#phone").evaluate(el => el.removeAttribute("required"));
    await page.locator("#passcode").evaluate(el => el.removeAttribute("required"));
    await page.locator("#phone").fill("98400");
    await page.locator("#passcode").fill("1234");
    await page.getByRole("button", { name: "Sign In" }).click();
    await expect(page.getByText("Enter a valid 10-digit mobile number")).toBeVisible();
  });

  test("validates short passcode", async ({ page }) => {
    await page.goto("/login");
    await page.locator("#phone").evaluate(el => el.removeAttribute("required"));
    await page.locator("#passcode").evaluate(el => el.removeAttribute("required"));
    await page.locator("#phone").fill("9840000000");
    await page.locator("#passcode").fill("12");
    await page.getByRole("button", { name: "Sign In" }).click();
    await expect(page.getByText("Enter a valid 4-digit passcode")).toBeVisible();
  });

  test("phone input only accepts digits", async ({ page }) => {
    await page.goto("/login");
    await page.locator("#phone").fill("abc9840000000xyz");
    await expect(page.locator("#phone")).toHaveValue("9840000000");
  });

  test("passcode input is limited to 4 digits", async ({ page }) => {
    await page.goto("/login");
    await page.locator("#passcode").fill("123456");
    await expect(page.locator("#passcode")).toHaveValue("1234");
  });

  test("forgot passcode link navigates correctly", async ({ page }) => {
    await page.goto("/login");
    const link = page.getByText("Forgot Passcode?");
    await expect(link).toBeVisible();
    await expect(link).toHaveAttribute("href", "/forgot-passcode");
  });
});
