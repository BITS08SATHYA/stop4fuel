-- =========================================
-- StopForFuel Comprehensive Seed Data
-- Runs on every application boot (idempotent)
-- Separator: ;; (configured in application.yml)
-- =========================================

-- =====================
-- PHASE 0: Core Reference Data
-- =====================

-- Payment Modes (unique constraint on mode_name)
INSERT INTO payment_mode (mode_name) VALUES ('CASH') ON CONFLICT (mode_name) DO NOTHING;;
INSERT INTO payment_mode (mode_name) VALUES ('CHEQUE') ON CONFLICT (mode_name) DO NOTHING;;
INSERT INTO payment_mode (mode_name) VALUES ('UPI') ON CONFLICT (mode_name) DO NOTHING;;
INSERT INTO payment_mode (mode_name) VALUES ('NEFT') ON CONFLICT (mode_name) DO NOTHING;;
INSERT INTO payment_mode (mode_name) VALUES ('CARD') ON CONFLICT (mode_name) DO NOTHING;;
INSERT INTO payment_mode (mode_name) VALUES ('BANK') ON CONFLICT (mode_name) DO NOTHING;;
INSERT INTO payment_mode (mode_name) VALUES ('CCMS') ON CONFLICT (mode_name) DO NOTHING;;

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
    INSERT INTO product (scid, name, hsn_code, price, category, unit, brand, active, created_at, updated_at) VALUES
        (1, 'Petrol', '27101211', 107.50, 'FUEL', 'LITERS', 'IOCL', true, NOW(), NOW()),
        (1, 'Diesel', '27101990', 93.20, 'FUEL', 'LITERS', 'IOCL', true, NOW(), NOW()),
        (1, 'Xtra Premium', '27101210', 112.00, 'FUEL', 'LITERS', 'IOCL', true, NOW(), NOW());
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

-- Employees
DO $$
BEGIN
IF (SELECT COUNT(*) FROM employees) = 0 THEN
    INSERT INTO employees (scid, name, designation, department, employee_code, phone, email, salary, join_date, status,
                           address, city, state, pincode, bank_name, bank_branch, gender, blood_group, aadhar_number,
                           created_at, updated_at) VALUES
        (1, 'Murugan S',  'Attendant', 'Operations',  'EMP001', '9840011111', 'murugan@stopforfuel.com',  18000, '2024-06-15', 'Active',
         '12 Anna Nagar, Chennai',  'Chennai', 'Tamil Nadu', '600040', 'Indian Bank', 'Anna Nagar',  'Male',   'O+',  '123456789012', NOW(), NOW()),
        (1, 'Lakshmi R',  'Attendant', 'Operations',  'EMP002', '9840022222', 'lakshmi@stopforfuel.com',  18000, '2024-08-01', 'Active',
         '34 T Nagar, Chennai',     'Chennai', 'Tamil Nadu', '600017', 'Indian Bank', 'T Nagar',     'Female', 'B+',  '234567890123', NOW(), NOW()),
        (1, 'Karthik V',  'Cashier',   'Accounts',    'EMP003', '9840033333', 'karthik@stopforfuel.com',  22000, '2024-03-10', 'Active',
         '56 Adyar, Chennai',       'Chennai', 'Tamil Nadu', '600020', 'Indian Bank', 'Adyar',       'Male',   'A+',  '345678901234', NOW(), NOW()),
        (1, 'Priya M',    'Manager',   'Management',  'EMP004', '9840044444', 'priya@stopforfuel.com',    35000, '2023-11-01', 'Active',
         '78 Velachery, Chennai',   'Chennai', 'Tamil Nadu', '600042', 'Indian Bank', 'Velachery',   'Female', 'AB+', '456789012345', NOW(), NOW());
END IF;
END $$;;


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
