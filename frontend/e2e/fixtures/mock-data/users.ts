/** Shared user constants for test mocking and E2E auth */

export const API_BASE = "http://localhost:8080/api";

/** Owner user with all permissions — used as default mock auth user */
export const DEV_USER = {
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
    "CUSTOMER_MANAGE", "EMPLOYEE_MANAGE", "PRODUCT_MANAGE",
    "STATION_MANAGE", "INVENTORY_MANAGE", "SHIFT_MANAGE", "INVOICE_MANAGE",
    "PAYMENT_MANAGE", "FINANCE_MANAGE", "SETTINGS_MANAGE", "USER_MANAGE",
  ],
};

/** Cashier user with limited permissions */
export const CASHIER_USER = {
  id: 2,
  cognitoId: "dev-cashier-001",
  username: "cashier",
  name: "Dev Cashier",
  email: "cashier@stopforfuel.com",
  role: "CASHIER",
  permissions: [
    "DASHBOARD_VIEW", "SHIFT_VIEW", "SHIFT_MANAGE",
    "INVOICE_VIEW", "INVOICE_MANAGE",
    "CUSTOMER_VIEW",
  ],
};

/** Employee/attendant user with minimal permissions */
export const EMPLOYEE_USER = {
  id: 3,
  cognitoId: "dev-employee-001",
  username: "employee",
  name: "Dev Employee",
  email: "employee@stopforfuel.com",
  role: "EMPLOYEE",
  permissions: [
    "DASHBOARD_VIEW", "SHIFT_VIEW",
  ],
};

/** Seed data credentials for real E2E login */
export const SEED_OWNER = {
  phone: "9840000000",
  passcode: "1234",
};
