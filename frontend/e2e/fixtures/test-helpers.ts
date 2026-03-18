import { Page, expect } from "@playwright/test";

const API_BASE = "http://localhost:8080/api";

/**
 * Mock API routes to prevent real backend calls during tests.
 * Routes return empty arrays for GETs and 200 for mutations.
 */
export async function mockApiRoutes(page: Page) {
  // Block all API calls with mock responses
  await page.route(`${API_BASE}/**`, async (route) => {
    const method = route.request().method();
    if (method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
    } else {
      await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
    }
  });
}

/**
 * Mock specific API route with custom data.
 */
export async function mockApiRoute(page: Page, urlPattern: string, data: any) {
  await page.route(urlPattern, async (route) => {
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(data) });
  });
}

/**
 * Assert that a field error message is visible.
 */
export async function expectFieldError(page: Page, errorText: string) {
  const error = page.locator(`[role="alert"]`, { hasText: errorText });
  await expect(error).toBeVisible();
}

/**
 * Assert that a field error message is NOT visible.
 */
export async function expectNoFieldError(page: Page, errorText: string) {
  const error = page.locator(`[role="alert"]`, { hasText: errorText });
  await expect(error).not.toBeVisible();
}

/**
 * Click the submit/save button in a modal form.
 */
export async function clickSubmitButton(page: Page, buttonText: string) {
  await page.getByRole("button", { name: buttonText }).click();
}

/**
 * Open the "Add New" modal by clicking the add button.
 */
export async function clickAddButton(page: Page, buttonText: string) {
  await page.getByRole("button", { name: buttonText }).click();
}
