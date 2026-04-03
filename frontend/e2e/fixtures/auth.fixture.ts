import { test as base, Page } from "@playwright/test";
import { mockApiRoutes } from "./test-helpers";
import { API_BASE, SEED_OWNER } from "./mock-data/users";

type AuthFixtures = {
  /** Page with all API routes mocked (for mock tests) */
  authenticatedPage: Page;
  /** Page logged in via real passcode auth (for E2E tests) */
  loggedInPage: Page;
};

export const test = base.extend<AuthFixtures>({
  authenticatedPage: async ({ page }, use) => {
    await mockApiRoutes(page);
    await use(page);
  },

  loggedInPage: async ({ page }, use) => {
    // Navigate to login page
    await page.goto("/login");

    // Fill phone number
    await page.locator("#phone").fill(SEED_OWNER.phone);

    // Fill passcode
    await page.locator("#passcode").fill(SEED_OWNER.passcode);

    // Click sign in
    await page.getByRole("button", { name: "Sign In" }).click();

    // Wait for redirect to dashboard
    await page.waitForURL("**/dashboard", { timeout: 15000 });

    await use(page);
  },
});

export { expect } from "@playwright/test";
