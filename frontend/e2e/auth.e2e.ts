import { test, expect } from "./fixtures/auth.fixture";

test.describe("Auth Flow", () => {
  test("login with valid passcode redirects to dashboard", async ({ loggedInPage: page }) => {
    // loggedInPage fixture already logs in and waits for /dashboard
    await expect(page).toHaveURL(/\/dashboard/);
    // Sidebar should show user name
    await expect(page.getByText(/StopForFuel/)).toBeVisible();
  });

  test("login with invalid passcode shows error", async ({ page }) => {
    await page.goto("/login");
    await page.locator("#phone").fill("9840000000");
    await page.locator("#passcode").fill("9999");
    await page.getByRole("button", { name: "Sign In" }).click();

    // Should show error message and stay on login page
    await expect(page.locator(".text-red-600, .text-red-400")).toBeVisible({ timeout: 5000 });
    await expect(page).toHaveURL(/\/login/);
  });

  test("login with invalid phone shows validation error", async ({ page }) => {
    await page.goto("/login");
    await page.locator("#phone").fill("123");
    await page.locator("#passcode").fill("1234");
    await page.getByRole("button", { name: "Sign In" }).click();

    await expect(page.getByText("Enter a valid 10-digit mobile number")).toBeVisible();
    await expect(page).toHaveURL(/\/login/);
  });

  test("login with invalid passcode format shows validation error", async ({ page }) => {
    await page.goto("/login");
    await page.locator("#phone").fill("9840000000");
    await page.locator("#passcode").fill("12");
    await page.getByRole("button", { name: "Sign In" }).click();

    await expect(page.getByText("Enter a valid 4-digit passcode")).toBeVisible();
    await expect(page).toHaveURL(/\/login/);
  });

  test("unauthenticated user is redirected to login", async ({ page }) => {
    await page.goto("/dashboard");
    // Should redirect to login with returnTo param
    await expect(page).toHaveURL(/\/login/);
  });

  test("forgot passcode link is visible", async ({ page }) => {
    await page.goto("/login");
    await expect(page.getByText("Forgot Passcode?")).toBeVisible();
  });
});
