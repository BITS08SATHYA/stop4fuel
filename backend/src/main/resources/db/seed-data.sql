-- =========================================
-- StopForFuel Comprehensive Seed Data
-- Runs on every application boot (idempotent)
-- Separator: ;; (configured in application.yml)
-- =========================================

-- =====================
-- PHASE 0: Core Reference Data
-- =====================

-- Payment Modes: now a Java enum (PaymentMode), no DB table needed

-- Parties (no unique constraint, use count guard)
DO $$
BEGIN
IF (SELECT COUNT(*) FROM party) = 0 THEN
    INSERT INTO party (party_type) VALUES ('Local'), ('Statement');
END IF;
END $$;;

-- Roles (unique constraint on role_type)
INSERT INTO roles (role_type) VALUES ('CUSTOMER') ON CONFLICT (role_type) DO NOTHING;;
INSERT INTO roles (role_type) VALUES ('EMPLOYEE') ON CONFLICT (role_type) DO NOTHING;;
INSERT INTO roles (role_type) VALUES ('DEALER') ON CONFLICT (role_type) DO NOTHING;;
INSERT INTO roles (role_type) VALUES ('OWNER') ON CONFLICT (role_type) DO NOTHING;;
INSERT INTO roles (role_type) VALUES ('ADMIN') ON CONFLICT (role_type) DO NOTHING;;
INSERT INTO roles (role_type) VALUES ('CASHIER') ON CONFLICT (role_type) DO NOTHING;;

-- Add action and system_default columns if missing (idempotent)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'permissions' AND column_name = 'action') THEN
        ALTER TABLE permissions ADD COLUMN action VARCHAR(255) NOT NULL DEFAULT 'VIEW';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'permissions' AND column_name = 'system_default') THEN
        ALTER TABLE permissions ADD COLUMN system_default BOOLEAN NOT NULL DEFAULT true;
    END IF;
END $$;;

-- Migrate old MANAGE permissions to fine-grained CREATE/UPDATE/DELETE
DO $$
DECLARE
    v_manage_id BIGINT;
    v_module TEXT;
    v_new_code TEXT;
    v_new_action TEXT;
    v_new_desc TEXT;
    v_new_perm_id BIGINT;
    v_role_rec RECORD;
BEGIN
    -- For each _MANAGE permission that still exists, create _CREATE, _UPDATE, _DELETE
    FOR v_manage_id, v_module IN
        SELECT id, module FROM permissions WHERE code LIKE '%_MANAGE'
    LOOP
        FOREACH v_new_action IN ARRAY ARRAY['CREATE', 'UPDATE', 'DELETE']
        LOOP
            v_new_code := v_module || '_' || v_new_action;
            v_new_desc := initcap(v_new_action) || ' ' || lower(v_module);
            INSERT INTO permissions (code, description, module, action, system_default)
                VALUES (v_new_code, v_new_desc, v_module, v_new_action, true)
                ON CONFLICT (code) DO NOTHING;
        END LOOP;

        -- Copy role assignments from _MANAGE to the 3 new permissions
        FOR v_role_rec IN SELECT role_id FROM role_permissions WHERE permission_id = v_manage_id
        LOOP
            INSERT INTO role_permissions (role_id, permission_id)
                SELECT v_role_rec.role_id, p.id FROM permissions p
                WHERE p.module = v_module AND p.action IN ('CREATE', 'UPDATE', 'DELETE')
                ON CONFLICT DO NOTHING;
        END LOOP;

        -- Delete old _MANAGE role assignments and permission
        DELETE FROM role_permissions WHERE permission_id = v_manage_id;
        DELETE FROM permissions WHERE id = v_manage_id;
    END LOOP;
    RAISE NOTICE 'Migrated MANAGE to fine-grained permissions';
END $$;;

-- Backfill action column for existing VIEW permissions
UPDATE permissions SET action = 'VIEW' WHERE code LIKE '%_VIEW' AND action = 'VIEW';;
UPDATE permissions SET action = 'GENERATE' WHERE code = 'REPORT_GENERATE';;

-- Seed fine-grained permissions (fresh installs)
DO $$
BEGIN
IF (SELECT COUNT(*) FROM permissions) = 0 THEN
    INSERT INTO permissions (code, description, module, action, system_default) VALUES
        ('DASHBOARD_VIEW', 'View dashboard', 'DASHBOARD', 'VIEW', true),
        ('CUSTOMER_VIEW', 'View customers', 'CUSTOMER', 'VIEW', true),
        ('CUSTOMER_CREATE', 'Create customers', 'CUSTOMER', 'CREATE', true),
        ('CUSTOMER_UPDATE', 'Update customers', 'CUSTOMER', 'UPDATE', true),
        ('CUSTOMER_DELETE', 'Delete customers', 'CUSTOMER', 'DELETE', true),
        ('EMPLOYEE_VIEW', 'View employees', 'EMPLOYEE', 'VIEW', true),
        ('EMPLOYEE_CREATE', 'Create employees', 'EMPLOYEE', 'CREATE', true),
        ('EMPLOYEE_UPDATE', 'Update employees', 'EMPLOYEE', 'UPDATE', true),
        ('EMPLOYEE_DELETE', 'Delete employees', 'EMPLOYEE', 'DELETE', true),
        ('PRODUCT_VIEW', 'View products', 'PRODUCT', 'VIEW', true),
        ('PRODUCT_CREATE', 'Create products', 'PRODUCT', 'CREATE', true),
        ('PRODUCT_UPDATE', 'Update products', 'PRODUCT', 'UPDATE', true),
        ('PRODUCT_DELETE', 'Delete products', 'PRODUCT', 'DELETE', true),
        ('STATION_VIEW', 'View station layout', 'STATION', 'VIEW', true),
        ('STATION_CREATE', 'Create station layout', 'STATION', 'CREATE', true),
        ('STATION_UPDATE', 'Update station layout', 'STATION', 'UPDATE', true),
        ('STATION_DELETE', 'Delete station layout', 'STATION', 'DELETE', true),
        ('INVENTORY_VIEW', 'View inventory', 'INVENTORY', 'VIEW', true),
        ('INVENTORY_CREATE', 'Create inventory', 'INVENTORY', 'CREATE', true),
        ('INVENTORY_UPDATE', 'Update inventory', 'INVENTORY', 'UPDATE', true),
        ('INVENTORY_DELETE', 'Delete inventory', 'INVENTORY', 'DELETE', true),
        ('SHIFT_VIEW', 'View shifts', 'SHIFT', 'VIEW', true),
        ('SHIFT_CREATE', 'Create shifts', 'SHIFT', 'CREATE', true),
        ('SHIFT_UPDATE', 'Update shifts', 'SHIFT', 'UPDATE', true),
        ('SHIFT_DELETE', 'Delete shifts', 'SHIFT', 'DELETE', true),
        ('INVOICE_VIEW', 'View invoices', 'INVOICE', 'VIEW', true),
        ('INVOICE_CREATE', 'Create invoices', 'INVOICE', 'CREATE', true),
        ('INVOICE_UPDATE', 'Update invoices', 'INVOICE', 'UPDATE', true),
        ('INVOICE_DELETE', 'Delete invoices', 'INVOICE', 'DELETE', true),
        ('PAYMENT_VIEW', 'View payments', 'PAYMENT', 'VIEW', true),
        ('PAYMENT_CREATE', 'Create payments', 'PAYMENT', 'CREATE', true),
        ('PAYMENT_UPDATE', 'Update payments', 'PAYMENT', 'UPDATE', true),
        ('PAYMENT_DELETE', 'Delete payments', 'PAYMENT', 'DELETE', true),
        ('FINANCE_VIEW', 'View finance', 'FINANCE', 'VIEW', true),
        ('FINANCE_CREATE', 'Create finance', 'FINANCE', 'CREATE', true),
        ('FINANCE_UPDATE', 'Update finance', 'FINANCE', 'UPDATE', true),
        ('FINANCE_DELETE', 'Delete finance', 'FINANCE', 'DELETE', true),
        ('PURCHASE_VIEW', 'View purchases', 'PURCHASE', 'VIEW', true),
        ('PURCHASE_CREATE', 'Create purchases', 'PURCHASE', 'CREATE', true),
        ('PURCHASE_UPDATE', 'Update purchases', 'PURCHASE', 'UPDATE', true),
        ('PURCHASE_DELETE', 'Delete purchases', 'PURCHASE', 'DELETE', true),
        ('REPORT_VIEW', 'View reports', 'REPORT', 'VIEW', true),
        ('REPORT_GENERATE', 'Generate reports', 'REPORT', 'GENERATE', true),
        ('SETTINGS_VIEW', 'View settings', 'SETTINGS', 'VIEW', true),
        ('SETTINGS_CREATE', 'Create settings', 'SETTINGS', 'CREATE', true),
        ('SETTINGS_UPDATE', 'Update settings', 'SETTINGS', 'UPDATE', true),
        ('SETTINGS_DELETE', 'Delete settings', 'SETTINGS', 'DELETE', true),
        ('USER_VIEW', 'View users', 'USER', 'VIEW', true),
        ('USER_CREATE', 'Create users', 'USER', 'CREATE', true),
        ('USER_UPDATE', 'Update users', 'USER', 'UPDATE', true),
        ('USER_DELETE', 'Delete users', 'USER', 'DELETE', true);
    RAISE NOTICE 'Seeded fine-grained permissions';
END IF;
END $$;;

-- Assign permissions to CASHIER role
DO $$
DECLARE v_role_id BIGINT;
BEGIN
SELECT id INTO v_role_id FROM roles WHERE role_type = 'CASHIER';
IF v_role_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM role_permissions WHERE role_id = v_role_id) THEN
    INSERT INTO role_permissions (role_id, permission_id)
    SELECT v_role_id, id FROM permissions WHERE code IN (
        'DASHBOARD_VIEW',
        'SHIFT_VIEW', 'SHIFT_CREATE', 'SHIFT_UPDATE',
        'INVOICE_VIEW', 'INVOICE_CREATE', 'INVOICE_UPDATE',
        'PAYMENT_VIEW', 'PAYMENT_CREATE', 'PAYMENT_UPDATE',
        'FINANCE_VIEW'
    )
    ON CONFLICT DO NOTHING;
    RAISE NOTICE 'Assigned permissions to CASHIER';
END IF;
END $$;;

-- Assign permissions to ADMIN role (everything except settings/user delete)
DO $$
DECLARE v_role_id BIGINT;
BEGIN
SELECT id INTO v_role_id FROM roles WHERE role_type = 'ADMIN';
IF v_role_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM role_permissions WHERE role_id = v_role_id) THEN
    INSERT INTO role_permissions (role_id, permission_id)
    SELECT v_role_id, id FROM permissions
    WHERE code NOT IN ('SETTINGS_DELETE', 'USER_DELETE')
    ON CONFLICT DO NOTHING;
    RAISE NOTICE 'Assigned permissions to ADMIN';
END IF;
END $$;;

-- Assign permissions to EMPLOYEE role
DO $$
DECLARE v_role_id BIGINT;
BEGIN
SELECT id INTO v_role_id FROM roles WHERE role_type = 'EMPLOYEE';
IF v_role_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM role_permissions WHERE role_id = v_role_id) THEN
    INSERT INTO role_permissions (role_id, permission_id)
    SELECT v_role_id, id FROM permissions WHERE code IN (
        'DASHBOARD_VIEW', 'SHIFT_VIEW', 'INVENTORY_VIEW'
    )
    ON CONFLICT DO NOTHING;
    RAISE NOTICE 'Assigned permissions to EMPLOYEE';
END IF;
END $$;;

-- Customer Groups (check by name)
DO $$
BEGIN
IF NOT EXISTS (SELECT 1 FROM customer_group WHERE group_name = 'Default') THEN
    INSERT INTO customer_group (group_name, description) VALUES ('Default', 'Default customer group');
END IF;
END $$;;

-- Vehicle Types
DO $$
BEGIN
IF (SELECT COUNT(*) FROM vehicle_type) = 0 THEN
    INSERT INTO vehicle_type (type_name, description, created_at, updated_at) VALUES
        ('Car', 'Standard passenger car', NOW(), NOW()),
        ('Bus', 'Public or private bus', NOW(), NOW()),
        ('Truck', 'Heavy duty truck', NOW(), NOW()),
        ('Jeep', 'Off-road vehicle', NOW(), NOW()),
        ('Bike', 'Motorcycle or scooter', NOW(), NOW());
END IF;
END $$;;

-- Products (Fuel)
DO $$
BEGIN
IF (SELECT COUNT(*) FROM product) = 0 THEN
    INSERT INTO product (scid, name, hsn_code, price, category, unit, brand, fuel_family, active, created_at, updated_at) VALUES
        (1, 'Petrol', '27101211', 107.50, 'FUEL', 'LITERS', 'IOCL', 'PETROL', true, NOW(), NOW()),
        (1, 'Diesel', '27101990', 93.20, 'FUEL', 'LITERS', 'IOCL', 'DIESEL', true, NOW(), NOW()),
        (1, 'Xtra Premium', '27101210', 112.00, 'FUEL', 'LITERS', 'IOCL', 'PETROL', true, NOW(), NOW());
END IF;
END $$;;

-- Tanks (linked to fuel products)
DO $$
BEGIN
IF (SELECT COUNT(*) FROM tank) = 0 THEN
    INSERT INTO tank (scid, name, capacity, available_stock, product_id, active, created_at, updated_at) VALUES
        (1, 'Tank-1', 12000.0, 0.0, (SELECT id FROM product WHERE name = 'Petrol' LIMIT 1), true, NOW(), NOW()),
        (1, 'Tank-2', 6000.0, 0.0, (SELECT id FROM product WHERE name = 'Xtra Premium' LIMIT 1), true, NOW(), NOW()),
        (1, 'Tank-3', 10000.0, 0.0, (SELECT id FROM product WHERE name = 'Diesel' LIMIT 1), true, NOW(), NOW());
END IF;
END $$;;

-- Pumps
DO $$
BEGIN
IF (SELECT COUNT(*) FROM pump) = 0 THEN
    INSERT INTO pump (scid, name, active, created_at, updated_at) VALUES
        (1, 'Pump-1', true, NOW(), NOW()),
        (1, 'Pump-2', true, NOW(), NOW()),
        (1, 'Pump-3', true, NOW(), NOW());
END IF;
END $$;;

-- Nozzles (12 nozzles across 3 pumps, station layout)
DO $$
BEGIN
IF (SELECT COUNT(*) FROM nozzle) = 0 THEN
    INSERT INTO nozzle (scid, nozzle_name, tank_id, pump_id, active, created_at, updated_at) VALUES
        -- Pump-1: N-1, N-2 (Petrol/Tank-1), N-7, N-8 (XP/Tank-2)
        (1, 'N-1',  (SELECT id FROM tank WHERE name='Tank-1'), (SELECT id FROM pump WHERE name='Pump-1'), true, NOW(), NOW()),
        (1, 'N-2',  (SELECT id FROM tank WHERE name='Tank-1'), (SELECT id FROM pump WHERE name='Pump-1'), true, NOW(), NOW()),
        (1, 'N-7',  (SELECT id FROM tank WHERE name='Tank-2'), (SELECT id FROM pump WHERE name='Pump-1'), true, NOW(), NOW()),
        (1, 'N-8',  (SELECT id FROM tank WHERE name='Tank-2'), (SELECT id FROM pump WHERE name='Pump-1'), true, NOW(), NOW()),
        -- Pump-2: N-3, N-4 (Petrol/Tank-1), N-9, N-10 (Diesel/Tank-3)
        (1, 'N-3',  (SELECT id FROM tank WHERE name='Tank-1'), (SELECT id FROM pump WHERE name='Pump-2'), true, NOW(), NOW()),
        (1, 'N-4',  (SELECT id FROM tank WHERE name='Tank-1'), (SELECT id FROM pump WHERE name='Pump-2'), true, NOW(), NOW()),
        (1, 'N-9',  (SELECT id FROM tank WHERE name='Tank-3'), (SELECT id FROM pump WHERE name='Pump-2'), true, NOW(), NOW()),
        (1, 'N-10', (SELECT id FROM tank WHERE name='Tank-3'), (SELECT id FROM pump WHERE name='Pump-2'), true, NOW(), NOW()),
        -- Pump-3: N-5, N-6 (Petrol/Tank-1), N-11, N-12 (Diesel/Tank-3)
        (1, 'N-5',  (SELECT id FROM tank WHERE name='Tank-1'), (SELECT id FROM pump WHERE name='Pump-3'), true, NOW(), NOW()),
        (1, 'N-6',  (SELECT id FROM tank WHERE name='Tank-1'), (SELECT id FROM pump WHERE name='Pump-3'), true, NOW(), NOW()),
        (1, 'N-11', (SELECT id FROM tank WHERE name='Tank-3'), (SELECT id FROM pump WHERE name='Pump-3'), true, NOW(), NOW()),
        (1, 'N-12', (SELECT id FROM tank WHERE name='Tank-3'), (SELECT id FROM pump WHERE name='Pump-3'), true, NOW(), NOW());
END IF;
END $$;;

-- Company
DO $$
BEGIN
IF (SELECT COUNT(*) FROM company) = 0 THEN
    INSERT INTO company (scid, name, open_date, sap_code, gst_no, site, type, address, created_at, updated_at) VALUES
        (1, 'StopForFuel Station #1', CURRENT_DATE, '123456', '29AAAAA0000A1Z5', 'Main Highway', 'Dealer Owned', '123 Fuel Road, Bangalore, Karnataka', NOW(), NOW());
END IF;
END $$;;


-- =====================
-- PHASE 1: Suppliers, Oil Types, Groups, UPI, Expense/Leave Types, Employees
-- =====================

-- Suppliers
DO $$
BEGIN
IF (SELECT COUNT(*) FROM supplier) = 0 THEN
    INSERT INTO supplier (scid, name, contact_person, phone, email, active, created_at, updated_at) VALUES
        (1, 'Indian Oil Corporation Ltd', 'Rajesh Sharma', '9841012345', 'iocl.chennai@indianoil.in', true, NOW(), NOW()),
        (1, 'Bharat Petroleum Corp Ltd', 'Arun Kumar', '9841023456', 'bpcl.supply@bharatpetroleum.in', true, NOW(), NOW()),
        (1, 'Castrol India Ltd', 'Pradeep Menon', '9841034567', 'castrol.dealer@castrol.co.in', true, NOW(), NOW());
END IF;
END $$;;

-- Oil Types (unique constraint on name)
INSERT INTO oil_type (name, description, active, created_at, updated_at) VALUES
    ('Engine Oil', 'Lubricant for internal combustion engines', true, NOW(), NOW()) ON CONFLICT (name) DO NOTHING;;
INSERT INTO oil_type (name, description, active, created_at, updated_at) VALUES
    ('Gear Oil', 'Lubricant for transmissions and differentials', true, NOW(), NOW()) ON CONFLICT (name) DO NOTHING;;
INSERT INTO oil_type (name, description, active, created_at, updated_at) VALUES
    ('Coolant', 'Engine cooling and anti-freeze fluid', true, NOW(), NOW()) ON CONFLICT (name) DO NOTHING;;

-- Extra Customer Groups
DO $$
BEGIN
IF NOT EXISTS (SELECT 1 FROM customer_group WHERE group_name = 'Transport') THEN
    INSERT INTO customer_group (group_name, description) VALUES ('Transport', 'Private transport and logistics companies');
END IF;
IF NOT EXISTS (SELECT 1 FROM customer_group WHERE group_name = 'Government') THEN
    INSERT INTO customer_group (group_name, description) VALUES ('Government', 'Government department vehicles');
END IF;
IF NOT EXISTS (SELECT 1 FROM customer_group WHERE group_name = 'Corporate') THEN
    INSERT INTO customer_group (group_name, description) VALUES ('Corporate', 'Corporate fleet accounts');
END IF;
END $$;;

-- UPI Companies
DO $$
BEGIN
IF (SELECT COUNT(*) FROM upi_company) = 0 THEN
    INSERT INTO upi_company (company_name, created_at, updated_at) VALUES
        ('PhonePe', NOW(), NOW()),
        ('GPay', NOW(), NOW()),
        ('Paytm', NOW(), NOW());
END IF;
END $$;;

-- Expense Types
DO $$
BEGIN
IF (SELECT COUNT(*) FROM expense_type) = 0 THEN
    INSERT INTO expense_type (type_name, created_at, updated_at) VALUES
        ('Electricity', NOW(), NOW()),
        ('Water', NOW(), NOW()),
        ('Maintenance', NOW(), NOW()),
        ('Salary', NOW(), NOW()),
        ('Miscellaneous', NOW(), NOW()),
        ('Rent', NOW(), NOW());
END IF;
END $$;;

-- Leave Types
DO $$
BEGIN
IF (SELECT COUNT(*) FROM leave_types) = 0 THEN
    INSERT INTO leave_types (type_name, max_days_per_year, carry_forward, max_carry_forward_days, created_at, updated_at) VALUES
        ('Casual Leave', 12, false, 0, NOW(), NOW()),
        ('Sick Leave', 10, false, 0, NOW(), NOW()),
        ('Earned Leave', 15, true, 10, NOW(), NOW());
END IF;
END $$;;

-- Designations
DO $$
BEGIN
IF (SELECT COUNT(*) FROM designations) = 0 THEN
    INSERT INTO designations (name, default_role, description) VALUES
        ('Manager', 'ADMIN', 'Station manager'),
        ('Cashier', 'CASHIER', 'Station cashier'),
        ('Pump Attendant', 'EMPLOYEE', 'Fuel pump attendant'),
        ('Attendant', 'EMPLOYEE', 'General attendant'),
        ('Supervisor', 'ADMIN', 'Shift supervisor');
    RAISE NOTICE 'Seeded 5 designations';
END IF;
END $$;;

-- Owner User (for dev mode login)
DO $$
DECLARE
    v_id BIGINT;
    v_role_id BIGINT;
BEGIN
IF NOT EXISTS (SELECT 1 FROM users u JOIN roles r ON u.role_id = r.id WHERE r.role_type = 'OWNER') THEN
    SELECT id INTO v_role_id FROM roles WHERE role_type = 'OWNER';
    INSERT INTO person_entity (scid, name, address, person_type, created_at, updated_at)
    VALUES (1, 'Sathipa', 'Chennai', 'Employee', NOW(), NOW())
    RETURNING id INTO v_id;
    INSERT INTO users (id, username, role_id, join_date, status, passcode)
    VALUES (v_id, '9840000000', v_role_id, '2024-01-01', 'Active', '$2a$12$odFww0BaiMCi.x01MilC3eCLpPQ8v.aGKYg89jSCiGKmp7rfajfJW');
    INSERT INTO person_phones (person_id, phone_number) VALUES (v_id, '9840000000');
    INSERT INTO person_emails (person_id, email) VALUES (v_id, 'owner@stopforfuel.com');
    RAISE NOTICE 'Seeded owner user';
END IF;
END $$;;

-- Employees (JOINED inheritance: person_entity -> users -> employees)
DO $$
DECLARE
    v_id BIGINT;
    v_role_id BIGINT;
    v_desig_id BIGINT;
BEGIN
IF (SELECT COUNT(*) FROM employees) = 0 THEN

    -- 1. Murugan S - Attendant
    SELECT id INTO v_role_id FROM roles WHERE role_type = 'EMPLOYEE';
    SELECT id INTO v_desig_id FROM designations WHERE name = 'Attendant';
    INSERT INTO person_entity (scid, name, address, person_type, created_at, updated_at)
    VALUES (1, 'Murugan S', '12 Anna Nagar, Chennai', 'Employee', NOW(), NOW())
    RETURNING id INTO v_id;
    INSERT INTO users (id, username, role_id, join_date, status, passcode)
    VALUES (v_id, 'emp001', v_role_id, '2024-06-15', 'Active', '$2a$12$odFww0BaiMCi.x01MilC3eCLpPQ8v.aGKYg89jSCiGKmp7rfajfJW');
    INSERT INTO employees (id, designation_id, department, employee_code, salary, salary_day,
                           city, state, pincode, bank_name, bank_branch, gender, blood_group, aadhar_number)
    VALUES (v_id, v_desig_id, 'Operations', 'EMP001', 18000, 1,
            'Chennai', 'Tamil Nadu', '600040', 'Indian Bank', 'Anna Nagar', 'Male', 'O+', '123456789012');
    INSERT INTO person_emails (person_id, email) VALUES (v_id, 'murugan@stopforfuel.com');
    INSERT INTO person_phones (person_id, phone_number) VALUES (v_id, '9840011111');

    -- 2. Lakshmi R - Attendant
    INSERT INTO person_entity (scid, name, address, person_type, created_at, updated_at)
    VALUES (1, 'Lakshmi R', '34 T Nagar, Chennai', 'Employee', NOW(), NOW())
    RETURNING id INTO v_id;
    INSERT INTO users (id, username, role_id, join_date, status, passcode)
    VALUES (v_id, 'emp002', v_role_id, '2024-08-01', 'Active', '$2a$12$odFww0BaiMCi.x01MilC3eCLpPQ8v.aGKYg89jSCiGKmp7rfajfJW');
    INSERT INTO employees (id, designation_id, department, employee_code, salary, salary_day,
                           city, state, pincode, bank_name, bank_branch, gender, blood_group, aadhar_number)
    VALUES (v_id, v_desig_id, 'Operations', 'EMP002', 18000, 1,
            'Chennai', 'Tamil Nadu', '600017', 'Indian Bank', 'T Nagar', 'Female', 'B+', '234567890123');
    INSERT INTO person_emails (person_id, email) VALUES (v_id, 'lakshmi@stopforfuel.com');
    INSERT INTO person_phones (person_id, phone_number) VALUES (v_id, '9840022222');

    -- 3. Karthik V - Cashier
    SELECT id INTO v_role_id FROM roles WHERE role_type = 'CASHIER';
    SELECT id INTO v_desig_id FROM designations WHERE name = 'Cashier';
    INSERT INTO person_entity (scid, name, address, person_type, created_at, updated_at)
    VALUES (1, 'Karthik V', '56 Adyar, Chennai', 'Employee', NOW(), NOW())
    RETURNING id INTO v_id;
    INSERT INTO users (id, username, role_id, join_date, status, passcode)
    VALUES (v_id, 'emp003', v_role_id, '2024-03-10', 'Active', '$2a$12$odFww0BaiMCi.x01MilC3eCLpPQ8v.aGKYg89jSCiGKmp7rfajfJW');
    INSERT INTO employees (id, designation_id, department, employee_code, salary, salary_day,
                           city, state, pincode, bank_name, bank_branch, gender, blood_group, aadhar_number)
    VALUES (v_id, v_desig_id, 'Accounts', 'EMP003', 22000, 1,
            'Chennai', 'Tamil Nadu', '600020', 'Indian Bank', 'Adyar', 'Male', 'A+', '345678901234');
    INSERT INTO person_emails (person_id, email) VALUES (v_id, 'karthik@stopforfuel.com');
    INSERT INTO person_phones (person_id, phone_number) VALUES (v_id, '9840033333');

    -- 4. Priya M - Manager
    SELECT id INTO v_role_id FROM roles WHERE role_type = 'ADMIN';
    SELECT id INTO v_desig_id FROM designations WHERE name = 'Manager';
    INSERT INTO person_entity (scid, name, address, person_type, created_at, updated_at)
    VALUES (1, 'Priya M', '78 Velachery, Chennai', 'Employee', NOW(), NOW())
    RETURNING id INTO v_id;
    INSERT INTO users (id, username, role_id, join_date, status, passcode)
    VALUES (v_id, 'emp004', v_role_id, '2023-11-01', 'Active', '$2a$12$odFww0BaiMCi.x01MilC3eCLpPQ8v.aGKYg89jSCiGKmp7rfajfJW');
    INSERT INTO employees (id, designation_id, department, employee_code, salary, salary_day,
                           city, state, pincode, bank_name, bank_branch, gender, blood_group, aadhar_number)
    VALUES (v_id, v_desig_id, 'Management', 'EMP004', 35000, 1,
            'Chennai', 'Tamil Nadu', '600042', 'Indian Bank', 'Velachery', 'Female', 'AB+', '456789012345');
    INSERT INTO person_emails (person_id, email) VALUES (v_id, 'priya@stopforfuel.com');
    INSERT INTO person_phones (person_id, phone_number) VALUES (v_id, '9840044444');

    RAISE NOTICE 'Seeded 4 employees';
END IF;
END $$;;

-- Backfill passcodes for existing employees (passcode: 1234)
UPDATE users SET passcode = '$2a$12$odFww0BaiMCi.x01MilC3eCLpPQ8v.aGKYg89jSCiGKmp7rfajfJW'
WHERE passcode IS NULL AND id IN (SELECT id FROM employees);;


-- =====================
-- PHASE 2: Grade Types & Non-fuel Products
-- =====================

-- Grade Types (unique constraint on name)
DO $$
BEGIN
IF (SELECT COUNT(*) FROM grade_type) = 0 THEN
    INSERT INTO grade_type (scid, name, oil_type_id, description, active, created_at, updated_at) VALUES
        (1, '20W-40', (SELECT id FROM oil_type WHERE name = 'Engine Oil'), 'Standard mineral engine oil',   true, NOW(), NOW()),
        (1, '20W-50', (SELECT id FROM oil_type WHERE name = 'Engine Oil'), 'Heavy duty engine oil',         true, NOW(), NOW()),
        (1, '10W-30', (SELECT id FROM oil_type WHERE name = 'Engine Oil'), 'Semi-synthetic engine oil',     true, NOW(), NOW()),
        (1, '80W-90', (SELECT id FROM oil_type WHERE name = 'Gear Oil'),   'Standard gear oil',             true, NOW(), NOW());
END IF;
END $$;;

-- Non-fuel Products (only seed if we have <= 3 products, i.e., only fuels exist)
DO $$
BEGIN
IF (SELECT COUNT(*) FROM product) <= 3 THEN
    INSERT INTO product (scid, name, price, category, unit, volume, brand, supplier_id, oil_type_id, grade_id, active, created_at, updated_at) VALUES
        (1, 'Servo 4T 20W-40 (1L)',          280, 'LUBRICANT', 'PIECES', 1.0, 'Servo',
            (SELECT id FROM supplier WHERE name = 'Indian Oil Corporation Ltd'),
            (SELECT id FROM oil_type WHERE name = 'Engine Oil'),
            (SELECT id FROM grade_type WHERE name = '20W-40'),
            true, NOW(), NOW()),
        (1, 'Servo 4T 20W-40 (500ml)',       155, 'LUBRICANT', 'PIECES', 0.5, 'Servo',
            (SELECT id FROM supplier WHERE name = 'Indian Oil Corporation Ltd'),
            (SELECT id FROM oil_type WHERE name = 'Engine Oil'),
            (SELECT id FROM grade_type WHERE name = '20W-40'),
            true, NOW(), NOW()),
        (1, 'Castrol Activ 4T 20W-40 (1L)',  319, 'LUBRICANT', 'PIECES', 1.0, 'Castrol',
            (SELECT id FROM supplier WHERE name = 'Castrol India Ltd'),
            (SELECT id FROM oil_type WHERE name = 'Engine Oil'),
            (SELECT id FROM grade_type WHERE name = '20W-40'),
            true, NOW(), NOW()),
        (1, 'Castrol Activ 4T 20W-50 (1L)',  339, 'LUBRICANT', 'PIECES', 1.0, 'Castrol',
            (SELECT id FROM supplier WHERE name = 'Castrol India Ltd'),
            (SELECT id FROM oil_type WHERE name = 'Engine Oil'),
            (SELECT id FROM grade_type WHERE name = '20W-50'),
            true, NOW(), NOW()),
        (1, 'Servo Gear Oil 80W-90 (1L)',    240, 'LUBRICANT', 'PIECES', 1.0, 'Servo',
            (SELECT id FROM supplier WHERE name = 'Indian Oil Corporation Ltd'),
            (SELECT id FROM oil_type WHERE name = 'Gear Oil'),
            (SELECT id FROM grade_type WHERE name = '80W-90'),
            true, NOW(), NOW()),
        (1, 'Coolant Green (1L)',            180, 'LUBRICANT', 'PIECES', 1.0, 'Servo',
            (SELECT id FROM supplier WHERE name = 'Indian Oil Corporation Ltd'),
            (SELECT id FROM oil_type WHERE name = 'Coolant'),
            NULL,
            true, NOW(), NOW()),
        (1, 'Air Freshener',                120, 'ACCESSORY', 'PIECES', NULL, 'Ambipur',
            NULL, NULL, NULL,
            true, NOW(), NOW()),
        (1, 'Tissue Box',                    50, 'ACCESSORY', 'PIECES', NULL, 'Generic',
            NULL, NULL, NULL,
            true, NOW(), NOW());
END IF;
END $$;;


-- =====================
-- PHASE 3: Customers & Vehicles
-- =====================

-- Customers (JOINED inheritance: person_entity -> users -> customer)
DO $$
DECLARE
    v_id BIGINT;
    v_role_id BIGINT;
    v_party_id BIGINT;
    v_group_id BIGINT;
BEGIN
IF (SELECT COUNT(*) FROM customer) = 0 THEN
    SELECT id INTO v_role_id FROM roles WHERE role_type = 'CUSTOMER';
    SELECT id INTO v_party_id FROM party WHERE party_type = 'Statement';

    -- 1. Sri Murugan Transport
    SELECT id INTO v_group_id FROM customer_group WHERE group_name = 'Transport';
    INSERT INTO person_entity (scid, name, address, person_type, created_at, updated_at)
    VALUES (1, 'Sri Murugan Transport', '45 Transport Nagar, Guindy, Chennai', 'Company', NOW(), NOW())
    RETURNING id INTO v_id;
    INSERT INTO users (id, username, password, role_id, join_date, status)
    VALUES (v_id, 'smt_transport', 'pass123', v_role_id, '2025-01-01', 'ACTIVE');
    INSERT INTO customer (id, group_id, party_id, credit_limit_amount, credit_limit_liters, consumed_liters)
    VALUES (v_id, v_group_id, v_party_id, 500000, 20000, 0);
    INSERT INTO person_phones (person_id, phone_number) VALUES (v_id, '9841100001');

    -- 2. Vel Logistics
    INSERT INTO person_entity (scid, name, address, person_type, created_at, updated_at)
    VALUES (1, 'Vel Logistics', '78 Industrial Estate, Ambattur, Chennai', 'Company', NOW(), NOW())
    RETURNING id INTO v_id;
    INSERT INTO users (id, username, password, role_id, join_date, status)
    VALUES (v_id, 'vel_logistics', 'pass123', v_role_id, '2025-01-01', 'ACTIVE');
    INSERT INTO customer (id, group_id, party_id, credit_limit_amount, credit_limit_liters, consumed_liters)
    VALUES (v_id, v_group_id, v_party_id, 300000, 12000, 0);
    INSERT INTO person_phones (person_id, phone_number) VALUES (v_id, '9841100002');

    -- 3. TN State Transport Corp
    SELECT id INTO v_group_id FROM customer_group WHERE group_name = 'Government';
    INSERT INTO person_entity (scid, name, address, person_type, created_at, updated_at)
    VALUES (1, 'TN State Transport Corp', '150 EVR Salai, Koyambedu, Chennai', 'Company', NOW(), NOW())
    RETURNING id INTO v_id;
    INSERT INTO users (id, username, password, role_id, join_date, status)
    VALUES (v_id, 'tnstc_fleet', 'pass123', v_role_id, '2025-01-01', 'ACTIVE');
    INSERT INTO customer (id, group_id, party_id, credit_limit_amount, credit_limit_liters, consumed_liters)
    VALUES (v_id, v_group_id, v_party_id, 1000000, 50000, 0);
    INSERT INTO person_phones (person_id, phone_number) VALUES (v_id, '9841100003');

    -- 4. Infosys Ltd - Chennai
    SELECT id INTO v_group_id FROM customer_group WHERE group_name = 'Corporate';
    INSERT INTO person_entity (scid, name, address, person_type, created_at, updated_at)
    VALUES (1, 'Infosys Ltd - Chennai', 'IT Park, Sholinganallur, Chennai', 'Company', NOW(), NOW())
    RETURNING id INTO v_id;
    INSERT INTO users (id, username, password, role_id, join_date, status)
    VALUES (v_id, 'infosys_chn', 'pass123', v_role_id, '2025-01-01', 'ACTIVE');
    INSERT INTO customer (id, group_id, party_id, credit_limit_amount, credit_limit_liters, consumed_liters)
    VALUES (v_id, v_group_id, v_party_id, 200000, 5000, 0);
    INSERT INTO person_phones (person_id, phone_number) VALUES (v_id, '9841100004');

    -- 5. Raj Kumar K (Individual)
    SELECT id INTO v_group_id FROM customer_group WHERE group_name = 'Default';
    INSERT INTO person_entity (scid, name, address, person_type, created_at, updated_at)
    VALUES (1, 'Raj Kumar K', '22 Gandhi Street, Tambaram, Chennai', 'Individual', NOW(), NOW())
    RETURNING id INTO v_id;
    INSERT INTO users (id, username, password, role_id, join_date, status)
    VALUES (v_id, 'rajkumar_k', 'pass123', v_role_id, '2025-01-01', 'ACTIVE');
    INSERT INTO customer (id, group_id, party_id, credit_limit_amount, credit_limit_liters, consumed_liters)
    VALUES (v_id, v_group_id, v_party_id, 50000, 2000, 0);
    INSERT INTO person_phones (person_id, phone_number) VALUES (v_id, '9841100005');

    -- 6. Sundaram Motors
    SELECT id INTO v_group_id FROM customer_group WHERE group_name = 'Corporate';
    INSERT INTO person_entity (scid, name, address, person_type, created_at, updated_at)
    VALUES (1, 'Sundaram Motors', '90 Mount Road, Guindy, Chennai', 'Company', NOW(), NOW())
    RETURNING id INTO v_id;
    INSERT INTO users (id, username, password, role_id, join_date, status)
    VALUES (v_id, 'sundaram_mtrs', 'pass123', v_role_id, '2025-01-01', 'ACTIVE');
    INSERT INTO customer (id, group_id, party_id, credit_limit_amount, credit_limit_liters, consumed_liters)
    VALUES (v_id, v_group_id, v_party_id, 150000, 8000, 0);
    INSERT INTO person_phones (person_id, phone_number) VALUES (v_id, '9841100006');

    RAISE NOTICE 'Seeded 6 customers';
END IF;
END $$;;

-- Vehicles
DO $$
BEGIN
IF (SELECT COUNT(*) FROM vehicle) = 0 THEN
    INSERT INTO vehicle (vehicle_number, vehicle_type_id, product_id, max_capacity, max_liters_per_month,
                         consumed_liters, status, customer_id, created_at, updated_at) VALUES
        ('TN 01 AB 1234', (SELECT id FROM vehicle_type WHERE type_name='Truck'), (SELECT id FROM product WHERE name='Diesel'),
            200, 5000, 0, 'ACTIVE', (SELECT u.id FROM users u WHERE u.username='smt_transport'), NOW(), NOW()),
        ('TN 01 CD 5678', (SELECT id FROM vehicle_type WHERE type_name='Truck'), (SELECT id FROM product WHERE name='Diesel'),
            200, 5000, 0, 'ACTIVE', (SELECT u.id FROM users u WHERE u.username='smt_transport'), NOW(), NOW()),
        ('TN 01 EF 9012', (SELECT id FROM vehicle_type WHERE type_name='Truck'), (SELECT id FROM product WHERE name='Diesel'),
            150, 4000, 0, 'ACTIVE', (SELECT u.id FROM users u WHERE u.username='vel_logistics'), NOW(), NOW()),
        ('TN 01 GH 3456', (SELECT id FROM vehicle_type WHERE type_name='Truck'), (SELECT id FROM product WHERE name='Diesel'),
            200, 6000, 0, 'ACTIVE', (SELECT u.id FROM users u WHERE u.username='vel_logistics'), NOW(), NOW()),
        ('TN 09 N 1234',  (SELECT id FROM vehicle_type WHERE type_name='Bus'),   (SELECT id FROM product WHERE name='Diesel'),
            300, 10000, 0, 'ACTIVE', (SELECT u.id FROM users u WHERE u.username='tnstc_fleet'), NOW(), NOW()),
        ('TN 09 N 5678',  (SELECT id FROM vehicle_type WHERE type_name='Bus'),   (SELECT id FROM product WHERE name='Diesel'),
            300, 10000, 0, 'ACTIVE', (SELECT u.id FROM users u WHERE u.username='tnstc_fleet'), NOW(), NOW()),
        ('TN 10 AK 4455', (SELECT id FROM vehicle_type WHERE type_name='Car'),   (SELECT id FROM product WHERE name='Petrol'),
            50, 500, 0, 'ACTIVE', (SELECT u.id FROM users u WHERE u.username='infosys_chn'), NOW(), NOW()),
        ('TN 10 BM 6677', (SELECT id FROM vehicle_type WHERE type_name='Car'),   (SELECT id FROM product WHERE name='Petrol'),
            50, 500, 0, 'ACTIVE', (SELECT u.id FROM users u WHERE u.username='infosys_chn'), NOW(), NOW()),
        ('TN 01 CK 8899', (SELECT id FROM vehicle_type WHERE type_name='Car'),   (SELECT id FROM product WHERE name='Petrol'),
            45, 300, 0, 'ACTIVE', (SELECT u.id FROM users u WHERE u.username='rajkumar_k'), NOW(), NOW()),
        ('TN 22 DE 1122', (SELECT id FROM vehicle_type WHERE type_name='Jeep'),  (SELECT id FROM product WHERE name='Diesel'),
            60, 1000, 0, 'ACTIVE', (SELECT u.id FROM users u WHERE u.username='sundaram_mtrs'), NOW(), NOW());
    RAISE NOTICE 'Seeded 10 vehicles';
END IF;
END $$;;


-- =====================
-- PHASE 4: Incentives, Tank Inventory, Godown Stock, Cashier Stock
-- =====================

-- Incentives
DO $$
BEGIN
IF (SELECT COUNT(*) FROM incentive) = 0 THEN
    INSERT INTO incentive (scid, customer_id, product_id, discount_rate, min_quantity, active, created_at, updated_at) VALUES
        (1, (SELECT id FROM users WHERE username='smt_transport'),  (SELECT id FROM product WHERE name='Diesel'), 1.50, 100, true, NOW(), NOW()),
        (1, (SELECT id FROM users WHERE username='smt_transport'),  (SELECT id FROM product WHERE name='Petrol'), 1.00,  50, true, NOW(), NOW()),
        (1, (SELECT id FROM users WHERE username='vel_logistics'),  (SELECT id FROM product WHERE name='Diesel'), 1.25, 100, true, NOW(), NOW()),
        (1, (SELECT id FROM users WHERE username='tnstc_fleet'),    (SELECT id FROM product WHERE name='Diesel'), 2.00, 200, true, NOW(), NOW()),
        (1, (SELECT id FROM users WHERE username='infosys_chn'),    (SELECT id FROM product WHERE name='Petrol'), 0.75,  25, true, NOW(), NOW()),
        (1, (SELECT id FROM users WHERE username='sundaram_mtrs'),  (SELECT id FROM product WHERE name='Diesel'), 1.00,  50, true, NOW(), NOW());
    RAISE NOTICE 'Seeded 6 incentives';
END IF;
END $$;;

-- Tank Inventory (opening stock for today)
DO $$
BEGIN
IF (SELECT COUNT(*) FROM tank_inventory) = 0 THEN
    INSERT INTO tank_inventory (scid, date, tank_id, open_dip, open_stock, income_stock, total_stock, created_at, updated_at) VALUES
        (1, CURRENT_DATE, (SELECT id FROM tank WHERE name='Tank-1'), '85', 8500, 0, 8500, NOW(), NOW()),
        (1, CURRENT_DATE, (SELECT id FROM tank WHERE name='Tank-2'), '40', 4000, 0, 4000, NOW(), NOW()),
        (1, CURRENT_DATE, (SELECT id FROM tank WHERE name='Tank-3'), '70', 7000, 0, 7000, NOW(), NOW());

    -- Update tank available stock to match opening inventory
    UPDATE tank SET available_stock = 8500 WHERE name = 'Tank-1';
    UPDATE tank SET available_stock = 4000 WHERE name = 'Tank-2';
    UPDATE tank SET available_stock = 7000 WHERE name = 'Tank-3';

    RAISE NOTICE 'Seeded 3 tank inventories with opening stock';
END IF;
END $$;;

-- Godown Stock (non-fuel products with shelf locations and reorder levels)
DO $$
BEGIN
IF (SELECT COUNT(*) FROM godown_stock) = 0 THEN
    INSERT INTO godown_stock (scid, product_id, current_stock, reorder_level, max_stock, location, last_restock_date, created_at, updated_at) VALUES
        (1, (SELECT id FROM product WHERE name='Servo 4T 20W-40 (1L)'),         24, 5,  48,  'Shelf A-1', CURRENT_DATE, NOW(), NOW()),
        (1, (SELECT id FROM product WHERE name='Servo 4T 20W-40 (500ml)'),      36, 10, 72,  'Shelf A-2', CURRENT_DATE, NOW(), NOW()),
        (1, (SELECT id FROM product WHERE name='Castrol Activ 4T 20W-40 (1L)'), 18, 5,  36,  'Shelf A-3', CURRENT_DATE, NOW(), NOW()),
        (1, (SELECT id FROM product WHERE name='Castrol Activ 4T 20W-50 (1L)'), 12, 3,  24,  'Shelf A-4', CURRENT_DATE, NOW(), NOW()),
        (1, (SELECT id FROM product WHERE name='Servo Gear Oil 80W-90 (1L)'),   12, 3,  24,  'Shelf B-1', CURRENT_DATE, NOW(), NOW()),
        (1, (SELECT id FROM product WHERE name='Coolant Green (1L)'),            10, 3,  20,  'Shelf B-2', CURRENT_DATE, NOW(), NOW()),
        (1, (SELECT id FROM product WHERE name='Air Freshener'),                 30, 10, 60,  'Shelf C-1', CURRENT_DATE, NOW(), NOW()),
        (1, (SELECT id FROM product WHERE name='Tissue Box'),                    50, 15, 100, 'Shelf C-2', CURRENT_DATE, NOW(), NOW());
    RAISE NOTICE 'Seeded 8 godown stock entries';
END IF;
END $$;;

-- Cashier Stock (popular items at counter)
DO $$
BEGIN
IF (SELECT COUNT(*) FROM cashier_stock) = 0 THEN
    INSERT INTO cashier_stock (scid, product_id, current_stock, max_capacity, created_at, updated_at) VALUES
        (1, (SELECT id FROM product WHERE name='Servo 4T 20W-40 (1L)'),         6,  12, NOW(), NOW()),
        (1, (SELECT id FROM product WHERE name='Castrol Activ 4T 20W-40 (1L)'), 4,   8, NOW(), NOW()),
        (1, (SELECT id FROM product WHERE name='Air Freshener'),                 10, 20, NOW(), NOW()),
        (1, (SELECT id FROM product WHERE name='Tissue Box'),                    15, 30, NOW(), NOW());
    RAISE NOTICE 'Seeded 4 cashier stock entries';
END IF;
END $$;;


-- =====================
-- PHASE 5: Permissions & Role-Permission Mappings
-- =====================

-- Permissions
DO $$
BEGIN
IF (SELECT COUNT(*) FROM permissions) = 0 THEN
    INSERT INTO permissions (code, description, module) VALUES
        ('DASHBOARD_VIEW', 'View dashboards', 'DASHBOARD'),
        ('INVOICE_VIEW', 'View invoices', 'INVOICE'),
        ('INVOICE_CREATE', 'Create invoices', 'INVOICE'),
        ('INVOICE_MODIFY', 'Modify invoices', 'INVOICE'),
        ('INVOICE_DELETE', 'Delete invoices', 'INVOICE'),
        ('CUSTOMER_VIEW', 'View customers', 'CUSTOMER'),
        ('CUSTOMER_MANAGE', 'Create, edit, delete customers', 'CUSTOMER'),
        ('VEHICLE_VIEW', 'View vehicles', 'VEHICLE'),
        ('VEHICLE_MANAGE', 'Manage vehicles', 'VEHICLE'),
        ('PRODUCT_VIEW', 'View products, oil types, grades', 'PRODUCT'),
        ('PRODUCT_MANAGE', 'Manage products', 'PRODUCT'),
        ('STATION_VIEW', 'View tanks, pumps, nozzles', 'STATION'),
        ('STATION_MANAGE', 'Manage station equipment', 'STATION'),
        ('INVENTORY_VIEW', 'View inventory readings and stock', 'INVENTORY'),
        ('INVENTORY_MANAGE', 'Manage inventory', 'INVENTORY'),
        ('SHIFT_VIEW', 'View shifts and transactions', 'SHIFT'),
        ('SHIFT_MANAGE', 'Manage shifts, advances, inflows', 'SHIFT'),
        ('PAYMENT_VIEW', 'View payments, statements, ledger', 'PAYMENT'),
        ('PAYMENT_MANAGE', 'Manage payments and credit', 'PAYMENT'),
        ('EMPLOYEE_VIEW', 'View employees', 'EMPLOYEE'),
        ('EMPLOYEE_MANAGE', 'Manage employees, attendance, leave, salary', 'EMPLOYEE'),
        ('FINANCE_VIEW', 'View expenses and utility bills', 'FINANCE'),
        ('FINANCE_MANAGE', 'Manage expenses and utility bills', 'FINANCE'),
        ('PURCHASE_VIEW', 'View purchase orders and invoices', 'PURCHASE'),
        ('PURCHASE_MANAGE', 'Manage purchases', 'PURCHASE'),
        ('REPORT_VIEW', 'View shift reports', 'REPORT'),
        ('REPORT_GENERATE', 'Generate shift reports', 'REPORT'),
        ('SETTINGS_VIEW', 'View settings and configurations', 'SETTINGS'),
        ('SETTINGS_MANAGE', 'Manage settings and configurations', 'SETTINGS'),
        ('USER_VIEW', 'View user accounts', 'USER_MGMT'),
        ('USER_MANAGE', 'Manage user accounts', 'USER_MGMT');
    RAISE NOTICE 'Seeded 31 permissions';
END IF;
END $$;;

-- Role-Permission Mappings
-- ADMIN gets almost everything except SETTINGS_MANAGE and USER_MANAGE
DO $$
DECLARE
    v_role_id BIGINT;
    v_perm_id BIGINT;
    v_perm RECORD;
BEGIN
IF (SELECT COUNT(*) FROM role_permissions) = 0 THEN

    -- ADMIN role permissions
    SELECT id INTO v_role_id FROM roles WHERE role_type = 'ADMIN';
    IF v_role_id IS NOT NULL THEN
        FOR v_perm IN SELECT id, code FROM permissions WHERE code NOT IN ('SETTINGS_MANAGE', 'USER_MANAGE') LOOP
            INSERT INTO role_permissions (role_id, permission_id) VALUES (v_role_id, v_perm.id);
        END LOOP;
    END IF;

    -- CASHIER role permissions
    SELECT id INTO v_role_id FROM roles WHERE role_type = 'CASHIER';
    IF v_role_id IS NOT NULL THEN
        FOR v_perm IN SELECT id FROM permissions WHERE code IN (
            'DASHBOARD_VIEW', 'INVOICE_VIEW', 'INVOICE_CREATE',
            'INVENTORY_VIEW', 'SHIFT_VIEW', 'SHIFT_MANAGE',
            'REPORT_VIEW', 'PRODUCT_VIEW', 'STATION_VIEW'
        ) LOOP
            INSERT INTO role_permissions (role_id, permission_id) VALUES (v_role_id, v_perm.id);
        END LOOP;
    END IF;

    -- CUSTOMER role permissions (minimal)
    SELECT id INTO v_role_id FROM roles WHERE role_type = 'CUSTOMER';
    IF v_role_id IS NOT NULL THEN
        FOR v_perm IN SELECT id FROM permissions WHERE code IN ('INVOICE_VIEW') LOOP
            INSERT INTO role_permissions (role_id, permission_id) VALUES (v_role_id, v_perm.id);
        END LOOP;
    END IF;

    RAISE NOTICE 'Seeded role-permission mappings';
END IF;
END $$;;


-- =====================
-- PHASE 6: Transactional / Operational Seed Data
-- Shifts, Nozzle Inventory, Invoices, Payments, Advances, Attendance, Expenses
-- =====================

-- Bill Sequence (initialize counters for current fiscal year)
DO $$
DECLARE
    v_fy INTEGER;
BEGIN
IF (SELECT COUNT(*) FROM bill_sequence) = 0 THEN
    -- Fiscal year: if month >= April then current year's last 2 digits, else previous year
    v_fy := CASE WHEN EXTRACT(MONTH FROM CURRENT_DATE) >= 4
                 THEN EXTRACT(YEAR FROM CURRENT_DATE) % 100
                 ELSE (EXTRACT(YEAR FROM CURRENT_DATE) - 1) % 100 END;
    INSERT INTO bill_sequence (type, fy_year, last_number, created_at, updated_at) VALUES
        ('CASH',   v_fy, 10, NOW(), NOW()),
        ('CREDIT', v_fy, 8, NOW(), NOW()),
        ('STMT',   v_fy, 2, NOW(), NOW());
    RAISE NOTICE 'Seeded bill sequences for FY %', v_fy;
END IF;
END $$;;

-- Shifts (6 closed shifts over 3 days + 1 open today)
DO $$
DECLARE
    v_shift1 BIGINT; v_shift2 BIGINT; v_shift3 BIGINT;
    v_shift4 BIGINT; v_shift5 BIGINT; v_shift6 BIGINT;
    v_shift7 BIGINT;
    v_murugan BIGINT; v_lakshmi BIGINT; v_karthik BIGINT;
BEGIN
IF (SELECT COUNT(*) FROM shifts) = 0 THEN

    SELECT id INTO v_murugan FROM users WHERE username = 'emp001';
    SELECT id INTO v_lakshmi FROM users WHERE username = 'emp002';
    SELECT id INTO v_karthik FROM users WHERE username = 'emp003';

    -- Day 1 (3 days ago): Morning + Evening
    INSERT INTO shifts (scid, start_time, end_time, attendant_id, status, created_at, updated_at)
    VALUES (1, (CURRENT_DATE - 3) + TIME '06:00', (CURRENT_DATE - 3) + TIME '14:00', v_murugan, 'CLOSED', NOW(), NOW())
    RETURNING id INTO v_shift1;

    INSERT INTO shifts (scid, start_time, end_time, attendant_id, status, created_at, updated_at)
    VALUES (1, (CURRENT_DATE - 3) + TIME '14:00', (CURRENT_DATE - 3) + TIME '22:00', v_lakshmi, 'CLOSED', NOW(), NOW())
    RETURNING id INTO v_shift2;

    -- Day 2 (2 days ago): Morning + Evening
    INSERT INTO shifts (scid, start_time, end_time, attendant_id, status, created_at, updated_at)
    VALUES (1, (CURRENT_DATE - 2) + TIME '06:00', (CURRENT_DATE - 2) + TIME '14:00', v_karthik, 'CLOSED', NOW(), NOW())
    RETURNING id INTO v_shift3;

    INSERT INTO shifts (scid, start_time, end_time, attendant_id, status, created_at, updated_at)
    VALUES (1, (CURRENT_DATE - 2) + TIME '14:00', (CURRENT_DATE - 2) + TIME '22:00', v_murugan, 'CLOSED', NOW(), NOW())
    RETURNING id INTO v_shift4;

    -- Day 3 (yesterday): Morning + Evening
    INSERT INTO shifts (scid, start_time, end_time, attendant_id, status, created_at, updated_at)
    VALUES (1, (CURRENT_DATE - 1) + TIME '06:00', (CURRENT_DATE - 1) + TIME '14:00', v_lakshmi, 'CLOSED', NOW(), NOW())
    RETURNING id INTO v_shift5;

    INSERT INTO shifts (scid, start_time, end_time, attendant_id, status, created_at, updated_at)
    VALUES (1, (CURRENT_DATE - 1) + TIME '14:00', (CURRENT_DATE - 1) + TIME '22:00', v_karthik, 'CLOSED', NOW(), NOW())
    RETURNING id INTO v_shift6;

    -- Today: Morning shift (OPEN)
    INSERT INTO shifts (scid, start_time, attendant_id, status, created_at, updated_at)
    VALUES (1, CURRENT_DATE + TIME '06:00', v_murugan, 'OPEN', NOW(), NOW())
    RETURNING id INTO v_shift7;

    RAISE NOTICE 'Seeded 7 shifts (6 closed, 1 open)';
END IF;
END $$;;

-- Nozzle Inventory (readings for closed shifts — key nozzles only for brevity)
-- Each shift has opening = previous closing; sales = close - open
DO $$
DECLARE
    v_s1 BIGINT; v_s2 BIGINT; v_s3 BIGINT; v_s4 BIGINT; v_s5 BIGINT; v_s6 BIGINT;
    v_n1 BIGINT; v_n2 BIGINT; v_n3 BIGINT; v_n7 BIGINT; v_n9 BIGINT; v_n11 BIGINT;
BEGIN
IF (SELECT COUNT(*) FROM nozzle_inventory) = 0 THEN

    -- Get shift IDs (ordered by start_time)
    SELECT id INTO v_s1 FROM shifts ORDER BY start_time ASC LIMIT 1 OFFSET 0;
    SELECT id INTO v_s2 FROM shifts ORDER BY start_time ASC LIMIT 1 OFFSET 1;
    SELECT id INTO v_s3 FROM shifts ORDER BY start_time ASC LIMIT 1 OFFSET 2;
    SELECT id INTO v_s4 FROM shifts ORDER BY start_time ASC LIMIT 1 OFFSET 3;
    SELECT id INTO v_s5 FROM shifts ORDER BY start_time ASC LIMIT 1 OFFSET 4;
    SELECT id INTO v_s6 FROM shifts ORDER BY start_time ASC LIMIT 1 OFFSET 5;

    -- Get nozzle IDs
    SELECT id INTO v_n1  FROM nozzle WHERE nozzle_name = 'N-1';
    SELECT id INTO v_n2  FROM nozzle WHERE nozzle_name = 'N-2';
    SELECT id INTO v_n3  FROM nozzle WHERE nozzle_name = 'N-3';
    SELECT id INTO v_n7  FROM nozzle WHERE nozzle_name = 'N-7';
    SELECT id INTO v_n9  FROM nozzle WHERE nozzle_name = 'N-9';
    SELECT id INTO v_n11 FROM nozzle WHERE nozzle_name = 'N-11';

    -- Shift 1 (Day1 AM): N-1 Petrol, N-7 XP, N-9 Diesel
    INSERT INTO nozzle_inventory (scid, shift_id, date, nozzle_id, open_meter_reading, close_meter_reading, sales, created_at, updated_at) VALUES
        (1, v_s1, CURRENT_DATE - 3, v_n1,  50000, 50320, 320, NOW(), NOW()),
        (1, v_s1, CURRENT_DATE - 3, v_n2,  40000, 40280, 280, NOW(), NOW()),
        (1, v_s1, CURRENT_DATE - 3, v_n7,  20000, 20150, 150, NOW(), NOW()),
        (1, v_s1, CURRENT_DATE - 3, v_n9,  80000, 80650, 650, NOW(), NOW()),
        (1, v_s1, CURRENT_DATE - 3, v_n3,  30000, 30200, 200, NOW(), NOW()),
        (1, v_s1, CURRENT_DATE - 3, v_n11, 60000, 60400, 400, NOW(), NOW());

    -- Shift 2 (Day1 PM)
    INSERT INTO nozzle_inventory (scid, shift_id, date, nozzle_id, open_meter_reading, close_meter_reading, sales, created_at, updated_at) VALUES
        (1, v_s2, CURRENT_DATE - 3, v_n1,  50320, 50590, 270, NOW(), NOW()),
        (1, v_s2, CURRENT_DATE - 3, v_n2,  40280, 40510, 230, NOW(), NOW()),
        (1, v_s2, CURRENT_DATE - 3, v_n7,  20150, 20280, 130, NOW(), NOW()),
        (1, v_s2, CURRENT_DATE - 3, v_n9,  80650, 81200, 550, NOW(), NOW()),
        (1, v_s2, CURRENT_DATE - 3, v_n3,  30200, 30380, 180, NOW(), NOW()),
        (1, v_s2, CURRENT_DATE - 3, v_n11, 60400, 60750, 350, NOW(), NOW());

    -- Shift 3 (Day2 AM)
    INSERT INTO nozzle_inventory (scid, shift_id, date, nozzle_id, open_meter_reading, close_meter_reading, sales, created_at, updated_at) VALUES
        (1, v_s3, CURRENT_DATE - 2, v_n1,  50590, 50900, 310, NOW(), NOW()),
        (1, v_s3, CURRENT_DATE - 2, v_n2,  40510, 40800, 290, NOW(), NOW()),
        (1, v_s3, CURRENT_DATE - 2, v_n7,  20280, 20420, 140, NOW(), NOW()),
        (1, v_s3, CURRENT_DATE - 2, v_n9,  81200, 81850, 650, NOW(), NOW()),
        (1, v_s3, CURRENT_DATE - 2, v_n3,  30380, 30600, 220, NOW(), NOW()),
        (1, v_s3, CURRENT_DATE - 2, v_n11, 60750, 61100, 350, NOW(), NOW());

    -- Shift 4 (Day2 PM)
    INSERT INTO nozzle_inventory (scid, shift_id, date, nozzle_id, open_meter_reading, close_meter_reading, sales, created_at, updated_at) VALUES
        (1, v_s4, CURRENT_DATE - 2, v_n1,  50900, 51180, 280, NOW(), NOW()),
        (1, v_s4, CURRENT_DATE - 2, v_n2,  40800, 41050, 250, NOW(), NOW()),
        (1, v_s4, CURRENT_DATE - 2, v_n7,  20420, 20560, 140, NOW(), NOW()),
        (1, v_s4, CURRENT_DATE - 2, v_n9,  81850, 82450, 600, NOW(), NOW()),
        (1, v_s4, CURRENT_DATE - 2, v_n3,  30600, 30810, 210, NOW(), NOW()),
        (1, v_s4, CURRENT_DATE - 2, v_n11, 61100, 61480, 380, NOW(), NOW());

    -- Shift 5 (Day3 AM)
    INSERT INTO nozzle_inventory (scid, shift_id, date, nozzle_id, open_meter_reading, close_meter_reading, sales, created_at, updated_at) VALUES
        (1, v_s5, CURRENT_DATE - 1, v_n1,  51180, 51520, 340, NOW(), NOW()),
        (1, v_s5, CURRENT_DATE - 1, v_n2,  41050, 41360, 310, NOW(), NOW()),
        (1, v_s5, CURRENT_DATE - 1, v_n7,  20560, 20720, 160, NOW(), NOW()),
        (1, v_s5, CURRENT_DATE - 1, v_n9,  82450, 83150, 700, NOW(), NOW()),
        (1, v_s5, CURRENT_DATE - 1, v_n3,  30810, 31050, 240, NOW(), NOW()),
        (1, v_s5, CURRENT_DATE - 1, v_n11, 61480, 61900, 420, NOW(), NOW());

    -- Shift 6 (Day3 PM)
    INSERT INTO nozzle_inventory (scid, shift_id, date, nozzle_id, open_meter_reading, close_meter_reading, sales, created_at, updated_at) VALUES
        (1, v_s6, CURRENT_DATE - 1, v_n1,  51520, 51810, 290, NOW(), NOW()),
        (1, v_s6, CURRENT_DATE - 1, v_n2,  41360, 41620, 260, NOW(), NOW()),
        (1, v_s6, CURRENT_DATE - 1, v_n7,  20720, 20860, 140, NOW(), NOW()),
        (1, v_s6, CURRENT_DATE - 1, v_n9,  83150, 83780, 630, NOW(), NOW()),
        (1, v_s6, CURRENT_DATE - 1, v_n3,  31050, 31270, 220, NOW(), NOW()),
        (1, v_s6, CURRENT_DATE - 1, v_n11, 61900, 62280, 380, NOW(), NOW());

    RAISE NOTICE 'Seeded nozzle inventory for 6 shifts (6 nozzles each)';
END IF;
END $$;;

-- Product Inventory (daily product-level summary for 3 days)
DO $$
DECLARE
    v_petrol BIGINT; v_diesel BIGINT; v_xp BIGINT;
BEGIN
IF (SELECT COUNT(*) FROM product_inventory) = 0 THEN

    SELECT id INTO v_petrol FROM product WHERE name = 'Petrol';
    SELECT id INTO v_diesel FROM product WHERE name = 'Diesel';
    SELECT id INTO v_xp     FROM product WHERE name = 'Xtra Premium';

    -- Day 1 (3 days ago)
    INSERT INTO product_inventory (scid, date, product_id, open_stock, income_stock, total_stock, close_stock, sales, rate, amount, created_at, updated_at) VALUES
        (1, CURRENT_DATE - 3, v_petrol, 8500, 0, 8500, 7400, 1100, 107.50, 118250.00, NOW(), NOW()),
        (1, CURRENT_DATE - 3, v_diesel, 7000, 0, 7000, 5170, 1830, 93.20, 170556.00, NOW(), NOW()),
        (1, CURRENT_DATE - 3, v_xp,     4000, 0, 4000, 3720, 280,  112.00, 31360.00, NOW(), NOW());

    -- Day 2 (2 days ago)
    INSERT INTO product_inventory (scid, date, product_id, open_stock, income_stock, total_stock, close_stock, sales, rate, amount, created_at, updated_at) VALUES
        (1, CURRENT_DATE - 2, v_petrol, 7400, 0, 7400, 6240, 1160, 107.50, 124700.00, NOW(), NOW()),
        (1, CURRENT_DATE - 2, v_diesel, 5170, 5000, 10170, 7990, 2180, 93.20, 203176.00, NOW(), NOW()),
        (1, CURRENT_DATE - 2, v_xp,     3720, 0, 3720, 3440, 280,  112.00, 31360.00, NOW(), NOW());

    -- Day 3 (yesterday)
    INSERT INTO product_inventory (scid, date, product_id, open_stock, income_stock, total_stock, close_stock, sales, rate, amount, created_at, updated_at) VALUES
        (1, CURRENT_DATE - 1, v_petrol, 6240, 6000, 12240, 10580, 1660, 107.50, 178450.00, NOW(), NOW()),
        (1, CURRENT_DATE - 1, v_diesel, 7990, 0, 7990, 5860, 2130, 93.20, 198516.00, NOW(), NOW()),
        (1, CURRENT_DATE - 1, v_xp,     3440, 0, 3440, 3140, 300,  112.00, 33600.00, NOW(), NOW());

    RAISE NOTICE 'Seeded 9 product inventory records (3 days x 3 products)';
END IF;
END $$;;

-- Invoice Bills (10 invoices: 6 cash + 4 credit, spread across shifts)
DO $$
DECLARE
    v_s1 BIGINT; v_s2 BIGINT; v_s3 BIGINT; v_s4 BIGINT; v_s5 BIGINT; v_s6 BIGINT;
    v_cashier BIGINT;
    v_petrol BIGINT; v_diesel BIGINT; v_xp BIGINT;
    v_n1 BIGINT; v_n7 BIGINT; v_n9 BIGINT; v_n11 BIGINT;
    v_smt BIGINT; v_vel BIGINT; v_tnstc BIGINT; v_infosys BIGINT; v_raj BIGINT; v_sundaram BIGINT;
    v_truck1 BIGINT; v_truck3 BIGINT; v_bus1 BIGINT; v_car1 BIGINT; v_car3 BIGINT; v_jeep BIGINT;
    v_fy TEXT;
    v_inv BIGINT;
BEGIN
IF (SELECT COUNT(*) FROM invoice_bill) = 0 THEN

    -- Fiscal year prefix
    v_fy := CASE WHEN EXTRACT(MONTH FROM CURRENT_DATE) >= 4
                 THEN LPAD((EXTRACT(YEAR FROM CURRENT_DATE) % 100)::TEXT, 2, '0')
                 ELSE LPAD(((EXTRACT(YEAR FROM CURRENT_DATE) - 1) % 100)::TEXT, 2, '0') END;

    -- Shift IDs
    SELECT id INTO v_s1 FROM shifts ORDER BY start_time ASC LIMIT 1 OFFSET 0;
    SELECT id INTO v_s2 FROM shifts ORDER BY start_time ASC LIMIT 1 OFFSET 1;
    SELECT id INTO v_s3 FROM shifts ORDER BY start_time ASC LIMIT 1 OFFSET 2;
    SELECT id INTO v_s4 FROM shifts ORDER BY start_time ASC LIMIT 1 OFFSET 3;
    SELECT id INTO v_s5 FROM shifts ORDER BY start_time ASC LIMIT 1 OFFSET 4;
    SELECT id INTO v_s6 FROM shifts ORDER BY start_time ASC LIMIT 1 OFFSET 5;

    -- Cashier (raised_by)
    SELECT id INTO v_cashier FROM users WHERE username = 'emp003';

    -- Products
    SELECT id INTO v_petrol FROM product WHERE name = 'Petrol';
    SELECT id INTO v_diesel FROM product WHERE name = 'Diesel';
    SELECT id INTO v_xp     FROM product WHERE name = 'Xtra Premium';

    -- Nozzles
    SELECT id INTO v_n1  FROM nozzle WHERE nozzle_name = 'N-1';
    SELECT id INTO v_n7  FROM nozzle WHERE nozzle_name = 'N-7';
    SELECT id INTO v_n9  FROM nozzle WHERE nozzle_name = 'N-9';
    SELECT id INTO v_n11 FROM nozzle WHERE nozzle_name = 'N-11';

    -- Customer IDs
    SELECT id INTO v_smt     FROM users WHERE username = 'smt_transport';
    SELECT id INTO v_vel     FROM users WHERE username = 'vel_logistics';
    SELECT id INTO v_tnstc   FROM users WHERE username = 'tnstc_fleet';
    SELECT id INTO v_infosys FROM users WHERE username = 'infosys_chn';
    SELECT id INTO v_raj     FROM users WHERE username = 'rajkumar_k';
    SELECT id INTO v_sundaram FROM users WHERE username = 'sundaram_mtrs';

    -- Vehicle IDs
    SELECT id INTO v_truck1 FROM vehicle WHERE vehicle_number = 'TN 01 AB 1234';
    SELECT id INTO v_truck3 FROM vehicle WHERE vehicle_number = 'TN 01 EF 9012';
    SELECT id INTO v_bus1   FROM vehicle WHERE vehicle_number = 'TN 09 N 1234';
    SELECT id INTO v_car1   FROM vehicle WHERE vehicle_number = 'TN 10 AK 4455';
    SELECT id INTO v_car3   FROM vehicle WHERE vehicle_number = 'TN 01 CK 8899';
    SELECT id INTO v_jeep   FROM vehicle WHERE vehicle_number = 'TN 22 DE 1122';

    -- === CASH INVOICES ===

    -- Invoice 1: Walk-in cash petrol (Shift 1)
    INSERT INTO invoice_bill (scid, shift_id, bill_date, bill_no, bill_type, payment_mode, gross_amount, total_discount, net_amount, bill_status, payment_status, raised_by_id, created_at, updated_at)
    VALUES (1, v_s1, (CURRENT_DATE - 3) + TIME '07:30', 'C' || v_fy || '/1', 'CASH', 'CASH', 537.50, 0, 537.50, 'PAID', 'PAID', v_cashier, NOW(), NOW())
    RETURNING id INTO v_inv;
    INSERT INTO invoice_product (scid, shift_id, invoice_bill_id, product_id, nozzle_id, quantity, unit_price, gross_amount, discount_rate, discount_amount, amount, created_at, updated_at)
    VALUES (1, v_s1, v_inv, v_petrol, v_n1, 5.0000, 107.5000, 537.5000, 0, 0, 537.5000, NOW(), NOW());

    -- Invoice 2: Walk-in cash diesel (Shift 1)
    INSERT INTO invoice_bill (scid, shift_id, bill_date, bill_no, bill_type, payment_mode, gross_amount, total_discount, net_amount, bill_status, payment_status, raised_by_id, created_at, updated_at)
    VALUES (1, v_s1, (CURRENT_DATE - 3) + TIME '09:15', 'C' || v_fy || '/2', 'CASH', 'UPI', 4660.00, 0, 4660.00, 'PAID', 'PAID', v_cashier, NOW(), NOW())
    RETURNING id INTO v_inv;
    INSERT INTO invoice_product (scid, shift_id, invoice_bill_id, product_id, nozzle_id, quantity, unit_price, gross_amount, discount_rate, discount_amount, amount, created_at, updated_at)
    VALUES (1, v_s1, v_inv, v_diesel, v_n9, 50.0000, 93.2000, 4660.0000, 0, 0, 4660.0000, NOW(), NOW());

    -- Invoice 3: Raj Kumar cash petrol (Shift 2)
    INSERT INTO invoice_bill (scid, shift_id, bill_date, bill_no, bill_type, payment_mode, gross_amount, total_discount, net_amount, bill_status, payment_status, raised_by_id, customer_id, vehicle_id, created_at, updated_at)
    VALUES (1, v_s2, (CURRENT_DATE - 3) + TIME '15:45', 'C' || v_fy || '/3', 'CASH', 'CARD', 3225.00, 0, 3225.00, 'PAID', 'PAID', v_cashier, v_raj, v_car3, NOW(), NOW())
    RETURNING id INTO v_inv;
    INSERT INTO invoice_product (scid, shift_id, invoice_bill_id, product_id, nozzle_id, quantity, unit_price, gross_amount, discount_rate, discount_amount, amount, created_at, updated_at)
    VALUES (1, v_s2, v_inv, v_petrol, v_n1, 30.0000, 107.5000, 3225.0000, 0, 0, 3225.0000, NOW(), NOW());

    -- Invoice 4: Walk-in XP cash (Shift 3)
    INSERT INTO invoice_bill (scid, shift_id, bill_date, bill_no, bill_type, payment_mode, gross_amount, total_discount, net_amount, bill_status, payment_status, raised_by_id, created_at, updated_at)
    VALUES (1, v_s3, (CURRENT_DATE - 2) + TIME '08:00', 'C' || v_fy || '/4', 'CASH', 'CASH', 2240.00, 0, 2240.00, 'PAID', 'PAID', v_cashier, NOW(), NOW())
    RETURNING id INTO v_inv;
    INSERT INTO invoice_product (scid, shift_id, invoice_bill_id, product_id, nozzle_id, quantity, unit_price, gross_amount, discount_rate, discount_amount, amount, created_at, updated_at)
    VALUES (1, v_s3, v_inv, v_xp, v_n7, 20.0000, 112.0000, 2240.0000, 0, 0, 2240.0000, NOW(), NOW());

    -- Invoice 5: Infosys car petrol cash (Shift 4)
    INSERT INTO invoice_bill (scid, shift_id, bill_date, bill_no, bill_type, payment_mode, gross_amount, total_discount, net_amount, bill_status, payment_status, raised_by_id, customer_id, vehicle_id, created_at, updated_at)
    VALUES (1, v_s4, (CURRENT_DATE - 2) + TIME '16:30', 'C' || v_fy || '/5', 'CASH', 'UPI', 2687.50, 18.75, 2668.75, 'PAID', 'PAID', v_cashier, v_infosys, v_car1, NOW(), NOW())
    RETURNING id INTO v_inv;
    INSERT INTO invoice_product (scid, shift_id, invoice_bill_id, product_id, nozzle_id, quantity, unit_price, gross_amount, discount_rate, discount_amount, amount, created_at, updated_at)
    VALUES (1, v_s4, v_inv, v_petrol, v_n1, 25.0000, 107.5000, 2687.5000, 0.7500, 18.7500, 2668.7500, NOW(), NOW());

    -- Invoice 6: Walk-in diesel cash (Shift 5)
    INSERT INTO invoice_bill (scid, shift_id, bill_date, bill_no, bill_type, payment_mode, gross_amount, total_discount, net_amount, bill_status, payment_status, raised_by_id, created_at, updated_at)
    VALUES (1, v_s5, (CURRENT_DATE - 1) + TIME '10:00', 'C' || v_fy || '/6', 'CASH', 'CASH', 9320.00, 0, 9320.00, 'PAID', 'PAID', v_cashier, NOW(), NOW())
    RETURNING id INTO v_inv;
    INSERT INTO invoice_product (scid, shift_id, invoice_bill_id, product_id, nozzle_id, quantity, unit_price, gross_amount, discount_rate, discount_amount, amount, created_at, updated_at)
    VALUES (1, v_s5, v_inv, v_diesel, v_n9, 100.0000, 93.2000, 9320.0000, 0, 0, 9320.0000, NOW(), NOW());

    -- === CREDIT INVOICES ===

    -- Invoice 7: Sri Murugan Transport - Diesel credit (Shift 1)
    INSERT INTO invoice_bill (scid, shift_id, bill_date, bill_no, bill_type, gross_amount, total_discount, net_amount, bill_status, payment_status, raised_by_id, customer_id, vehicle_id, driver_name, driver_phone, indent_no, created_at, updated_at)
    VALUES (1, v_s1, (CURRENT_DATE - 3) + TIME '10:30', 'A' || v_fy || '/1', 'CREDIT', 18640.00, 300.00, 18340.00, 'PAID', 'NOT_PAID', v_cashier, v_smt, v_truck1, 'Ravi', '9876543210', 'IND-001', NOW(), NOW())
    RETURNING id INTO v_inv;
    INSERT INTO invoice_product (scid, shift_id, invoice_bill_id, product_id, nozzle_id, quantity, unit_price, gross_amount, discount_rate, discount_amount, amount, created_at, updated_at)
    VALUES (1, v_s1, v_inv, v_diesel, v_n11, 200.0000, 93.2000, 18640.0000, 1.5000, 300.0000, 18340.0000, NOW(), NOW());

    -- Invoice 8: Vel Logistics - Diesel credit (Shift 2)
    INSERT INTO invoice_bill (scid, shift_id, bill_date, bill_no, bill_type, gross_amount, total_discount, net_amount, bill_status, payment_status, raised_by_id, customer_id, vehicle_id, driver_name, driver_phone, created_at, updated_at)
    VALUES (1, v_s2, (CURRENT_DATE - 3) + TIME '17:00', 'A' || v_fy || '/2', 'CREDIT', 13980.00, 187.50, 13792.50, 'PAID', 'NOT_PAID', v_cashier, v_vel, v_truck3, 'Senthil', '9876543211', NOW(), NOW())
    RETURNING id INTO v_inv;
    INSERT INTO invoice_product (scid, shift_id, invoice_bill_id, product_id, nozzle_id, quantity, unit_price, gross_amount, discount_rate, discount_amount, amount, created_at, updated_at)
    VALUES (1, v_s2, v_inv, v_diesel, v_n9, 150.0000, 93.2000, 13980.0000, 1.2500, 187.5000, 13792.5000, NOW(), NOW());

    -- Invoice 9: TNSTC Bus - Diesel credit (Shift 3)
    INSERT INTO invoice_bill (scid, shift_id, bill_date, bill_no, bill_type, gross_amount, total_discount, net_amount, bill_status, payment_status, raised_by_id, customer_id, vehicle_id, driver_name, created_at, updated_at)
    VALUES (1, v_s3, (CURRENT_DATE - 2) + TIME '07:00', 'A' || v_fy || '/3', 'CREDIT', 27944.00, 600.00, 27344.00, 'PAID', 'NOT_PAID', v_cashier, v_tnstc, v_bus1, 'Kumar', NOW(), NOW())
    RETURNING id INTO v_inv;
    INSERT INTO invoice_product (scid, shift_id, invoice_bill_id, product_id, nozzle_id, quantity, unit_price, gross_amount, discount_rate, discount_amount, amount, created_at, updated_at)
    VALUES (1, v_s3, v_inv, v_diesel, v_n11, 300.0000, 93.2000, 27944.0000, 2.0000, 600.0000, 27344.0000, NOW(), NOW());

    -- Invoice 10: Sundaram Motors - Diesel credit (Shift 5)
    INSERT INTO invoice_bill (scid, shift_id, bill_date, bill_no, bill_type, gross_amount, total_discount, net_amount, bill_status, payment_status, raised_by_id, customer_id, vehicle_id, created_at, updated_at)
    VALUES (1, v_s5, (CURRENT_DATE - 1) + TIME '11:30', 'A' || v_fy || '/4', 'CREDIT', 9320.00, 100.00, 9220.00, 'PAID', 'NOT_PAID', v_cashier, v_sundaram, v_jeep, NOW(), NOW())
    RETURNING id INTO v_inv;
    INSERT INTO invoice_product (scid, shift_id, invoice_bill_id, product_id, nozzle_id, quantity, unit_price, gross_amount, discount_rate, discount_amount, amount, created_at, updated_at)
    VALUES (1, v_s5, v_inv, v_diesel, v_n9, 100.0000, 93.2000, 9320.0000, 1.0000, 100.0000, 9220.0000, NOW(), NOW());

    RAISE NOTICE 'Seeded 10 invoices (6 cash, 4 credit) with products';
END IF;
END $$;;

-- Statements (1 statement for Sri Murugan Transport covering their credit bills)
DO $$
DECLARE
    v_smt BIGINT;
    v_stmt BIGINT;
    v_fy TEXT;
BEGIN
IF (SELECT COUNT(*) FROM statement) = 0 THEN

    v_fy := CASE WHEN EXTRACT(MONTH FROM CURRENT_DATE) >= 4
                 THEN LPAD((EXTRACT(YEAR FROM CURRENT_DATE) % 100)::TEXT, 2, '0')
                 ELSE LPAD(((EXTRACT(YEAR FROM CURRENT_DATE) - 1) % 100)::TEXT, 2, '0') END;

    SELECT id INTO v_smt FROM users WHERE username = 'smt_transport';

    INSERT INTO statement (scid, statement_no, customer_id, from_date, to_date, statement_date, number_of_bills, total_amount, rounding_amount, net_amount, received_amount, balance_amount, status, created_at, updated_at)
    VALUES (1, 'S' || v_fy || '/1', v_smt, CURRENT_DATE - 3, CURRENT_DATE - 1, CURRENT_DATE, 1, 18340.00, 0, 18340.00, 0, 18340.00, 'NOT_PAID', NOW(), NOW())
    RETURNING id INTO v_stmt;

    -- Link the SMT credit invoice to this statement
    UPDATE invoice_bill SET statement_id = v_stmt
    WHERE bill_no = 'A' || v_fy || '/1';

    RAISE NOTICE 'Seeded 1 statement for Sri Murugan Transport';
END IF;
END $$;;

-- Payments (3 payments: 1 for statement, 1 for local credit bill, 1 partial)
DO $$
DECLARE
    v_smt BIGINT; v_vel BIGINT; v_tnstc BIGINT;
    v_stmt_id BIGINT;
    v_vel_inv BIGINT;
    v_cash_mode BIGINT; v_neft_mode BIGINT; v_cheque_mode BIGINT;
    v_fy TEXT;
BEGIN
IF (SELECT COUNT(*) FROM payment) = 0 THEN

    v_fy := CASE WHEN EXTRACT(MONTH FROM CURRENT_DATE) >= 4
                 THEN LPAD((EXTRACT(YEAR FROM CURRENT_DATE) % 100)::TEXT, 2, '0')
                 ELSE LPAD(((EXTRACT(YEAR FROM CURRENT_DATE) - 1) % 100)::TEXT, 2, '0') END;

    SELECT id INTO v_smt   FROM users WHERE username = 'smt_transport';
    SELECT id INTO v_vel   FROM users WHERE username = 'vel_logistics';
    SELECT id INTO v_tnstc FROM users WHERE username = 'tnstc_fleet';

    SELECT id INTO v_cash_mode   FROM payment_mode WHERE mode_name = 'CASH';
    SELECT id INTO v_neft_mode   FROM payment_mode WHERE mode_name = 'NEFT';
    SELECT id INTO v_cheque_mode FROM payment_mode WHERE mode_name = 'CHEQUE';

    SELECT id INTO v_stmt_id FROM statement WHERE statement_no = 'S' || v_fy || '/1';
    SELECT id INTO v_vel_inv FROM invoice_bill WHERE bill_no = 'A' || v_fy || '/2';

    -- Payment 1: SMT pays 10000 against statement (partial)
    INSERT INTO payment (scid, payment_date, amount, payment_mode_id, reference_no, customer_id, statement_id, remarks, created_at, updated_at)
    VALUES (1, (CURRENT_DATE)::TIMESTAMP + TIME '10:00', 10000.0000, v_neft_mode, 'UTR20260320001', v_smt, v_stmt_id, 'Partial payment for March statement', NOW(), NOW());

    -- Update statement received/balance
    UPDATE statement SET received_amount = 10000.00, balance_amount = 8340.00 WHERE id = v_stmt_id;

    -- Payment 2: Vel Logistics pays full against invoice
    INSERT INTO payment (scid, payment_date, amount, payment_mode_id, reference_no, customer_id, invoice_bill_id, remarks, created_at, updated_at)
    VALUES (1, (CURRENT_DATE - 1)::TIMESTAMP + TIME '14:00', 13792.5000, v_cheque_mode, 'CHQ-445566', v_vel, v_vel_inv, 'Full payment by cheque', NOW(), NOW());

    -- Mark Vel invoice as paid
    UPDATE invoice_bill SET payment_status = 'PAID' WHERE id = v_vel_inv;

    -- Payment 3: TNSTC partial payment (cash)
    INSERT INTO payment (scid, payment_date, amount, payment_mode_id, customer_id, remarks, created_at, updated_at)
    VALUES (1, (CURRENT_DATE)::TIMESTAMP + TIME '11:30', 15000.0000, v_cash_mode, v_tnstc, 'Partial advance payment', NOW(), NOW());

    RAISE NOTICE 'Seeded 3 payments';
END IF;
END $$;;

-- Employee Advances (3 advances)
DO $$
DECLARE
    v_murugan BIGINT; v_lakshmi BIGINT;
BEGIN
IF (SELECT COUNT(*) FROM employee_advances) = 0 THEN
    SELECT e.id INTO v_murugan FROM employees e JOIN users u ON e.id = u.id WHERE u.username = 'emp001';
    SELECT e.id INTO v_lakshmi FROM employees e JOIN users u ON e.id = u.id WHERE u.username = 'emp002';

    INSERT INTO employee_advances (employee_id, amount, advance_date, advance_type, remarks, status, created_at, updated_at) VALUES
        (v_murugan, 5000, CURRENT_DATE - 10, 'SALARY_ADVANCE', 'For medical expenses', 'PENDING', NOW(), NOW()),
        (v_murugan, 2000, CURRENT_DATE - 3, 'NIGHT_ADVANCE',   'Night shift food',     'DEDUCTED', NOW(), NOW()),
        (v_lakshmi, 3000, CURRENT_DATE - 5, 'SALARY_ADVANCE',  'Personal requirement',  'PENDING', NOW(), NOW());
    RAISE NOTICE 'Seeded 3 employee advances';
END IF;
END $$;;

-- Cash Advances (2 cash advances during shifts)
DO $$
DECLARE
    v_murugan BIGINT;
    v_s2 BIGINT;
BEGIN
IF (SELECT COUNT(*) FROM cash_advances) = 0 THEN
    SELECT id INTO v_murugan FROM users WHERE username = 'emp001';
    SELECT id INTO v_s2 FROM shifts ORDER BY start_time ASC LIMIT 1 OFFSET 1;

    INSERT INTO cash_advances (scid, shift_id, advance_date, amount, advance_type, recipient_name, recipient_phone, purpose, remarks, status, returned_amount, employee_id, utilized_amount, created_at, updated_at) VALUES
        (1, v_s2, (CURRENT_DATE - 3)::TIMESTAMP + TIME '15:00', 2000.0000, 'CASH_ADVANCE', 'Ravi (Driver)', '9876543210', 'Fuel advance for trip', 'SMT driver advance', 'RETURNED', 2000.0000, v_murugan, 0, NOW(), NOW()),
        (1, v_s2, (CURRENT_DATE - 3)::TIMESTAMP + TIME '18:00', 5000.0000, 'CASH_ADVANCE', 'Senthil', '9876543211', 'Vehicle repair', 'Emergency repair advance', 'GIVEN', 0, v_murugan, 0, NOW(), NOW());
    RAISE NOTICE 'Seeded 2 cash advances';
END IF;
END $$;;

-- Attendance (past 5 days for all 4 employees)
DO $$
DECLARE
    v_emp RECORD;
    v_day INTEGER;
    v_status TEXT;
BEGIN
IF (SELECT COUNT(*) FROM attendance) = 0 THEN
    FOR v_emp IN SELECT e.id, u.username FROM employees e JOIN users u ON e.id = u.id LOOP
        FOR v_day IN 1..5 LOOP
            -- Mostly present, with some variation
            IF v_emp.username = 'emp002' AND v_day = 3 THEN
                v_status := 'ON_LEAVE';
            ELSIF v_emp.username = 'emp001' AND v_day = 5 THEN
                v_status := 'HALF_DAY';
            ELSE
                v_status := 'PRESENT';
            END IF;

            INSERT INTO attendance (scid, employee_id, date, check_in_time, check_out_time, total_hours_worked, status, source, created_at, updated_at)
            VALUES (1, v_emp.id, CURRENT_DATE - v_day,
                    CASE WHEN v_status = 'ON_LEAVE' THEN NULL ELSE TIME '06:00' END,
                    CASE WHEN v_status = 'ON_LEAVE' THEN NULL
                         WHEN v_status = 'HALF_DAY' THEN TIME '12:00'
                         ELSE TIME '18:00' END,
                    CASE WHEN v_status = 'ON_LEAVE' THEN 0
                         WHEN v_status = 'HALF_DAY' THEN 6
                         ELSE 12 END,
                    v_status, 'MANUAL', NOW(), NOW());
        END LOOP;
    END LOOP;
    RAISE NOTICE 'Seeded attendance for 4 employees x 5 days';
END IF;
END $$;;

-- Leave Balance (current year for all employees)
DO $$
DECLARE
    v_emp RECORD;
    v_lt RECORD;
    v_year INTEGER := EXTRACT(YEAR FROM CURRENT_DATE)::INTEGER;
BEGIN
IF (SELECT COUNT(*) FROM leave_balances) = 0 THEN
    FOR v_emp IN SELECT e.id FROM employees e LOOP
        FOR v_lt IN SELECT id, max_days_per_year FROM leave_types LOOP
            INSERT INTO leave_balances (scid, employee_id, leave_type_id, year, total_allotted, used, remaining, created_at, updated_at)
            VALUES (1, v_emp.id, v_lt.id, v_year, v_lt.max_days_per_year,
                    CASE WHEN v_lt.max_days_per_year = 12 THEN 2 -- casual: used 2
                         WHEN v_lt.max_days_per_year = 10 THEN 1 -- sick: used 1
                         ELSE 0 END,
                    CASE WHEN v_lt.max_days_per_year = 12 THEN 10
                         WHEN v_lt.max_days_per_year = 10 THEN 9
                         ELSE 15 END,
                    NOW(), NOW());
        END LOOP;
    END LOOP;
    RAISE NOTICE 'Seeded leave balances for all employees';
END IF;
END $$;;

-- Station Expenses (4 recent expenses)
DO $$
BEGIN
IF (SELECT COUNT(*) FROM station_expenses) = 0 THEN
    INSERT INTO station_expenses (scid, expense_type_id, amount, expense_date, description, paid_to, payment_mode, recurring_type, created_at, updated_at) VALUES
        (1, (SELECT id FROM expense_type WHERE type_name = 'Electricity'), 12500, CURRENT_DATE - 5, 'March electricity bill', 'TNEB', 'NEFT', 'MONTHLY', NOW(), NOW()),
        (1, (SELECT id FROM expense_type WHERE type_name = 'Maintenance'), 3500, CURRENT_DATE - 2, 'Pump-2 motor servicing', 'Sri Balaji Engineers', 'CASH', 'ONE_TIME', NOW(), NOW()),
        (1, (SELECT id FROM expense_type WHERE type_name = 'Water'), 800, CURRENT_DATE - 4, 'Monthly water bill', 'Chennai Metro Water', 'UPI', 'MONTHLY', NOW(), NOW()),
        (1, (SELECT id FROM expense_type WHERE type_name = 'Miscellaneous'), 1200, CURRENT_DATE - 1, 'Stationery and printing', 'Lakshmi Stores', 'CASH', 'ONE_TIME', NOW(), NOW());
    RAISE NOTICE 'Seeded 4 station expenses';
END IF;
END $$;;

-- Utility Bills (1 electricity + 1 water)
DO $$
BEGIN
IF (SELECT COUNT(*) FROM utility_bills) = 0 THEN
    INSERT INTO utility_bills (scid, bill_type, provider, consumer_number, bill_date, due_date, bill_amount, paid_amount, status, units_consumed, bill_period, remarks, created_at, updated_at) VALUES
        (1, 'ELECTRICITY', 'TNEB', 'TN-042-1234567', CURRENT_DATE - 10, CURRENT_DATE + 5, 12500, 12500, 'PAID', 1850, 'Feb 2026', 'Paid via NEFT', NOW(), NOW()),
        (1, 'WATER', 'Chennai Metro Water', 'CMW-98765', CURRENT_DATE - 8, CURRENT_DATE + 10, 800, 0, 'PENDING', NULL, 'Feb 2026', NULL, NOW(), NOW());
    RAISE NOTICE 'Seeded 2 utility bills';
END IF;
END $$;;

-- External Cash Inflow (owner brought cash for operations)
DO $$
BEGIN
IF (SELECT COUNT(*) FROM external_cash_inflows) = 0 THEN
    INSERT INTO external_cash_inflows (scid, amount, inflow_date, source, purpose, remarks, status, repaid_amount, created_at, updated_at) VALUES
        (1, 50000.0000, (CURRENT_DATE - 5)::TIMESTAMP + TIME '09:00', 'Owner', 'Working capital', 'Monthly cash infusion for operations', 'PARTIALLY_REPAID', 20000.0000, NOW(), NOW());
    RAISE NOTICE 'Seeded 1 external cash inflow';
END IF;
END $$;;

-- Cash Inflow Repayment
DO $$
DECLARE
    v_inflow BIGINT;
BEGIN
IF (SELECT COUNT(*) FROM cash_inflow_repayments) = 0 THEN
    SELECT id INTO v_inflow FROM external_cash_inflows LIMIT 1;
    INSERT INTO cash_inflow_repayments (scid, cash_inflow_id, amount, repayment_date, remarks, created_at, updated_at) VALUES
        (1, v_inflow, 20000.0000, (CURRENT_DATE - 2)::TIMESTAMP + TIME '17:00', 'Partial repayment from collections', NOW(), NOW());
    RAISE NOTICE 'Seeded 1 cash inflow repayment';
END IF;
END $$;;

-- Stock Transfers (2 transfers from godown to cashier)
DO $$
BEGIN
IF (SELECT COUNT(*) FROM stock_transfer) = 0 THEN
    INSERT INTO stock_transfer (scid, product_id, quantity, from_location, to_location, transfer_date, remarks, transferred_by, created_at, updated_at) VALUES
        (1, (SELECT id FROM product WHERE name = 'Servo 4T 20W-40 (1L)'), 6, 'GODOWN', 'CASHIER', (CURRENT_DATE - 2)::TIMESTAMP + TIME '08:00', 'Restocking counter', 'Murugan S', NOW(), NOW()),
        (1, (SELECT id FROM product WHERE name = 'Air Freshener'), 10, 'GODOWN', 'CASHIER', (CURRENT_DATE - 1)::TIMESTAMP + TIME '08:30', 'Restocking counter', 'Lakshmi R', NOW(), NOW());
    RAISE NOTICE 'Seeded 2 stock transfers';
END IF;
END $$;;

-- Backfill fuel_family for existing fuel products
UPDATE product SET fuel_family = 'PETROL' WHERE LOWER(name) IN ('petrol', 'xtra premium') AND fuel_family IS NULL;
UPDATE product SET fuel_family = 'DIESEL' WHERE LOWER(name) IN ('diesel', 'xtra mile') AND fuel_family IS NULL;
