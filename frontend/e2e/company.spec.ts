import { test, expect } from "@playwright/test";
import { mockApiRoutes, API_BASE } from "./fixtures/test-helpers";

const mockCompany = {
  id: 1,
  name: "StopForFuel Pvt Ltd",
  openDate: "2020-01-01",
  sapCode: "SAP001",
  gstNo: "33AABCS1234F1Z5",
  site: "Chennai",
  type: "COCO",
  address: "Chennai, Tamil Nadu",
};

test.describe("Company Settings Page", () => {
  test.beforeEach(async ({ page }) => {
    await mockApiRoutes(page);
    await page.route(`${API_BASE}/companies*`, (route) => {
      if (route.request().method() === "GET") {
        return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify([mockCompany]) });
      }
      return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockCompany) });
    });
  });

  test("loads company page", async ({ page }) => {
    await page.goto("/company");
    await expect(page.getByText(/company/i).first()).toBeVisible();
  });

  test("displays company information", async ({ page }) => {
    await page.goto("/company");
    await expect(page.getByText("StopForFuel Pvt Ltd")).toBeVisible();
  });

  test("displays company details", async ({ page }) => {
    await page.goto("/company");
    await expect(page.getByText("SAP001")).toBeVisible();
  });
});
