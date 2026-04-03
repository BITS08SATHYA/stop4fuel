import { Page, expect } from "@playwright/test";
import { API_BASE, DEV_USER } from "./mock-data/users";

// Re-export for convenience
export { API_BASE, DEV_USER } from "./mock-data/users";

/**
 * Mock API routes to prevent real backend calls during tests.
 * Routes return empty arrays for GETs and 200 for mutations.
 * Always mocks /auth/me with a valid dev user.
 */
export async function mockApiRoutes(page: Page) {
  // Set a fake auth token so AuthProvider loads the user via /auth/me
  await page.addInitScript(() => {
    localStorage.setItem("sff-token", "mock-test-token");
    document.cookie = "sff-auth-session=mock-test-token; path=/";
  });

  // Block all API calls with mock responses
  await page.route(`${API_BASE}/**`, async (route) => {
    const url = route.request().url();
    const method = route.request().method();

    // Auth endpoint must return a valid user
    if (url.includes("/auth/me") && method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(DEV_USER) });
      return;
    }

    // Endpoints that return objects (not arrays) — return empty object to prevent crashes
    const objectEndpoints = ["/dashboard/stats", "/credit/overview", "/summary"];
    if (method === "GET" && objectEndpoints.some(ep => url.includes(ep))) {
      await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
      return;
    }

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
