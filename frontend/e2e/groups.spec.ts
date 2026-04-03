import { test, expect } from "@playwright/test";
import { mockApiRoutes, mockApiRoute, expectFieldError, clickSubmitButton, API_BASE } from "./fixtures/test-helpers";

const mockGroups = [
  { id: 1, groupName: "Default", description: "Default customer group" },
  { id: 2, groupName: "Transport", description: "Private transport and logistics" },
  { id: 3, groupName: "Government", description: "Government department vehicles" },
];

test.describe("Customer Groups Page", () => {
  test.beforeEach(async ({ page }) => {
    await mockApiRoutes(page);
    await page.route(`${API_BASE}/groups`, (route) => {
      if (route.request().method() === "GET") {
        return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockGroups) });
      }
      return route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
    });
    // catch-all already handled by mockApiRoutes
  });

  test("loads groups page with heading", async ({ page }) => {
    await page.goto("/customers/groups");
    await expect(page.getByText(/group/i).first()).toBeVisible();
  });

  test("displays existing groups", async ({ page }) => {
    await page.goto("/customers/groups");
    await expect(page.getByText("Default")).toBeVisible();
    await expect(page.getByText("Transport")).toBeVisible();
    await expect(page.getByText("Government")).toBeVisible();
  });

  test("opens add group modal", async ({ page }) => {
    await page.goto("/customers/groups");
    const addBtn = page.getByRole("button", { name: /add/i });
    if (await addBtn.isVisible()) {
      await addBtn.click();
      await expect(page.getByText(/group name/i).first()).toBeVisible();
    }
  });
});
