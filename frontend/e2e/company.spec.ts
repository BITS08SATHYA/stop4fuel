import { test, expect } from "@playwright/test";

const API_BASE = "http://localhost:8080/api";

const DEV_USER = {
  id: 1,
  cognitoId: "dev-user-001",
  username: "owner",
  name: "Dev Owner",
  email: "owner@stopforfuel.com",
  role: "OWNER",
  permissions: [
    "DASHBOARD_VIEW", "CUSTOMER_VIEW", "EMPLOYEE_VIEW", "PRODUCT_VIEW",
    "STATION_VIEW", "INVENTORY_VIEW", "SHIFT_VIEW", "INVOICE_VIEW",
    "PAYMENT_VIEW", "FINANCE_VIEW", "SETTINGS_VIEW",
  ],
};

// The CompanyDetails component fetches from /companies (not /company)
// and uses field "name" (not "companyName")
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
    await page.route(`${API_BASE}/auth/me`, (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(DEV_USER) })
    );
    // CompanyDetails fetches from /companies and expects an array
    await page.route(`${API_BASE}/companies*`, (route) => {
      if (route.request().method() === "GET") {
        return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify([mockCompany]) });
      }
      return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockCompany) });
    });
    await page.route(`${API_BASE}/**`, (route) => {
      const url = route.request().url();
      if (url.includes("/auth/me") || url.includes("/companies")) return;
      route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
    });
  });

  test("loads company page", async ({ page }) => {
    await page.goto("/company");
    // The heading is "Company Details" (h2)
    await expect(page.getByText(/company/i).first()).toBeVisible();
  });

  test("displays company information", async ({ page }) => {
    await page.goto("/company");
    // The form input with id="name" should have the company name as value
    // The form input with id="name" should have the company name as value
    await expect(page.locator('input#name')).toHaveValue("StopForFuel Pvt Ltd");
  });

  test("has save button", async ({ page }) => {
    await page.goto("/company");
    // Button text is "Save Changes"
    const saveBtn = page.getByRole("button", { name: /save/i });
    await expect(saveBtn.first()).toBeVisible();
  });
});
