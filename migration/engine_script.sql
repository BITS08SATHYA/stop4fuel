-- ============================================================
-- MIGRATION ENGINE SCRIPT (pgloader-compatible, lowercase columns)
-- ============================================================
-- Usage:  docker exec -i <container> psql -U postgres -d stopforfuel < migration/engine_script.sql
--
-- Prerequisites:
--   1. Public schema tables exist (from schema-only dump)
--   2. Raw MySQL tables imported into 'raw' schema via pgloader
--
-- This script handles EVERYTHING: master data + transactional data.
-- IDEMPOTENT — safe to re-run when new data arrives.
-- ============================================================

\echo '=============================='
\echo 'MIGRATION ENGINE — START'
\echo '=============================='

-- ============================================================
-- PRE-FLIGHT: Verify raw schema and required tables exist
-- ============================================================
\echo 'PRE-FLIGHT: Checking raw schema...'

DO $$
DECLARE
    missing_tables TEXT[] := '{}';
    required_tables TEXT[] := ARRAY[
        -- Master data
        'customergroups', 'vehicle_type', 'customer_data', 'vehicle_new', 'employee',
        -- Products & pricing
        'gst_oil_prices', 'productprice',
        -- Invoices
        'creditbill_test', 'creditbill_products_test', 'cashbill', 'cashbill_products',
        -- Statements & payments
        'credit_statement', 'statement_association_1',
        'incomebill', 'intermediatebills',
        'income_creditbill_statement', 'intermediate_creditbill_statement',
        -- Payment details (for enrichment)
        'cheque_incomebill', 'card_incomebill', 'ccms_incomebill', 'bank_transfer_incomebill',
        'card_incomebill_statement', 'ccms_incomebill_statement', 'cheque_incomebill_statement',
        -- Advances & shifts
        'card_advances', 'cheque_advance', 'ccms_advance', 'bank_transfer_advance',
        'home_advances', 'cash_advances',
        'shift_closing_timings',
        -- Expenses
        'expense', 'expensetype',
        -- Incentives
        'incentive_table',
        -- Tank inventory (tank-wise)
        'e_book_tw_ms', 'e_book_tw_xp', 'e_book_tw_hsd_1',
        -- Nozzle inventory (meter-wise)
        'e_book_mw_ms', 'e_book_mw_xp', 'e_book_mw_hsd_1', 'e_book_mw_hsd_2'
    ];
    tbl TEXT;
BEGIN
    -- Check raw schema exists
    IF NOT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'raw') THEN
        RAISE EXCEPTION 'FATAL: "raw" schema does not exist. Run pgloader first.';
    END IF;

    -- Check each required table
    FOREACH tbl IN ARRAY required_tables LOOP
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.tables
            WHERE table_schema = 'raw' AND table_name = tbl
        ) THEN
            missing_tables := array_append(missing_tables, tbl);
        END IF;
    END LOOP;

    IF array_length(missing_tables, 1) > 0 THEN
        RAISE EXCEPTION 'FATAL: Missing raw tables: %. Run pgloader with full MySQL import.', array_to_string(missing_tables, ', ');
    END IF;

    RAISE NOTICE 'PRE-FLIGHT PASSED: raw schema OK, all % tables found.', array_length(required_tables, 1);
END $$;

-- ============================================================
-- M1: Roles, Party, Customer Groups
-- ============================================================
\echo 'M1: Roles, Party, Groups...'

INSERT INTO roles (role_type) VALUES ('CUSTOMER'),('EMPLOYEE'),('ADMIN'),('CASHIER'),('DEALER')
ON CONFLICT DO NOTHING;

INSERT INTO party (party_type) VALUES ('Local'),('Statement')
ON CONFLICT DO NOTHING;

TRUNCATE customer_group CASCADE;
INSERT INTO customer_group (group_name)
SELECT groupname FROM raw.customergroups WHERE groupname IS NOT NULL AND TRIM(groupname) != '';

-- ============================================================
-- M2: Vehicle Types
-- ============================================================
\echo 'M2: Vehicle Types...'

INSERT INTO vehicle_type (type_name, created_at, updated_at)
SELECT DISTINCT mapped, NOW(), NOW() FROM (
    SELECT CASE vehicletype
        WHEN 'Two-Wheeler' THEN 'Bike' WHEN 'Auto' THEN 'Auto' WHEN 'Mini Auto' THEN 'Auto'
        WHEN 'Spare Bus' THEN 'Bus' WHEN 'Line Bus' THEN 'Bus' WHEN 'Mini Bus' THEN 'Bus'
        WHEN 'Lorry' THEN 'Truck' WHEN 'Eicher' THEN 'Truck' WHEN 'Tempo' THEN 'Truck'
        ELSE vehicletype END AS mapped
    FROM raw.vehicle_type WHERE vehicletype != 'Select'
) t WHERE mapped IS NOT NULL
ON CONFLICT DO NOTHING;

-- ============================================================
-- M3: Customers
-- ============================================================
\echo 'M3: Customers...'

DO $$
DECLARE r RECORD; v_id BIGINT; v_pt TEXT; v_person TEXT; v_gid BIGINT; v_cl NUMERIC; v_cnt INT;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM customer;
    IF v_cnt > 0 THEN RAISE NOTICE 'Customers exist (%), skip', v_cnt; RETURN; END IF;
    FOR r IN SELECT * FROM raw.customer_data ORDER BY customer_id LOOP
        IF r.customer_name IS NULL OR TRIM(r.customer_name) = '' THEN CONTINUE; END IF;
        v_pt := COALESCE(TRIM(r.customer_partytype), 'Local');
        IF v_pt NOT IN ('Local','Statement') THEN v_pt := 'Local'; END IF;
        v_person := CASE WHEN v_pt = 'Statement' THEN 'Company' ELSE 'Individual' END;
        v_gid := NULL;
        IF r.customer_group_name IS NOT NULL AND TRIM(r.customer_group_name) != '' THEN
            SELECT id INTO v_gid FROM customer_group WHERE group_name = TRIM(r.customer_group_name) LIMIT 1;
        END IF;
        v_cl := NULL;
        IF r.customer_creditlimit IS NOT NULL AND r.customer_creditlimit > 0 THEN v_cl := r.customer_creditlimit; END IF;
        INSERT INTO person_entity (scid,name,address,person_type,created_at,updated_at)
        VALUES (1,TRIM(r.customer_name),NULLIF(TRIM(COALESCE(r.customer_address,'')),''),v_person,NOW(),NOW()) RETURNING id INTO v_id;
        INSERT INTO users (id,username,role_id,join_date,status)
        VALUES (v_id,'cust_'||r.customer_id,(SELECT id FROM roles WHERE role_type='CUSTOMER'),r.customer_join_date,'ACTIVE');
        INSERT INTO customer (id,group_id,party_id,credit_limit_amount,consumed_liters)
        VALUES (v_id,v_gid,(SELECT id FROM party WHERE party_type=v_pt),v_cl,0);
        IF r.customer_phone IS NOT NULL AND TRIM(r.customer_phone) NOT IN ('','null','NULL') THEN
            INSERT INTO person_phones (person_id,phone_number) VALUES (v_id,TRIM(r.customer_phone));
        END IF;
        IF r.customer_email IS NOT NULL AND TRIM(r.customer_email) LIKE '%@%' THEN
            INSERT INTO person_emails (person_id,email) VALUES (v_id,TRIM(r.customer_email));
        END IF;
    END LOOP;
END $$;

-- SSVTC EMPLOYEE (Senthil Office) — orphan customer for CID 111
DO $$ DECLARE v_id BIGINT; BEGIN
    IF NOT EXISTS (SELECT 1 FROM person_entity WHERE name='SSVTC EMPLOYEE (Senthil Office)') THEN
        INSERT INTO person_entity (scid,name,person_type,created_at,updated_at)
        VALUES (1,'SSVTC EMPLOYEE (Senthil Office)','Company',NOW(),NOW()) RETURNING id INTO v_id;
        INSERT INTO users (id,username,role_id,status)
        VALUES (v_id,'cust_111',(SELECT id FROM roles WHERE role_type='CUSTOMER'),'ACTIVE');
        INSERT INTO customer (id,group_id,party_id,credit_limit_amount,consumed_liters)
        VALUES (v_id,NULL,(SELECT id FROM party WHERE party_type='Local'),NULL,0);
    END IF;
END $$;

-- ============================================================
-- M4: Employees
-- ============================================================
\echo 'M4: Employees...'

INSERT INTO designations (name) VALUES
('Cashier'),('Pump Attender'),('Manager'),('Owner'),('Lorry Driver'),
('Lorry Cleaner'),('Sweeper'),('Watchman'),('Supervisor'),('Attendant'),('Helper')
ON CONFLICT DO NOTHING;

DO $$
DECLARE r RECORD; v_id BIGINT; v_name TEXT; v_desig TEXT; v_role TEXT; v_st TEXT; v_g TEXT; v_a TEXT; v_ph TEXT; v_cnt INT;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM employees;
    IF v_cnt > 0 THEN RAISE NOTICE 'Employees exist (%), skip', v_cnt; RETURN; END IF;
    FOR r IN SELECT * FROM raw.employee ORDER BY employeeid LOOP
        v_name := TRIM(COALESCE(r.employee_name,'')||' '||COALESCE(r.employeelastname,''));
        IF v_name = '' THEN CONTINUE; END IF;
        v_desig := CASE UPPER(TRIM(COALESCE(r.employee_designation,'')))
            WHEN 'PETROL CASHIER' THEN 'Cashier' WHEN 'CASHIER' THEN 'Cashier'
            WHEN 'PUMP BOY' THEN 'Pump Attender' WHEN 'PUMP ATTENDANT' THEN 'Pump Attender'
            WHEN 'ATTENDANT' THEN 'Pump Attender' WHEN 'MANAGER' THEN 'Manager'
            WHEN 'ADMIN' THEN 'Manager' WHEN 'SUPERVISOR' THEN 'Supervisor'
            WHEN 'SECURITY' THEN 'Watchman' WHEN 'HELPER' THEN 'Pump Attender'
            WHEN 'CLEANER' THEN 'Pump Attender' WHEN 'LORRY DRIVER' THEN 'Lorry Driver'
            WHEN 'LORRY CLEANER' THEN 'Lorry Cleaner' WHEN 'SWEEPER' THEN 'Sweeper'
            WHEN 'WATCHMAN' THEN 'Watchman' WHEN 'OWNER' THEN 'Owner'
            ELSE INITCAP(TRIM(r.employee_designation)) END;
        v_role := CASE v_desig WHEN 'Cashier' THEN 'CASHIER' WHEN 'Manager' THEN 'ADMIN'
            WHEN 'Owner' THEN 'ADMIN' WHEN 'Supervisor' THEN 'ADMIN' ELSE 'EMPLOYEE' END;
        v_st := UPPER(TRIM(COALESCE(r.employeestatus,'ACTIVE')));
        IF v_st NOT IN ('ACTIVE','INACTIVE','TERMINATED') THEN v_st := 'ACTIVE'; END IF;
        v_g := UPPER(TRIM(COALESCE(r.employee_gender,'')));
        IF v_g IN ('M','MALE') THEN v_g:='Male'; ELSIF v_g IN ('F','FEMALE') THEN v_g:='Female'; ELSE v_g:=NULL; END IF;
        v_a := REGEXP_REPLACE(COALESCE(TRIM(r.employee_adharcard),''),'[^0-9]','','g');
        IF LENGTH(v_a)!=12 THEN v_a:=NULL; END IF;
        IF v_a IS NOT NULL AND EXISTS(SELECT 1 FROM employees WHERE aadhar_number=v_a) THEN v_a:=NULL; END IF;
        INSERT INTO person_entity (scid,name,address,person_type,created_at,updated_at)
        VALUES (1,v_name,NULLIF(TRIM(COALESCE(r.employee_address,'')),''),'Individual',NOW(),NOW()) RETURNING id INTO v_id;
        INSERT INTO users (id,username,role_id,join_date,status)
        VALUES (v_id,'emp_'||r.employeeid,(SELECT id FROM roles WHERE role_type=v_role LIMIT 1),r.employee_joindate,v_st);
        INSERT INTO employees (id,designation_id,salary,aadhar_number,bank_name,bank_ifsc,bank_account_number,
            gender,date_of_birth,blood_group,employee_code,termination_date)
        VALUES (v_id,(SELECT id FROM designations WHERE name=v_desig LIMIT 1),LEAST(COALESCE(r.employee_salary,0),9999999),v_a,
            NULLIF(TRIM(COALESCE(r.employee_bankname,'')),''),NULLIF(TRIM(COALESCE(r.employee_ifsc,'')),''),
            NULLIF(TRIM(COALESCE(r.employee_bankaccno,'')),''),v_g,r.employee_birthdate,
            NULLIF(TRIM(COALESCE(r.employeebloodgrp,'')),''),'emp'||LPAD(r.employeeid::TEXT,3,'0'),r.employeeterminationdate);
        v_ph := TRIM(COALESCE(r.employee_phone,''));
        IF v_ph != '' AND v_ph != '0' THEN INSERT INTO person_phones (person_id,phone_number) VALUES (v_id,v_ph); END IF;
    END LOOP;
END $$;

-- ============================================================
-- M5: Migration Mapping Tables (auto-built from usernames)
-- ============================================================
\echo 'M5: Mapping tables...'

DROP TABLE IF EXISTS _migration_cust_map;
CREATE TABLE _migration_cust_map (mysql_id INTEGER PRIMARY KEY, pg_id BIGINT NOT NULL);
INSERT INTO _migration_cust_map (mysql_id, pg_id)
SELECT CAST(REPLACE(u.username,'cust_','') AS INTEGER), u.id
FROM users u JOIN customer c ON c.id = u.id WHERE u.username LIKE 'cust_%'
ON CONFLICT DO NOTHING;

DROP TABLE IF EXISTS _migration_emp_map;
CREATE TABLE _migration_emp_map (mysql_id INTEGER PRIMARY KEY, pg_id BIGINT NOT NULL);
INSERT INTO _migration_emp_map (mysql_id, pg_id)
SELECT CAST(REPLACE(u.username,'emp_','') AS INTEGER), u.id
FROM users u JOIN employees e ON e.id = u.id WHERE u.username LIKE 'emp_%'
ON CONFLICT DO NOTHING;

-- ============================================================
-- M6: Pumps
-- ============================================================
\echo 'M6: Pumps...'

INSERT INTO pump (scid,name,active,created_at,updated_at)
SELECT 1,n,true,NOW(),NOW() FROM (VALUES ('DU-11'),('DU-15'),('DU-16')) AS t(n)
WHERE NOT EXISTS (SELECT 1 FROM pump LIMIT 1);

-- ============================================================
-- S0: Product Name Mapping (66 entries)
-- ============================================================
\echo 'S0: Product name mapping...'

DROP TABLE IF EXISTS _product_name_map;
CREATE TABLE _product_name_map (old_name VARCHAR(255) PRIMARY KEY, pg_product_id BIGINT NOT NULL);
INSERT INTO _product_name_map (old_name, pg_product_id) VALUES
('PETROL',1),('DIESEL',2),('XTRA_PREMIUM',3),('XRTA PREMIUM',3),('XTRA PREMIUM',3),
('HSD_2',2),('HSD_3',2),
('2T_LOOSE_OIL',189),('2T LOOSE OIL',189),('2TOIL 1/LTS',189),
('2T_40ML_POUCH',188),('2T 40 ML POUCH',188),
('2T_1BY2_LITER',186),('2T OIL 1/2 LTS',186),
('2T_1_LITER',185),('2T 1 LITER',185),
('4T_1_LITER',190),('4 T OIL 1 LTS',190),
('4TOIL_ZOOM',193),('4TOIL_20W_50W',191),
('4TOIL_JOSH_10W_30W',192),('4T_SCOOTOMATIC_10W-30W',212),
('15_40_1_LITER',179),('15 W/40 1LTS',179),
('15_40_5_LITER',180),('15/40 5 LITER',180),('15W/40 5LTS',180),
('PRIDE ALT PLUS 15W- 40W 15 LITER',208),
('20_40_1BY2_LITER',183),('20_40_1_LITER',182),('20/40 1 LITER',182),
('20_40_5_LITER',184),('20/40 5 LITER',184),
('90_1_LITER',195),('GEAR_OIL_90_1_LITER',195),
('90_5_LITER',196),('90 5 LITER',196),
('140_1_LITER',176),('140_5_LITER',177),
('RR3_GEM_1BY2_KG',210),('RR3_GEM_1_KG',209),('RR3 GEM 1 KG',209),
('SERVO LONG LIFE GREESE 5 KG',211),
('DISTEL_WATER',205),('D/ WATER',205),('ACID',197),('ADON_PETROL',200),('ADON_DIESEL',199),
('KOOL_PLUS_1BY2_LITER',207),('KOOL PLUS 1/2 L',207),
('KOOL_PLUS_1_LITER',206),('KOOL PLUS 1 L',206),
('BREAK_OIL_1BY2_LITER',202),('BREAK OIL 1/2 L',202),('BRAKE FLUID DOT 4 - 1/4 LITER',202),
('YELLOW_CLOTH',216),('YELLOW CLOTH',216),('BLUE_CLOTH',201),('BLUE CLOTH',201),
('SCOOTOMATIC_800ML',212),('SCOOTOMATIC_10W_30W',212),
('TRU4_KRAAFT_TVS_900_ML',215),
('CLEAR_BLUE_20L',204),('CLEAR_BLUE_10L',203),
('TATA_GENUINE_20L',214),('TATA_GENUINE_20LIT',214);

-- ============================================================
-- S1: Seed Products (52 rows)
-- ============================================================
\echo 'S1: Products...'

INSERT INTO product (id,scid,name,hsn_code,price,category,unit,brand,active,created_at,updated_at) VALUES
(1,1,'Petrol','27101211',101.48,'FUEL','LITERS','IOCL',true,NOW(),NOW()),
(2,1,'Diesel','27101990',93.08,'FUEL','LITERS','IOCL',true,NOW(),NOW()),
(3,1,'Xtra Premium','27101210',107.76,'FUEL','LITERS','IOCL',true,NOW(),NOW()),
(4,1,'Servo 4T 20W-40 (1L)','154546',280,'LUBRICANT','PIECES','Servo',true,NOW(),NOW()),
(5,1,'Servo 4T 20W-40 (500ml)','2712',155,'LUBRICANT','PIECES','Servo',true,NOW(),NOW()),
(6,1,'Castrol Activ 4T 20W-40 (1L)',NULL,319,'LUBRICANT','PIECES','Castrol',true,NOW(),NOW()),
(7,1,'Castrol Activ 4T 20W-50 (1L)',NULL,339,'LUBRICANT','PIECES','Castrol',true,NOW(),NOW()),
(8,1,'Servo Gear Oil 80W-90 (1L)',NULL,240,'LUBRICANT','PIECES','Servo',true,NOW(),NOW()),
(9,1,'Coolant Green (1L)',NULL,180,'LUBRICANT','PIECES','Servo',true,NOW(),NOW()),
(10,1,'Air Freshener',NULL,120,'ACCESSORY','PIECES','Ambipur',true,NOW(),NOW()),
(11,1,'Tissue Box',NULL,50,'ACCESSORY','PIECES','Generic',true,NOW(),NOW()),
(176,1,'Gear Oil 140 (1L)','27101980',NULL,'LUBRICANT','PIECES','Servo',true,NOW(),NOW()),
(177,1,'Gear Oil 140 (5L)','27101980',NULL,'LUBRICANT','PIECES','Servo',true,NOW(),NOW()),
(178,1,'Pride Alt Plus 15W-40 (10L)','27101980',NULL,'LUBRICANT','PIECES','Servo',true,NOW(),NOW()),
(179,1,'Pride Alt Plus 15W-40 (1L)','27101980',345,'LUBRICANT','PIECES','Servo',true,NOW(),NOW()),
(180,1,'Pride Alt Plus 15W-40 (5L)','27101980',1695,'LUBRICANT','PIECES','Servo',true,NOW(),NOW()),
(181,1,'20W-40 (10L)','27101980',NULL,'LUBRICANT','PIECES','Servo',true,NOW(),NOW()),
(182,1,'20W-40 (1L)','27101980',305,'LUBRICANT','PIECES','Servo',true,NOW(),NOW()),
(183,1,'20W-40 (1/2L)','27101980',139,'LUBRICANT','PIECES','Servo',true,NOW(),NOW()),
(184,1,'20W-40 (5L)','27101980',1450,'LUBRICANT','PIECES','Servo',true,NOW(),NOW()),
(185,1,'2T Oil (1L)','27101980',263,'LUBRICANT','PIECES','Servo',true,NOW(),NOW()),
(186,1,'2T Oil (1/2L)','27101980',160,'LUBRICANT','PIECES','Servo',true,NOW(),NOW()),
(187,1,'2T Oil (20ml Pouch)','27101980',7,'LUBRICANT','PIECES','Servo',true,NOW(),NOW()),
(188,1,'2T Oil (40ml Pouch)','27101980',14,'LUBRICANT','PIECES','Servo',true,NOW(),NOW()),
(189,1,'2T Loose Oil','27101980',320,'LUBRICANT','LITERS','Servo',true,NOW(),NOW()),
(190,1,'4T Oil 20W-40 (1L)','27101980',380,'LUBRICANT','PIECES','Servo',true,NOW(),NOW()),
(191,1,'4T Oil 20W-50 (1L)','271019',NULL,'LUBRICANT','PIECES','Servo',true,NOW(),NOW()),
(192,1,'Servo Honda Josh 10W-30 (1L)','271019',NULL,'LUBRICANT','PIECES','Servo',true,NOW(),NOW()),
(193,1,'4T Oil Zoom 20W-40 (1L)','271019',377,'LUBRICANT','PIECES','Servo',true,NOW(),NOW()),
(194,1,'4T Xtra 20W-40 BS6 (1L)','271019',464,'LUBRICANT','PIECES','Servo',true,NOW(),NOW()),
(195,1,'Gear Oil 90 (1L)','27101980',NULL,'LUBRICANT','PIECES','Servo',true,NOW(),NOW()),
(196,1,'Gear Oil 90 (5L)','27101980',NULL,'LUBRICANT','PIECES','Servo',true,NOW(),NOW()),
(197,1,'Battery Acid','28111990',40,'CONSUMABLE','PIECES','Generic',true,NOW(),NOW()),
(198,1,'ACV',NULL,NULL,'CONSUMABLE','PIECES','Generic',true,NOW(),NOW()),
(199,1,'Adon Diesel Additive','38119000',15,'CONSUMABLE','PIECES','Servo',true,NOW(),NOW()),
(200,1,'Adon Petrol Additive','38119000',9,'CONSUMABLE','PIECES','Servo',true,NOW(),NOW()),
(201,1,'Blue Cloth',NULL,20,'ACCESSORY','PIECES','Generic',true,NOW(),NOW()),
(202,1,'Brake Fluid (1/2L)','28190010',212,'LUBRICANT','PIECES','Servo',true,NOW(),NOW()),
(203,1,'Clear Blue DEF (10L)','31021000',670,'CONSUMABLE','PIECES','Clear Blue',true,NOW(),NOW()),
(204,1,'Clear Blue DEF (20L)',NULL,1300,'CONSUMABLE','PIECES','Clear Blue',true,NOW(),NOW()),
(205,1,'Distilled Water','28530010',12,'CONSUMABLE','PIECES','Generic',true,NOW(),NOW()),
(206,1,'Kool Plus Coolant (1L)','38200000',355,'LUBRICANT','PIECES','Servo',true,NOW(),NOW()),
(207,1,'Kool Plus Coolant (1/2L)','38200000',182,'LUBRICANT','PIECES','Servo',true,NOW(),NOW()),
(208,1,'Pride Alt Plus 15W-40 (15L)','27101980',4817,'LUBRICANT','PIECES','Servo',true,NOW(),NOW()),
(209,1,'RR3 Gem Grease (1Kg)','27101990',575,'LUBRICANT','PIECES','Servo',true,NOW(),NOW()),
(210,1,'RR3 Gem Grease (500g)','27101990',270,'LUBRICANT','PIECES','Servo',true,NOW(),NOW()),
(211,1,'RR3 Gem Grease (3Kg)','27101990',646.28,'LUBRICANT','PIECES','Servo',true,NOW(),NOW()),
(212,1,'Scootomatic 10W-30 (800ml)','271019',NULL,'LUBRICANT','PIECES','Servo',true,NOW(),NOW()),
(213,1,'Servo 68 Industrial Oil (26L)','271019',3120,'LUBRICANT','PIECES','Servo',true,NOW(),NOW()),
(214,1,'Tata Genuine Oil 15W-40 (20L)',NULL,1040,'LUBRICANT','PIECES','Tata',true,NOW(),NOW()),
(215,1,'Tru4 Kraaft TVS 10W-30 (900ml)','271019',NULL,'LUBRICANT','PIECES','Servo',true,NOW(),NOW()),
(216,1,'Yellow Cloth',NULL,25,'ACCESSORY','PIECES','Generic',true,NOW(),NOW())
ON CONFLICT (id) DO NOTHING;
SELECT setval('product_id_seq', (SELECT MAX(id) FROM product));

-- ============================================================
-- S2: Payment Modes
-- ============================================================
\echo 'S2: Payment modes...'

INSERT INTO payment_mode (id,mode_name) VALUES (1,'CASH'),(2,'CARD'),(3,'CHEQUE'),(4,'BANK_TRANSFER'),(5,'CCMS'),(6,'UPI')
ON CONFLICT DO NOTHING;

-- ============================================================
-- S3: Vehicles
-- ============================================================
\echo 'S3: Vehicles...'

ALTER TABLE vehicle ALTER COLUMN vehicle_number TYPE varchar(100);
TRUNCATE customer_vehicle_mapper CASCADE;
TRUNCATE vehicle CASCADE;

-- From credit bills (with customer)
INSERT INTO vehicle (vehicle_number, customer_id, status, created_at, updated_at)
SELECT v.vno, cm.pg_id, 'ACTIVE', NOW(), NOW()
FROM (
    SELECT DISTINCT ON (vno) vno, cid
    FROM (SELECT UPPER(TRIM(idx_vehicleno)) AS vno, cid, COUNT(*) AS cnt
          FROM raw.creditbill_test WHERE idx_vehicleno IS NOT NULL AND TRIM(idx_vehicleno) != ''
          GROUP BY UPPER(TRIM(idx_vehicleno)), cid) ranked
    ORDER BY vno, cnt DESC
) v LEFT JOIN _migration_cust_map cm ON v.cid = cm.mysql_id
ON CONFLICT DO NOTHING;

-- From cash bills (walk-in, no customer)
INSERT INTO vehicle (vehicle_number, status, created_at, updated_at)
SELECT DISTINCT UPPER(TRIM(idx_vehicleno)), 'ACTIVE', NOW(), NOW()
FROM raw.cashbill WHERE NULLIF(TRIM(idx_vehicleno),'') IS NOT NULL
  AND UPPER(TRIM(idx_vehicleno)) NOT IN (SELECT vehicle_number FROM vehicle)
ON CONFLICT DO NOTHING;

SELECT setval('vehicle_id_seq', (SELECT COALESCE(MAX(id),1) FROM vehicle));

INSERT INTO customer_vehicle_mapper (scid,customer_id,vehicle_id,is_active,created_at,updated_at)
SELECT DISTINCT 1,v.customer_id,v.id,true,NOW(),NOW() FROM vehicle v WHERE v.customer_id IS NOT NULL
ON CONFLICT DO NOTHING;
SELECT setval('customer_vehicle_mapper_id_seq', (SELECT COALESCE(MAX(id),1) FROM customer_vehicle_mapper));

-- ============================================================
-- S4: Tank Setup
-- ============================================================
\echo 'S4: Tanks + Nozzles...'

INSERT INTO tank (scid,name,capacity,available_stock,threshold_stock,product_id,active,created_at,updated_at)
SELECT 1,'Tank-3',20000,0,4000,(SELECT id FROM product WHERE name='Diesel' LIMIT 1),true,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM tank WHERE name='Tank-3');
INSERT INTO tank (scid,name,capacity,available_stock,threshold_stock,product_id,active,created_at,updated_at)
SELECT 1,'Tank-4',15000,0,3000,(SELECT id FROM product WHERE name='Petrol' LIMIT 1),true,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM tank WHERE name='Tank-4');
INSERT INTO tank (scid,name,capacity,available_stock,threshold_stock,product_id,active,created_at,updated_at)
SELECT 1,'Tank-5',15000,0,3000,(SELECT id FROM product WHERE name='Xtra Premium' LIMIT 1),true,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM tank WHERE name='Tank-5');

INSERT INTO nozzle (scid,nozzle_name,tank_id,pump_id,active,created_at,updated_at)
SELECT 1,n.nn,t.id,p.id,true,NOW(),NOW() FROM (VALUES
    ('N-36','Tank-4','DU-11'),('N-37','Tank-4','DU-11'),('N-38','Tank-3','DU-11'),('N-39','Tank-3','DU-11'),
    ('N-46','Tank-5','DU-15'),('N-47','Tank-4','DU-15'),('N-48','Tank-5','DU-15'),('N-49','Tank-4','DU-15'),
    ('N-50','Tank-4','DU-16'),('N-51','Tank-4','DU-16'),('N-52','Tank-3','DU-16'),('N-53','Tank-3','DU-16')
) AS n(nn,tn,pn) JOIN tank t ON t.name=n.tn JOIN pump p ON p.name=n.pn
WHERE NOT EXISTS (SELECT 1 FROM nozzle LIMIT 1);

-- ============================================================
-- S5: Credit Bills
-- ============================================================
\echo 'S5: Credit bills...'

DELETE FROM invoice_product WHERE invoice_bill_id IN (SELECT id FROM invoice_bill WHERE bill_type='CREDIT');
DELETE FROM incentive_payment WHERE invoice_bill_id IN (SELECT id FROM invoice_bill WHERE bill_type='CREDIT');
DELETE FROM payment WHERE invoice_bill_id IN (SELECT id FROM invoice_bill WHERE bill_type='CREDIT');
DELETE FROM invoice_bill WHERE bill_type='CREDIT';

CREATE TEMP TABLE _tmp_cid_map AS SELECT mysql_id AS cid, pg_id AS customer_id FROM _migration_cust_map;
-- Hardcoded fixes for unmapped CIDs
INSERT INTO _tmp_cid_map VALUES (69,(SELECT pg_id FROM _migration_cust_map WHERE mysql_id=222))
ON CONFLICT DO NOTHING;
INSERT INTO _tmp_cid_map VALUES (111,(SELECT id FROM users WHERE username='cust_111'))
ON CONFLICT DO NOTHING;

CREATE TEMP TABLE _tmp_eid_map AS SELECT mysql_id AS eid, pg_id AS employee_id FROM _migration_emp_map;
CREATE TEMP TABLE _tmp_vmap AS SELECT vehicle_number AS vno, id AS vid FROM vehicle WHERE vehicle_number IS NOT NULL;

INSERT INTO invoice_bill (scid,bill_date,bill_no,bill_type,payment_mode,payment_status,bill_status,
    customer_id,vehicle_id,raised_by_id,gross_amount,total_discount,net_amount,customer_gst,
    indent_no,bill_desc,signatory_name,signatory_cell_no,vehicle_km,reading_open,reading_close,created_at,updated_at)
SELECT 1, cb.idx_date::date + COALESCE(cb.time::time,'00:00'::time),
    TRIM(cb.idx_billid),'CREDIT',NULL,
    CASE WHEN UPPER(TRIM(cb.bill_status))='PAID' THEN 'PAID' ELSE 'NOT_PAID' END,
    CASE WHEN UPPER(TRIM(cb.bill_status))='PAID' THEN 'PAID' ELSE 'PENDING' END,
    cm.customer_id, vm.vid, em.employee_id,
    CASE WHEN cb.sub_total>0 THEN cb.sub_total ELSE cb.netamount END,
    COALESCE(cb.discount,0), COALESCE(cb.netamount,0),
    NULLIF(TRIM(cb.customer_gst),''), NULLIF(TRIM(cb.idx_indentno),''),
    NULLIF(TRIM(cb.bill_description),''), NULLIF(TRIM(cb.signatorysname),''),
    NULLIF(TRIM(cb.signatoryscellno),''),
    CASE WHEN cb.vehiclekm>0 THEN cb.vehiclekm::BIGINT END,
    CASE WHEN cb.pumpreadingopen>0 THEN cb.pumpreadingopen::BIGINT END,
    CASE WHEN cb.pumpreadingclose>0 THEN cb.pumpreadingclose::BIGINT END,
    NOW(),NOW()
FROM raw.creditbill_test cb
LEFT JOIN _tmp_cid_map cm ON cb.cid=cm.cid
LEFT JOIN _tmp_eid_map em ON cb.eid=em.eid
LEFT JOIN _tmp_vmap vm ON UPPER(TRIM(cb.idx_vehicleno))=vm.vno
WHERE TRIM(cb.idx_billid) IS NOT NULL AND TRIM(cb.idx_billid)!='' AND cb.idx_date IS NOT NULL;

-- Fix remaining unmapped customers by name
UPDATE invoice_bill SET customer_id=(SELECT pg_id FROM _migration_cust_map WHERE mysql_id=169)
WHERE customer_id IS NULL AND bill_no IN (SELECT TRIM(idx_billid) FROM raw.creditbill_test WHERE cid=0 AND TRIM(idx_customername)='GULLU BHAI');
UPDATE invoice_bill SET customer_id=(SELECT pg_id FROM _migration_cust_map WHERE mysql_id=72)
WHERE customer_id IS NULL AND bill_no IN (SELECT TRIM(idx_billid) FROM raw.creditbill_test WHERE cid=0 AND TRIM(idx_customername)='RAMESH (L.V.HOTEL)');
UPDATE invoice_bill SET customer_id=(SELECT pg_id FROM _migration_cust_map WHERE mysql_id=27)
WHERE customer_id IS NULL AND bill_no IN (SELECT TRIM(idx_billid) FROM raw.creditbill_test WHERE cid=0 AND TRIM(idx_customername)='B.S.N.L');

-- Credit bill products
CREATE TEMP TABLE _tmp_cbmap AS SELECT id AS ibid, bill_no, scid FROM invoice_bill WHERE bill_type='CREDIT';
CREATE INDEX ON _tmp_cbmap (bill_no);

INSERT INTO invoice_product (scid,invoice_bill_id,product_id,quantity,unit_price,amount,gross_amount,created_at,updated_at)
SELECT bm.scid,bm.ibid,pm.pg_product_id,COALESCE(cp.quantity,0),COALESCE(cp.rate,0),COALESCE(cp.amount,0),COALESCE(cp.amount,0),NOW(),NOW()
FROM raw.creditbill_products_test cp
JOIN _tmp_cbmap bm ON TRIM(cp.fk_billid)=bm.bill_no
JOIN _product_name_map pm ON UPPER(TRIM(cp.product_name))=UPPER(pm.old_name)
WHERE TRIM(cp.fk_billid) IS NOT NULL AND TRIM(cp.product_name) IS NOT NULL;

-- Merge duplicate credit product lines
WITH d AS (SELECT ip.invoice_bill_id,ip.product_id,MIN(ip.id) AS kid,SUM(ip.quantity) AS q,SUM(ip.amount) AS a,SUM(ip.gross_amount) AS g
    FROM invoice_product ip JOIN invoice_bill ib ON ip.invoice_bill_id=ib.id WHERE ib.bill_type='CREDIT'
    GROUP BY ip.invoice_bill_id,ip.product_id HAVING COUNT(*)>1)
UPDATE invoice_product ip SET quantity=d.q,amount=d.a,gross_amount=d.g FROM d WHERE ip.id=d.kid;
WITH d AS (SELECT ip.invoice_bill_id,ip.product_id,MIN(ip.id) AS kid
    FROM invoice_product ip JOIN invoice_bill ib ON ip.invoice_bill_id=ib.id WHERE ib.bill_type='CREDIT'
    GROUP BY ip.invoice_bill_id,ip.product_id HAVING COUNT(*)>1)
DELETE FROM invoice_product ip USING d WHERE ip.invoice_bill_id=d.invoice_bill_id AND ip.product_id=d.product_id AND ip.id!=d.kid;

\echo '  Credit bills done.'

-- ============================================================
-- S6: Cash Bills
-- ============================================================
\echo 'S6: Cash bills...'

DELETE FROM invoice_product WHERE invoice_bill_id IN (SELECT id FROM invoice_bill WHERE bill_type='CASH');
DELETE FROM invoice_bill WHERE bill_type='CASH';

INSERT INTO invoice_bill (scid,bill_date,bill_no,bill_type,payment_mode,payment_status,bill_status,
    raised_by_id,vehicle_id,gross_amount,total_discount,net_amount,customer_gst,
    vehicle_km,reading_open,reading_close,created_at,updated_at)
SELECT 1, cb.idx_date::date + COALESCE(cb.time::time,'00:00'::time),
    TRIM(cb.idx_billid),'CASH',
    CASE UPPER(TRIM(cb.payment_mode)) WHEN 'CASH' THEN 'CASH' WHEN 'CARD' THEN 'CARD' WHEN 'CCMS' THEN 'CCMS'
        WHEN 'CHEQUE' THEN 'CHEQUE' WHEN 'BANK_STATEMENTS' THEN 'BANK_TRANSFER' ELSE 'CASH' END,
    'PAID','PAID', em.employee_id, vm.vid,
    COALESCE(cb.netamount,0), COALESCE(cb.discount,0),
    COALESCE(cb.final_amount, CASE WHEN cb.discount>0 THEN cb.netamount-cb.discount ELSE cb.netamount END, cb.netamount,0),
    NULLIF(TRIM(cb.customer_gst),''),
    CASE WHEN cb.vehiclekm>0 THEN cb.vehiclekm::BIGINT END,
    CASE WHEN cb.pumpreadingopen>0 THEN cb.pumpreadingopen::BIGINT END,
    CASE WHEN cb.pumpreadingclose>0 THEN cb.pumpreadingclose::BIGINT END,
    NOW(),NOW()
FROM raw.cashbill cb
LEFT JOIN _tmp_eid_map em ON cb.eid=em.eid
LEFT JOIN _tmp_vmap vm ON UPPER(TRIM(cb.idx_vehicleno))=vm.vno
WHERE TRIM(cb.idx_billid) IS NOT NULL AND TRIM(cb.idx_billid)!='' AND cb.idx_date IS NOT NULL;

CREATE TEMP TABLE _tmp_cashmap AS SELECT id AS ibid, bill_no, scid FROM invoice_bill WHERE bill_type='CASH';
CREATE INDEX ON _tmp_cashmap (bill_no);

INSERT INTO invoice_product (scid,invoice_bill_id,product_id,quantity,unit_price,amount,gross_amount,created_at,updated_at)
SELECT bm.scid,bm.ibid,pm.pg_product_id,COALESCE(cp.quantity,0),COALESCE(cp.rate,0),COALESCE(cp.amount,0),COALESCE(cp.amount,0),NOW(),NOW()
FROM raw.cashbill_products cp
JOIN _tmp_cashmap bm ON TRIM(cp.fk_billid)=bm.bill_no
JOIN _product_name_map pm ON UPPER(TRIM(cp.product_name))=UPPER(pm.old_name)
WHERE TRIM(cp.fk_billid) IS NOT NULL AND TRIM(cp.product_name) IS NOT NULL;

-- Merge duplicate cash product lines
WITH d AS (SELECT ip.invoice_bill_id,ip.product_id,MIN(ip.id) AS kid,SUM(ip.quantity) AS q,SUM(ip.amount) AS a,SUM(ip.gross_amount) AS g
    FROM invoice_product ip JOIN invoice_bill ib ON ip.invoice_bill_id=ib.id WHERE ib.bill_type='CASH'
    GROUP BY ip.invoice_bill_id,ip.product_id HAVING COUNT(*)>1)
UPDATE invoice_product ip SET quantity=d.q,amount=d.a,gross_amount=d.g FROM d WHERE ip.id=d.kid;
WITH d AS (SELECT ip.invoice_bill_id,ip.product_id,MIN(ip.id) AS kid
    FROM invoice_product ip JOIN invoice_bill ib ON ip.invoice_bill_id=ib.id WHERE ib.bill_type='CASH'
    GROUP BY ip.invoice_bill_id,ip.product_id HAVING COUNT(*)>1)
DELETE FROM invoice_product ip USING d WHERE ip.invoice_bill_id=d.invoice_bill_id AND ip.product_id=d.product_id AND ip.id!=d.kid;

SELECT setval('invoice_bill_id_seq', (SELECT MAX(id) FROM invoice_bill));
\echo '  Cash bills done.'

-- ============================================================
-- S7: E-Advances
-- ============================================================
\echo 'S7: E-Advances...'
DELETE FROM e_advance;

INSERT INTO e_advance (scid,advance_type,transaction_date,amount,remarks,created_at,updated_at)
SELECT 1,'CASH',cashadv_date,cashadv_amount,NULLIF(TRIM(cashadv_desc),''),NOW(),NOW()
FROM raw.cash_advances WHERE cashadv_amount IS NOT NULL AND cashadv_amount>0;

INSERT INTO e_advance (scid,advance_type,transaction_date,amount,created_at,updated_at)
SELECT 1,'HOME',ha_date,ha_amount,NOW(),NOW()
FROM raw.home_advances WHERE ha_amount IS NOT NULL AND ha_amount>0;

INSERT INTO e_advance (scid,advance_type,transaction_date,amount,batch_id,tid,customer_name,customer_phone,bank_name,card_last4_digit,created_at,updated_at)
SELECT 1,'CARD',crd_adv_date,crd_adv_amount,crd_adv_batchid::text,crd_adv_tid::text,
    NULLIF(TRIM(crd_adv_customername),''),NULLIF(TRIM(crd_adv_customerphoneno),''),
    NULLIF(TRIM(crd_adv_bankname),''),NULLIF(TRIM(crd_adv_cardlast4digit),''),NOW(),NOW()
FROM raw.card_advances WHERE crd_adv_amount IS NOT NULL AND crd_adv_amount>0;

INSERT INTO e_advance (scid,advance_type,transaction_date,amount,customer_name,ccms_number,created_at,updated_at)
SELECT 1,'CCMS',ccms_date,ccms_cardamt,NULLIF(TRIM(ccms_customer),''),NULLIF(TRIM(ccms_cardno),''),NOW(),NOW()
FROM raw.ccms_advance WHERE ccms_cardamt IS NOT NULL AND ccms_cardamt>0;

INSERT INTO e_advance (scid,advance_type,transaction_date,amount,bank_name,cheque_no,cheque_date,in_favor_of,customer_name,created_at,updated_at)
SELECT 1,'CHEQUE',cheque_date_today,cheque_amt,NULLIF(TRIM(cheque_bank),''),NULLIF(TRIM(cheque_no),''),
    cheque_date::date,NULLIF(TRIM(cheque_infavourof),''),NULLIF(TRIM(cheque_cusname),''),NOW(),NOW()
FROM raw.cheque_advance WHERE cheque_amt IS NOT NULL AND cheque_amt>0;

INSERT INTO e_advance (scid,advance_type,transaction_date,amount,bank_name,created_at,updated_at)
SELECT 1,'BANK_TRANSFER',date_today,received_amt,NULLIF(TRIM(bank_name),''),NOW(),NOW()
FROM raw.bank_transfer_advance WHERE received_amt IS NOT NULL AND received_amt>0;

SELECT setval('e_advance_id_seq', (SELECT MAX(id) FROM e_advance));

-- ============================================================
-- S8: Operational Advances
-- ============================================================
\echo 'S8: Operational advances...'
DELETE FROM operational_advance;

INSERT INTO operational_advance (scid,advance_type,advance_date,amount,recipient_name,status,returned_amount,utilized_amount,created_at,updated_at)
SELECT 1,'CASH',cashadv_date,cashadv_amount,NULLIF(TRIM(cashadv_received_by),''),'SETTLED',0,0,NOW(),NOW()
FROM raw.cash_advances WHERE cashadv_amount IS NOT NULL;

INSERT INTO operational_advance (scid,advance_type,advance_date,amount,status,returned_amount,utilized_amount,created_at,updated_at)
SELECT 1,'HOME',ha_date,ha_amount,'SETTLED',0,0,NOW(),NOW()
FROM raw.home_advances WHERE ha_amount IS NOT NULL;

SELECT setval('operational_advance_id_seq', (SELECT COALESCE(MAX(id),1) FROM operational_advance));

-- ============================================================
-- S9: Statements
-- ============================================================
\echo 'S9: Statements...'

UPDATE invoice_bill SET statement_id=NULL WHERE statement_id IS NOT NULL;
DELETE FROM payment WHERE statement_id IS NOT NULL;
DELETE FROM statement;

CREATE TEMP TABLE _tmp_sc AS
SELECT DISTINCT ON (sa.stno) sa.stno AS smt_no, ib.customer_id
FROM raw.statement_association_1 sa JOIN invoice_bill ib ON TRIM(sa.billno)=ib.bill_no
WHERE ib.customer_id IS NOT NULL GROUP BY sa.stno,ib.customer_id ORDER BY sa.stno,COUNT(*) DESC;

INSERT INTO statement (scid,statement_no,statement_date,from_date,to_date,customer_id,total_amount,net_amount,
    received_amount,balance_amount,number_of_bills,status,created_at,updated_at)
SELECT 1,'S-'||cs.idx_smtno,cs.idx_date,cs.idx_date,cs.idx_date,sc.customer_id,
    cs.smt_amt,cs.smt_amt,0,cs.smt_amt,0,
    CASE UPPER(TRIM(cs.smt_status)) WHEN 'PAID' THEN 'PAID' ELSE 'NOT_PAID' END,NOW(),NOW()
FROM raw.credit_statement cs JOIN _tmp_sc sc ON sc.smt_no=cs.idx_smtno
ON CONFLICT DO NOTHING;

SELECT setval('statement_id_seq', (SELECT COALESCE(MAX(id),1) FROM statement));

CREATE TEMP TABLE _tmp_sm AS SELECT id AS sid, CAST(REPLACE(statement_no,'S-','') AS INTEGER) AS smt_no FROM statement;
CREATE INDEX ON _tmp_sm (smt_no);

UPDATE invoice_bill ib SET statement_id=sm.sid
FROM raw.statement_association_1 sa JOIN _tmp_sm sm ON sa.stno=sm.smt_no
WHERE TRIM(sa.billno)=ib.bill_no AND ib.statement_id IS NULL;

UPDATE statement s SET number_of_bills=sub.cnt
FROM (SELECT statement_id,COUNT(*) AS cnt FROM invoice_bill WHERE statement_id IS NOT NULL GROUP BY statement_id) sub
WHERE s.id=sub.statement_id;

-- ============================================================
-- S10: Payments
-- ============================================================
\echo 'S10: Payments...'
DELETE FROM payment;

-- Bill-level payments (EXCLUDE statement-linked bills)
INSERT INTO payment (scid,payment_date,amount,payment_mode_id,customer_id,invoice_bill_id,created_at,updated_at)
SELECT 1,r.idx_received_date::date+r.idx_received_date::time,r.receivedamount,
    CASE UPPER(TRIM(r.payment_mode)) WHEN 'CASH' THEN 1 WHEN 'CARD' THEN 2 WHEN 'CHEQUE' THEN 3
        WHEN 'BANK_STATEMENTS' THEN 4 WHEN 'BANK STATEMENTS' THEN 4 WHEN 'CCMS' THEN 5 ELSE 1 END,
    ib.customer_id,ib.id,NOW(),NOW()
FROM raw.incomebill r JOIN invoice_bill ib ON TRIM(r.idx_billid)=ib.bill_no
WHERE r.receivedamount IS NOT NULL AND r.receivedamount>0 AND ib.statement_id IS NULL;

-- Partial payments (EXCLUDE statement-linked)
INSERT INTO payment (scid,payment_date,amount,payment_mode_id,customer_id,invoice_bill_id,created_at,updated_at)
SELECT 1,r.received_date::date+r.received_date::time,r.received_amount,
    CASE UPPER(TRIM(r.payment_mode)) WHEN 'CASH' THEN 1 WHEN 'CARD' THEN 2 WHEN 'CHEQUE' THEN 3
        WHEN 'BANK_STATEMENTS' THEN 4 WHEN 'BANK STATEMENTS' THEN 4 WHEN 'CCMS' THEN 5 ELSE 1 END,
    ib.customer_id,ib.id,NOW(),NOW()
FROM raw.intermediatebills r JOIN invoice_bill ib ON TRIM(r.fkbillid)=ib.bill_no
WHERE r.received_amount IS NOT NULL AND r.received_amount>0 AND ib.statement_id IS NULL;

-- Remove duplicate incomebill entries where intermediates cover full amount
WITH bwb AS (SELECT TRIM(r.idx_billid) AS bn, r.receivedamount AS ia
    FROM raw.incomebill r WHERE TRIM(r.idx_billid) IN (SELECT TRIM(fkbillid) FROM raw.intermediatebills) AND r.receivedamount>0),
it AS (SELECT TRIM(fkbillid) AS bn, SUM(received_amount) AS t FROM raw.intermediatebills GROUP BY TRIM(fkbillid)),
ed AS (SELECT b.bn,b.ia FROM bwb b JOIN it i ON b.bn=i.bn WHERE ABS(b.ia-i.t)<0.01)
DELETE FROM payment p USING invoice_bill ib, ed
WHERE p.invoice_bill_id=ib.id AND ib.bill_no=ed.bn AND p.amount=ed.ia
  AND NOT EXISTS(SELECT 1 FROM raw.intermediatebills r WHERE TRIM(r.fkbillid)=ed.bn AND ABS(r.received_amount-p.amount)<0.01);

-- Statement full payments
INSERT INTO payment (scid,payment_date,amount,payment_mode_id,customer_id,statement_id,created_at,updated_at)
SELECT 1,r.idx_recv_date::date+r.idx_recv_date::time,r.idx_creditbill_recvamount,
    CASE UPPER(TRIM(r.payment_types)) WHEN 'CASH' THEN 1 WHEN 'CARD' THEN 2 WHEN 'CHEQUE' THEN 3
        WHEN 'BANK_STATEMENTS' THEN 4 WHEN 'BANK STATEMENTS' THEN 4 WHEN 'CCMS' THEN 5 ELSE 1 END,
    s.customer_id,s.id,NOW(),NOW()
FROM raw.income_creditbill_statement r
JOIN _tmp_sm sm ON r.idx_creditbill_statement=sm.smt_no JOIN statement s ON s.id=sm.sid
WHERE r.idx_creditbill_recvamount IS NOT NULL AND r.idx_creditbill_recvamount>0;

-- Statement partial payments
INSERT INTO payment (scid,payment_date,amount,payment_mode_id,customer_id,statement_id,created_at,updated_at)
SELECT 1,r.intermediate_creditbill_statement_recvdate::date+r.intermediate_creditbill_statement_recvdate::time,
    r.intermediate_creditbill_statement_recvamt,
    CASE UPPER(TRIM(r.intermediate_creditbill_statement_payment)) WHEN 'CASH' THEN 1 WHEN 'CARD' THEN 2 WHEN 'CHEQUE' THEN 3
        WHEN 'BANK_STATEMENTS' THEN 4 WHEN 'BANK STATEMENTS' THEN 4 WHEN 'CCMS' THEN 5 ELSE 1 END,
    s.customer_id,s.id,NOW(),NOW()
FROM raw.intermediate_creditbill_statement r
JOIN _tmp_sm sm ON r.idxintermediate_creditbill_statement_no=sm.smt_no JOIN statement s ON s.id=sm.sid
WHERE r.intermediate_creditbill_statement_recvamt IS NOT NULL AND r.intermediate_creditbill_statement_recvamt>0;

SELECT setval('payment_id_seq', (SELECT MAX(id) FROM payment));

-- Update payment statuses
UPDATE invoice_bill ib SET payment_status='PAID',bill_status='PAID'
FROM (SELECT invoice_bill_id,SUM(amount) AS tp FROM payment WHERE invoice_bill_id IS NOT NULL GROUP BY invoice_bill_id) p
WHERE ib.id=p.invoice_bill_id AND p.tp>=ib.net_amount AND ib.bill_type='CREDIT';

-- PARTIAL: bills with some but insufficient payments
UPDATE invoice_bill ib SET payment_status='PARTIAL'
FROM (SELECT invoice_bill_id, SUM(amount) AS tp FROM payment WHERE invoice_bill_id IS NOT NULL GROUP BY invoice_bill_id) p
WHERE ib.id=p.invoice_bill_id AND p.tp>0 AND p.tp<ib.net_amount AND ib.bill_type='CREDIT';

UPDATE statement s SET received_amount=sub.tr,balance_amount=s.net_amount-sub.tr
FROM (SELECT statement_id,SUM(amount) AS tr FROM payment WHERE statement_id IS NOT NULL GROUP BY statement_id) sub
WHERE s.id=sub.statement_id;

-- Mark fully-paid statements
UPDATE statement SET status='PAID' WHERE balance_amount<=0 AND status!='PAID';

-- Cascade: bills in PAID statements → PAID
UPDATE invoice_bill SET payment_status='PAID', bill_status='PAID'
WHERE statement_id IN (SELECT id FROM statement WHERE status='PAID') AND payment_status!='PAID';

-- ============================================================
-- S10b: Payment detail enrichment
-- ============================================================
\echo 'S10b: Payment detail enrichment...'

-- Cheque details → reference_no + remarks
UPDATE payment p SET reference_no=NULLIF(TRIM(cd.chequeno::text),''), remarks=NULLIF(TRIM(COALESCE(cd.chequebankname,'')||' '||COALESCE(cd.cheqcusname,'')),'')
FROM raw.cheque_incomebill cd JOIN invoice_bill ib ON TRIM(cd.idxbill_id)=ib.bill_no
WHERE p.invoice_bill_id=ib.id AND p.reference_no IS NULL;

-- Card details → reference_no + remarks
UPDATE payment p SET reference_no=NULLIF(TRIM(cd.card_number),''), remarks=NULLIF(TRIM(cd.card_name),'')
FROM raw.card_incomebill cd JOIN invoice_bill ib ON TRIM(cd.idxbill_id)=ib.bill_no
WHERE p.invoice_bill_id=ib.id AND p.reference_no IS NULL;

-- CCMS details → reference_no + remarks
UPDATE payment p SET reference_no=NULLIF(TRIM(cd.ccms_cardno),''), remarks=NULLIF(TRIM(cd.ccms_cname),'')
FROM raw.ccms_incomebill cd JOIN invoice_bill ib ON TRIM(cd.idxbill_id)=ib.bill_no
WHERE p.invoice_bill_id=ib.id AND p.reference_no IS NULL;

-- Bank transfer details → remarks
UPDATE payment p SET remarks=NULLIF(TRIM(cd.bs_bank_name),'')
FROM raw.bank_transfer_incomebill cd JOIN invoice_bill ib ON TRIM(cd.bs_billno)=ib.bill_no
WHERE p.invoice_bill_id=ib.id AND p.remarks IS NULL;

-- ============================================================
-- S10c: received_by_id backfill (best-effort)
-- ============================================================
\echo 'S10c: received_by_id backfill...'

-- Best-effort: set received_by = bill's raised_by (cashier who created the bill)
UPDATE payment p SET received_by_id=ib.raised_by_id
FROM invoice_bill ib WHERE p.invoice_bill_id=ib.id AND p.received_by_id IS NULL AND ib.raised_by_id IS NOT NULL;

-- ============================================================
-- S11: Shifts
-- ============================================================
\echo 'S11: Shifts...'
DELETE FROM shift_closing_reports;
DELETE FROM shifts;

-- Dynamic cashier mapping (resolve by name match, not hardcoded IDs)
CREATE TEMP TABLE _tmp_cm (rn VARCHAR, eid BIGINT);
INSERT INTO _tmp_cm (rn, eid)
SELECT alias, e.id FROM (VALUES
    ('Rajendran','Rajendran'),('Rajendran M','Rajendran'),
    ('Manikandan P','Manikandan'),
    ('SAKTHIVEL S','SAKTHIVEL'),('Sakthivel S','SAKTHIVEL'),
    ('JAYARAMAN K','JAYARAMAN'),('JayaRamn K','JAYARAMAN'),
    ('SANKAR','SANKAR'),('vijay','vijay')
) AS aliases(alias, search_name)
JOIN person_entity pe ON UPPER(pe.name) LIKE '%' || UPPER(search_name) || '%'
JOIN employees e ON e.id = pe.id;

INSERT INTO shifts (scid,start_time,end_time,attendant_id,status,created_at,updated_at)
SELECT 1, COALESCE(LAG(sct.shift_timestamp) OVER (ORDER BY sct.shiftdate), sct.shiftdate::timestamp+INTERVAL '6 hours'),
    sct.shift_timestamp, cm.eid, 'CLOSED', NOW(), NOW()
FROM raw.shift_closing_timings sct LEFT JOIN _tmp_cm cm ON TRIM(sct.sc_cashier)=cm.rn
ORDER BY sct.shiftdate;

SELECT setval('shifts_id_seq', (SELECT COALESCE(MAX(id),1) FROM shifts));

-- ============================================================
-- S12: Incentives
-- ============================================================
\echo 'S12: Incentives...'

UPDATE invoice_product SET discount_rate=NULL,discount_amount=NULL,amount=gross_amount WHERE discount_amount IS NOT NULL AND discount_amount>0;
UPDATE invoice_bill SET total_discount=0,net_amount=gross_amount WHERE total_discount>0;
DELETE FROM incentive_payment;

UPDATE invoice_product ip SET discount_rate=r.incentive_disc_rate,discount_amount=r.incentive_amt,amount=ip.gross_amount-r.incentive_amt
FROM raw.incentive_table r
JOIN invoice_bill ib ON UPPER(TRIM(r.incentive_billno))=UPPER(ib.bill_no)
JOIN _product_name_map pm ON UPPER(TRIM(r.incentive_productname))=UPPER(pm.old_name)
WHERE ip.invoice_bill_id=ib.id AND ip.product_id=pm.pg_product_id AND r.incentive_disc_rate>0 AND r.incentive_amt>0;

UPDATE invoice_bill ib SET total_discount=sub.td,net_amount=ib.gross_amount-sub.td
FROM (SELECT invoice_bill_id,SUM(COALESCE(discount_amount,0)) AS td FROM invoice_product WHERE discount_amount IS NOT NULL AND discount_amount>0 GROUP BY invoice_bill_id) sub
WHERE ib.id=sub.invoice_bill_id;

-- Linked incentive payments
INSERT INTO incentive_payment (scid,payment_date,amount,description,customer_id,invoice_bill_id,created_at,updated_at)
SELECT 1,r.incentive_date,r.incentive_amt,
    TRIM(r.incentive_productname)||' x '||r.incentive_qty||' @ '||r.incentive_disc_rate||' disc',
    ib.customer_id,ib.id,NOW(),NOW()
FROM raw.incentive_table r JOIN invoice_bill ib ON UPPER(TRIM(r.incentive_billno))=UPPER(ib.bill_no)
WHERE r.incentive_amt>0 AND TRIM(r.incentive_billno) NOT IN ('XXXX','0000','Nil','nil','NULL','');

-- Unlinked incentive payments (real transactions without valid bill)
INSERT INTO incentive_payment (scid,payment_date,amount,description,customer_id,invoice_bill_id,created_at,updated_at)
SELECT 1,r.incentive_date,r.incentive_amt,
    'UNLINKED: bill='||COALESCE(TRIM(r.incentive_billno),'?')||' cust='||COALESCE(TRIM(r.incentive_customername),'?')
    ||CASE WHEN TRIM(r.incentive_productname) NOT IN ('Nil','NIL','NULL','') THEN ' prod='||TRIM(r.incentive_productname)||' x'||r.incentive_qty ELSE '' END
    ||' @'||r.incentive_disc_rate||' disc',
    pe.id,NULL,NOW(),NOW()
FROM raw.incentive_table r
LEFT JOIN invoice_bill ib ON UPPER(TRIM(r.incentive_billno))=UPPER(ib.bill_no)
LEFT JOIN person_entity pe ON UPPER(TRIM(r.incentive_customername))=UPPER(TRIM(pe.name)) AND pe.id IN (SELECT id FROM customer)
WHERE ib.id IS NULL AND r.incentive_amt>0;

SELECT setval('incentive_payment_id_seq', (SELECT COALESCE(MAX(id),1) FROM incentive_payment));

-- ============================================================
-- POST-MIGRATION: NORMALIZE ENUM VALUES
-- ============================================================
\echo 'Normalizing status enum values...'
UPDATE users SET status = UPPER(TRIM(status)) WHERE status != UPPER(TRIM(status));
-- customer table has no status column; skip normalization
UPDATE vehicle SET status = UPPER(TRIM(status)) WHERE status IS NOT NULL AND status != UPPER(TRIM(status));

-- ============================================================
-- S12: Expense Types
-- ============================================================
\echo 'S12: Expense types...'

INSERT INTO expense_type (type_name, created_at, updated_at)
SELECT DISTINCT TRIM(expensetypename), NOW(), NOW()
FROM raw.expensetype
WHERE TRIM(expensetypename) NOT IN ('Select')
  AND NOT EXISTS (SELECT 1 FROM expense_type WHERE type_name = TRIM(raw.expensetype.expensetypename))
ON CONFLICT DO NOTHING;

-- ============================================================
-- S13: Expenses
-- ============================================================
\echo 'S13: Expenses...'

INSERT INTO expense (scid, expense_date, amount, description, expense_type_id, created_at, updated_at)
SELECT 1,
    e.exp_date,
    e.exp_amount,
    e.exp_description,
    et.id,
    NOW(), NOW()
FROM raw.expense e
LEFT JOIN expense_type et ON et.type_name = TRIM(e.exp_type)
WHERE NOT EXISTS (SELECT 1 FROM expense LIMIT 1);

SELECT setval('expense_id_seq', (SELECT COALESCE(MAX(id),1) FROM expense));

-- ============================================================
-- S14: Product Price History (Fuel prices)
-- ============================================================
\echo 'S14: Product price history...'

INSERT INTO product_price_history (effective_date, product_id, price, created_at, updated_at)
SELECT pp.pp_date,
    (SELECT id FROM product WHERE name='Petrol' LIMIT 1),
    pp.ms_price,
    NOW(), NOW()
FROM raw.productprice pp
WHERE pp.ms_price > 0
  AND NOT EXISTS (SELECT 1 FROM product_price_history LIMIT 1)
ORDER BY pp.pp_date;

INSERT INTO product_price_history (effective_date, product_id, price, created_at, updated_at)
SELECT pp.pp_date,
    (SELECT id FROM product WHERE name='Xtra Premium' LIMIT 1),
    pp.xp_price,
    NOW(), NOW()
FROM raw.productprice pp
WHERE pp.xp_price > 0
  AND NOT EXISTS (SELECT 1 FROM product_price_history WHERE product_id = (SELECT id FROM product WHERE name='Xtra Premium' LIMIT 1) LIMIT 1)
ORDER BY pp.pp_date;

INSERT INTO product_price_history (effective_date, product_id, price, created_at, updated_at)
SELECT pp.pp_date,
    (SELECT id FROM product WHERE name='Diesel' LIMIT 1),
    pp.hsd_price,
    NOW(), NOW()
FROM raw.productprice pp
WHERE pp.hsd_price > 0
  AND NOT EXISTS (SELECT 1 FROM product_price_history WHERE product_id = (SELECT id FROM product WHERE name='Diesel' LIMIT 1) LIMIT 1)
ORDER BY pp.pp_date;

SELECT setval('product_price_history_id_seq', (SELECT COALESCE(MAX(id),1) FROM product_price_history));

-- ============================================================
-- S15: Tank Inventory (from e_book_tw tables)
-- ============================================================
\echo 'S15: Tank inventory...'

-- Tank-4 (MS/Petrol) from e_book_tw_ms
INSERT INTO tank_inventory (scid, date, tank_id, open_stock, income_stock, total_stock, close_stock, sale_stock, created_at, updated_at)
SELECT 1,
    tw.tw_ms_edate,
    (SELECT id FROM tank WHERE name='Tank-4'),
    tw.tw_ms_eopen,
    tw.tw_ms_ereceipt,
    tw.tw_ms_etotal,
    tw.tw_ms_closing,
    tw.tw_ms_sales,
    NOW(), NOW()
FROM raw.e_book_tw_ms tw
WHERE NOT EXISTS (SELECT 1 FROM tank_inventory WHERE tank_id = (SELECT id FROM tank WHERE name='Tank-4') LIMIT 1)
ORDER BY tw.tw_ms_edate;

-- Tank-5 (XP/Xtra Premium) from e_book_tw_xp
INSERT INTO tank_inventory (scid, date, tank_id, open_stock, income_stock, total_stock, close_stock, sale_stock, created_at, updated_at)
SELECT 1,
    tw.tw_xp_edate,
    (SELECT id FROM tank WHERE name='Tank-5'),
    tw.tw_xp_eopen,
    tw.tw_xp_ereceipt,
    tw.tw_xp_etotal,
    tw.tw_xp_eclosing,
    tw.tw_xp_esales,
    NOW(), NOW()
FROM raw.e_book_tw_xp tw
WHERE NOT EXISTS (SELECT 1 FROM tank_inventory WHERE tank_id = (SELECT id FROM tank WHERE name='Tank-5') LIMIT 1)
ORDER BY tw.tw_xp_edate;

-- Tank-3 (HSD/Diesel) from e_book_tw_hsd_1
INSERT INTO tank_inventory (scid, date, tank_id, open_stock, income_stock, total_stock, close_stock, sale_stock, created_at, updated_at)
SELECT 1,
    tw.tw_hsd1_edate,
    (SELECT id FROM tank WHERE name='Tank-3'),
    tw.tw_hsd1_eopen,
    tw.tw_hsd1_ereceipt,
    tw.tw_hsd1_etotal,
    tw.tw_hsd1_eclosing,
    tw.tw_hsd1_esales,
    NOW(), NOW()
FROM raw.e_book_tw_hsd_1 tw
WHERE NOT EXISTS (SELECT 1 FROM tank_inventory WHERE tank_id = (SELECT id FROM tank WHERE name='Tank-3') LIMIT 1)
ORDER BY tw.tw_hsd1_edate;

SELECT setval('tank_inventory_id_seq', (SELECT COALESCE(MAX(id),1) FROM tank_inventory));

-- ============================================================
-- S16: Nozzle Inventory (from e_book_mw tables)
-- ============================================================
\echo 'S16: Nozzle inventory...'

-- Helper: unpivot meter-wise readings into (date, nozzle_name, close_reading)
-- then use LAG() to derive open_reading and sales

CREATE TEMP TABLE _nozzle_readings (
    reading_date DATE NOT NULL,
    nozzle_name VARCHAR(10) NOT NULL,
    close_meter_reading DOUBLE PRECISION NOT NULL
);

-- MS nozzles from e_book_mw_ms
INSERT INTO _nozzle_readings (reading_date, nozzle_name, close_meter_reading)
SELECT mw_ms_edate, 'N-36', mw_ms_en1 FROM raw.e_book_mw_ms WHERE mw_ms_en1 > 0
UNION ALL
SELECT mw_ms_edate, 'N-37', mw_ms_en2 FROM raw.e_book_mw_ms WHERE mw_ms_en2 > 0
UNION ALL
SELECT mw_ms_edate, 'N-49', mw_ms_en4 FROM raw.e_book_mw_ms WHERE mw_ms_en4 > 0
UNION ALL
SELECT mw_ms_edate, 'N-47', mw_ms_en5 FROM raw.e_book_mw_ms WHERE mw_ms_en5 > 0;

-- XP nozzles from e_book_mw_xp
INSERT INTO _nozzle_readings (reading_date, nozzle_name, close_meter_reading)
SELECT mw_xp_edate, 'N-46', mw_xp_n2 FROM raw.e_book_mw_xp WHERE mw_xp_n2 > 0
UNION ALL
SELECT mw_xp_edate, 'N-48', mw_xp_n1 FROM raw.e_book_mw_xp WHERE mw_xp_n1 > 0;

-- HSD nozzles from e_book_mw_hsd_1
INSERT INTO _nozzle_readings (reading_date, nozzle_name, close_meter_reading)
SELECT mw_hsd_1_edate, 'N-39', mw_hsd_1_en1 FROM raw.e_book_mw_hsd_1 WHERE mw_hsd_1_en1 > 0
UNION ALL
SELECT mw_hsd_1_edate, 'N-38', mw_hsd_1_en2 FROM raw.e_book_mw_hsd_1 WHERE mw_hsd_1_en2 > 0
UNION ALL
SELECT mw_hsd_1_edate, 'N-53', mw_hsd_1_new_a2 FROM raw.e_book_mw_hsd_1 WHERE mw_hsd_1_new_a2 > 0;

-- HSD nozzle from e_book_mw_hsd_2
INSERT INTO _nozzle_readings (reading_date, nozzle_name, close_meter_reading)
SELECT mw_hsd_2_edate, 'N-52', mw_hsd_2_new_a1 FROM raw.e_book_mw_hsd_2 WHERE mw_hsd_2_new_a1 > 0;

-- N-50 and N-51 manual data (DU-16, MS — cumulative readings)
INSERT INTO _nozzle_readings (reading_date, nozzle_name, close_meter_reading) VALUES
('2025-05-08','N-50',463),('2025-05-08','N-51',438),
('2025-05-09','N-50',468),('2025-05-09','N-51',447),
('2025-05-10','N-50',483),('2025-05-10','N-51',462),
('2025-05-11','N-50',488),('2025-05-11','N-51',467),
('2025-05-12','N-50',493),('2025-05-12','N-51',487),
('2025-05-13','N-50',499),('2025-05-13','N-51',492),
('2025-05-14','N-50',504),('2025-05-14','N-51',500),
('2025-05-15','N-50',510),('2025-05-15','N-51',509),
('2025-05-16','N-50',554),('2025-05-16','N-51',519),
('2025-05-17','N-50',586),('2025-05-17','N-51',534),
('2025-05-18','N-50',611),('2025-05-18','N-51',549),
('2025-05-19','N-50',617),('2025-05-19','N-51',629),
('2025-05-20','N-50',639),('2025-05-20','N-51',756),
('2025-05-21','N-50',653),('2025-05-21','N-51',846),
('2025-05-22','N-50',671),('2025-05-22','N-51',983),
('2025-05-23','N-50',689),('2025-05-23','N-51',1138),
('2025-05-24','N-50',713),('2025-05-24','N-51',1314),
('2025-05-25','N-50',723),('2025-05-25','N-51',1516),
('2025-05-26','N-50',737),('2025-05-26','N-51',1703),
('2025-05-27','N-50',747),('2025-05-27','N-51',1754),
('2025-05-28','N-50',762),('2025-05-28','N-51',1919),
('2025-05-29','N-50',805),('2025-05-29','N-51',2070),
('2025-05-30','N-50',824),('2025-05-30','N-51',2186),
('2025-05-31','N-50',901),('2025-05-31','N-51',2323),
('2025-06-01','N-50',926),('2025-06-01','N-51',2455),
('2025-06-02','N-50',932),('2025-06-02','N-51',2465),
('2025-06-03','N-50',949),('2025-06-03','N-51',2651),
('2025-06-04','N-50',964),('2025-06-04','N-51',2812),
('2025-06-05','N-50',983),('2025-06-05','N-51',2957),
('2025-06-06','N-50',1028),('2025-06-06','N-51',3124),
('2025-06-07','N-50',1098),('2025-06-07','N-51',3235),
('2025-06-08','N-50',1175),('2025-06-08','N-51',3507),
('2025-06-09','N-50',1207),('2025-06-09','N-51',3563),
('2025-06-10','N-50',1224),('2025-06-10','N-51',3639),
('2025-06-11','N-50',1327),('2025-06-11','N-51',3659),
('2025-06-12','N-50',1428),('2025-06-12','N-51',3718),
('2025-06-13','N-50',1470),('2025-06-13','N-51',3859),
('2025-06-14','N-50',1528),('2025-06-14','N-51',3969),
('2025-06-15','N-50',1594),('2025-06-15','N-51',4112),
('2025-06-16','N-50',1608),('2025-06-16','N-51',4257),
('2025-06-17','N-50',1647),('2025-06-17','N-51',4394),
('2025-06-18','N-50',1659),('2025-06-18','N-51',4428),
('2025-06-19','N-50',1691),('2025-06-19','N-51',4602),
('2025-06-20','N-50',1728),('2025-06-20','N-51',4758),
('2025-06-21','N-50',1770),('2025-06-21','N-51',4887),
('2025-06-22','N-50',1805),('2025-06-22','N-51',5048),
('2025-06-23','N-50',1880),('2025-06-23','N-51',5132),
('2025-06-24','N-50',1897),('2025-06-24','N-51',5291),
('2025-06-25','N-50',1952),('2025-06-25','N-51',5444),
('2025-06-26','N-50',1995),('2025-06-26','N-51',5545),
('2025-06-27','N-50',2008),('2025-06-27','N-51',5687),
('2025-06-28','N-50',2094),('2025-06-28','N-51',5837),
('2025-06-29','N-50',2168),('2025-06-29','N-51',5960),
('2025-06-30','N-50',2260),('2025-06-30','N-51',6020),
('2025-07-01','N-50',2343),('2025-07-01','N-51',6154),
('2025-07-02','N-50',2360),('2025-07-02','N-51',6232),
('2025-07-03','N-50',2413),('2025-07-03','N-51',6474),
('2025-07-04','N-50',2426),('2025-07-04','N-51',6609),
('2025-07-05','N-50',2456),('2025-07-05','N-51',6715),
('2025-07-06','N-50',2554),('2025-07-06','N-51',6846),
('2025-07-07','N-50',2575),('2025-07-07','N-51',7035),
('2025-07-08','N-50',2660),('2025-07-08','N-51',7266),
('2025-07-09','N-50',2711),('2025-07-09','N-51',7389),
('2025-07-10','N-50',2759),('2025-07-10','N-51',7496),
('2025-07-11','N-50',2771),('2025-07-11','N-51',7533),
('2025-07-12','N-50',2782),('2025-07-12','N-51',7675),
('2025-07-13','N-50',2804),('2025-07-13','N-51',7825),
('2025-07-14','N-50',2847),('2025-07-14','N-51',7982),
('2025-07-15','N-50',2874),('2025-07-15','N-51',8018),
('2025-07-16','N-50',2903),('2025-07-16','N-51',8201),
('2025-07-17','N-50',2950),('2025-07-17','N-51',8227),
('2025-07-18','N-50',3007),('2025-07-18','N-51',8386),
('2025-07-19','N-50',3027),('2025-07-19','N-51',8631),
('2025-07-20','N-50',3049),('2025-07-20','N-51',8804),
('2025-07-21','N-50',3104),('2025-07-21','N-51',9000),
('2025-07-22','N-50',3152),('2025-07-22','N-51',9130),
('2025-07-23','N-50',3187),('2025-07-23','N-51',9320),
('2025-07-24','N-50',3264),('2025-07-24','N-51',9392),
('2025-07-25','N-50',3346),('2025-07-25','N-51',9437),
('2025-07-26','N-50',3368),('2025-07-26','N-51',9569),
('2025-07-27','N-50',3398),('2025-07-27','N-51',9792),
('2025-07-28','N-50',3414),('2025-07-28','N-51',9841),
('2025-07-29','N-50',3451),('2025-07-29','N-51',9945),
('2025-07-30','N-50',3516),('2025-07-30','N-51',10082),
('2025-07-31','N-50',3532),('2025-07-31','N-51',10096),
('2025-08-01','N-50',3563),('2025-08-01','N-51',10216),
('2025-08-02','N-50',3630),('2025-08-02','N-51',10380),
('2025-08-03','N-50',3680),('2025-08-03','N-51',10510),
('2025-08-04','N-50',3718),('2025-08-04','N-51',10699),
('2025-08-05','N-50',3764),('2025-08-05','N-51',10708),
('2025-08-06','N-50',3789),('2025-08-06','N-51',10877),
('2025-08-07','N-50',3875),('2025-08-07','N-51',11076),
('2025-08-08','N-50',3928),('2025-08-08','N-51',11204),
('2025-08-09','N-50',4029),('2025-08-09','N-51',11402),
('2025-08-10','N-50',4044),('2025-08-10','N-51',11579),
('2025-08-11','N-50',4081),('2025-08-11','N-51',11823),
('2025-08-12','N-50',4098),('2025-08-12','N-51',12000),
('2025-08-13','N-50',4147),('2025-08-13','N-51',12110),
('2025-08-14','N-50',4163),('2025-08-14','N-51',12264),
('2025-08-15','N-50',4200),('2025-08-15','N-51',12402),
('2025-08-16','N-50',4230),('2025-08-16','N-51',12522),
('2025-08-17','N-50',4285),('2025-08-17','N-51',12539),
('2025-08-18','N-50',4309),('2025-08-18','N-51',12663),
('2025-08-19','N-50',4365),('2025-08-19','N-51',12851),
('2025-08-20','N-50',4395),('2025-08-20','N-51',12978),
('2025-08-21','N-50',4451),('2025-08-21','N-51',13119),
('2025-08-22','N-50',4482),('2025-08-22','N-51',13212),
('2025-08-23','N-50',4603),('2025-08-23','N-51',13376),
('2025-08-24','N-50',4639),('2025-08-24','N-51',13450),
('2025-08-25','N-50',4644),('2025-08-25','N-51',13624),
('2025-08-26','N-50',4649),('2025-08-26','N-51',13758),
('2025-08-27','N-50',4654),('2025-08-27','N-51',13957),
('2025-08-28','N-50',4659),('2025-08-28','N-51',14186),
('2025-08-29','N-50',4667),('2025-08-29','N-51',14377),
('2025-08-30','N-50',4672),('2025-08-30','N-51',14509),
('2025-08-31','N-50',4682),('2025-08-31','N-51',14694),
('2025-09-01','N-50',4687),('2025-09-01','N-51',14907),
('2025-09-02','N-50',4698),('2025-09-02','N-51',15099),
('2025-09-03','N-50',4706),('2025-09-03','N-51',15256),
('2025-09-04','N-50',4721),('2025-09-04','N-51',15419),
('2025-09-05','N-50',4779),('2025-09-05','N-51',15581),
('2025-09-06','N-50',4807),('2025-09-06','N-51',15732),
('2025-09-07','N-50',4841),('2025-09-07','N-51',15907),
('2025-09-08','N-50',4847),('2025-09-08','N-51',16080),
('2025-09-09','N-50',4915),('2025-09-09','N-51',16173),
('2025-09-10','N-50',4925),('2025-09-10','N-51',16308),
('2025-09-11','N-50',4962),('2025-09-11','N-51',16514),
('2025-09-12','N-50',4980),('2025-09-12','N-51',16662),
('2025-09-13','N-50',4993),('2025-09-13','N-51',16831),
('2025-09-14','N-50',5008),('2025-09-14','N-51',16983),
('2025-09-15','N-50',5037),('2025-09-15','N-51',17086),
('2025-09-16','N-50',5054),('2025-09-16','N-51',17183),
('2025-09-17','N-50',5087),('2025-09-17','N-51',17288),
('2025-09-18','N-50',5135),('2025-09-18','N-51',17444),
('2025-09-19','N-50',5222),('2025-09-19','N-51',17535),
('2025-09-20','N-50',5276),('2025-09-20','N-51',17651),
('2025-09-21','N-50',5286),('2025-09-21','N-51',17776),
('2025-09-22','N-50',5336),('2025-09-22','N-51',17960),
('2025-09-23','N-50',5381),('2025-09-23','N-51',18164),
('2025-09-24','N-50',5402),('2025-09-24','N-51',18314),
('2025-09-25','N-50',5457),('2025-09-25','N-51',18435),
('2025-09-26','N-50',5474),('2025-09-26','N-51',18530),
('2025-09-27','N-50',5513),('2025-09-27','N-51',18647),
('2025-09-28','N-50',5558),('2025-09-28','N-51',18763),
('2025-09-29','N-50',5622),('2025-09-29','N-51',18878),
('2025-09-30','N-50',5689),('2025-09-30','N-51',18930),
('2025-10-01','N-50',5763),('2025-10-01','N-51',19039),
('2025-10-02','N-50',5810),('2025-10-02','N-51',19168),
('2025-10-03','N-50',5870),('2025-10-03','N-51',19284),
('2025-10-04','N-50',5957),('2025-10-04','N-51',19383),
('2025-10-05','N-50',6000),('2025-10-05','N-51',19531),
('2025-10-06','N-50',6022),('2025-10-06','N-51',19705),
('2025-10-07','N-50',6055),('2025-10-07','N-51',19861),
('2025-10-08','N-50',6079),('2025-10-08','N-51',19998),
('2025-10-09','N-50',6101),('2025-10-09','N-51',20119),
('2025-10-10','N-50',6111),('2025-10-10','N-51',20244),
('2025-10-11','N-50',6157),('2025-10-11','N-51',20402),
('2025-10-12','N-50',6170),('2025-10-12','N-51',20559),
('2025-10-13','N-50',6204),('2025-10-13','N-51',20724),
('2025-10-14','N-50',6224),('2025-10-14','N-51',20829),
('2025-10-15','N-50',6255),('2025-10-15','N-51',20925),
('2025-10-16','N-50',6311),('2025-10-16','N-51',20997),
('2025-10-17','N-50',6400),('2025-10-17','N-51',21087),
('2025-10-18','N-50',6431),('2025-10-18','N-51',21176),
('2025-10-19','N-50',6449),('2025-10-19','N-51',21407),
('2025-10-20','N-50',6518),('2025-10-20','N-51',21564),
('2025-10-21','N-50',6524),('2025-10-21','N-51',21659),
('2025-10-22','N-50',6573),('2025-10-22','N-51',21788),
('2025-10-23','N-50',6624),('2025-10-23','N-51',21916),
('2025-10-24','N-50',6737),('2025-10-24','N-51',22053),
('2025-10-25','N-50',6747),('2025-10-25','N-51',22282),
('2025-10-26','N-50',6793),('2025-10-26','N-51',22377),
('2025-10-27','N-50',6840),('2025-10-27','N-51',22537),
('2025-10-28','N-50',6862),('2025-10-28','N-51',22631),
('2025-10-29','N-50',6867),('2025-10-29','N-51',22645),
('2025-10-30','N-50',6919),('2025-10-30','N-51',22827),
('2025-10-31','N-50',6933),('2025-10-31','N-51',23036),
('2025-11-01','N-50',6950),('2025-11-01','N-51',23089),
('2025-11-02','N-50',7021),('2025-11-02','N-51',23256),
('2025-11-03','N-50',7037),('2025-11-03','N-51',23426),
('2025-11-04','N-50',7082),('2025-11-04','N-51',23606),
('2025-11-05','N-50',7105),('2025-11-05','N-51',23732),
('2025-11-06','N-50',7153),('2025-11-06','N-51',23892),
('2025-11-07','N-50',7170),('2025-11-07','N-51',24030),
('2025-11-08','N-50',7252),('2025-11-08','N-51',24168),
('2025-11-09','N-50',7399),('2025-11-09','N-51',24178),
('2025-11-10','N-50',7432),('2025-11-10','N-51',24357),
('2025-11-11','N-50',7491),('2025-11-11','N-51',24483),
('2025-11-12','N-50',7535),('2025-11-12','N-51',24659),
('2025-11-13','N-50',7573),('2025-11-13','N-51',24772),
('2025-11-14','N-50',7680),('2025-11-14','N-51',24950),
('2025-11-15','N-50',7707),('2025-11-15','N-51',25057),
('2025-11-16','N-50',7742),('2025-11-16','N-51',25385),
('2025-11-17','N-50',7768),('2025-11-17','N-51',25548),
('2025-11-18','N-50',7794),('2025-11-18','N-51',25689),
('2025-11-19','N-50',7822),('2025-11-19','N-51',25882),
('2025-11-20','N-50',7968),('2025-11-20','N-51',25957),
('2025-11-21','N-50',7994),('2025-11-21','N-51',26069),
('2025-11-22','N-50',8030),('2025-11-22','N-51',26256),
('2025-11-23','N-50',8061),('2025-11-23','N-51',26389),
('2025-11-24','N-50',8096),('2025-11-24','N-51',26599),
('2025-11-25','N-50',8128),('2025-11-25','N-51',26712),
('2025-11-26','N-50',8233),('2025-11-26','N-51',26859),
('2025-11-27','N-50',8301),('2025-11-27','N-51',26901),
('2025-11-28','N-50',8342),('2025-11-28','N-51',27027),
('2025-11-29','N-50',8428),('2025-11-29','N-51',27203),
('2025-11-30','N-50',8508),('2025-11-30','N-51',27344),
('2025-12-01','N-50',8525),('2025-12-01','N-51',27452),
('2025-12-02','N-50',8563),('2025-12-02','N-51',27650),
('2025-12-03','N-50',8706),('2025-12-03','N-51',27710),
('2025-12-04','N-50',8732),('2025-12-04','N-51',27879),
('2025-12-05','N-50',8796),('2025-12-05','N-51',28005),
('2025-12-06','N-50',8851),('2025-12-06','N-51',28187),
('2025-12-07','N-50',8886),('2025-12-07','N-51',28310),
('2025-12-08','N-50',8940),('2025-12-08','N-51',28470),
('2025-12-09','N-50',8962),('2025-12-09','N-51',28585),
('2025-12-10','N-50',8986),('2025-12-10','N-51',28872),
('2025-12-11','N-50',9065),('2025-12-11','N-51',29011),
('2025-12-12','N-50',9087),('2025-12-12','N-51',29129),
('2025-12-13','N-50',9111),('2025-12-13','N-51',29172),
('2025-12-14','N-50',9190),('2025-12-14','N-51',29372),
('2025-12-15','N-50',9205),('2025-12-15','N-51',29501),
('2025-12-16','N-50',9233),('2025-12-16','N-51',29639),
('2025-12-17','N-50',9238),('2025-12-17','N-51',29671),
('2025-12-18','N-50',9326),('2025-12-18','N-51',29844),
('2025-12-19','N-50',9362),('2025-12-19','N-51',29978),
('2025-12-20','N-50',9414),('2025-12-20','N-51',30126),
('2025-12-21','N-50',9432),('2025-12-21','N-51',30285),
('2025-12-22','N-50',9513),('2025-12-22','N-51',30422),
('2025-12-23','N-50',9522),('2025-12-23','N-51',30536),
('2025-12-24','N-50',9591),('2025-12-24','N-51',30735),
('2025-12-25','N-50',9623),('2025-12-25','N-51',30869),
('2025-12-26','N-50',9702),('2025-12-26','N-51',31005),
('2025-12-27','N-50',9752),('2025-12-27','N-51',31101),
('2025-12-28','N-50',9800),('2025-12-28','N-51',31284),
('2025-12-29','N-50',9828),('2025-12-29','N-51',31430),
('2025-12-30','N-50',9863),('2025-12-30','N-51',31614),
('2025-12-31','N-50',9892),('2025-12-31','N-51',31775),
('2026-01-01','N-50',9916),('2026-01-01','N-51',31795),
('2026-01-02','N-50',9958),('2026-01-02','N-51',31822),
('2026-01-03','N-50',9981),('2026-01-03','N-51',31891),
('2026-01-04','N-50',10020),('2026-01-04','N-51',32016),
('2026-01-05','N-50',10054),('2026-01-05','N-51',32195),
('2026-01-06','N-50',10077),('2026-01-06','N-51',32310),
('2026-01-07','N-50',10130),('2026-01-07','N-51',32444),
('2026-01-08','N-50',10185),('2026-01-08','N-51',32702),
('2026-01-09','N-50',10281),('2026-01-09','N-51',32927),
('2026-01-10','N-50',10326),('2026-01-10','N-51',33098),
('2026-01-11','N-50',10364),('2026-01-11','N-51',33300),
('2026-01-12','N-50',10378),('2026-01-12','N-51',33486),
('2026-01-13','N-50',10445),('2026-01-13','N-51',33610),
('2026-01-14','N-50',10499),('2026-01-14','N-51',33789),
('2026-01-15','N-50',10518),('2026-01-15','N-51',34052),
('2026-01-16','N-50',10541),('2026-01-16','N-51',34158),
('2026-01-17','N-50',10564),('2026-01-17','N-51',34338),
('2026-01-18','N-50',10647),('2026-01-18','N-51',34530),
('2026-01-19','N-50',10674),('2026-01-19','N-51',34768),
('2026-01-20','N-50',10758),('2026-01-20','N-51',34908),
('2026-01-21','N-50',10805),('2026-01-21','N-51',35024),
('2026-01-22','N-50',10879),('2026-01-22','N-51',35143),
('2026-01-23','N-50',10930),('2026-01-23','N-51',35294),
('2026-01-24','N-50',10961),('2026-01-24','N-51',35375),
('2026-01-25','N-50',10981),('2026-01-25','N-51',35596),
('2026-01-26','N-50',11000),('2026-01-26','N-51',35810),
('2026-01-27','N-50',11031),('2026-01-27','N-51',36063),
('2026-01-28','N-50',11057),('2026-01-28','N-51',36103),
('2026-01-29','N-50',11113),('2026-01-29','N-51',36222),
('2026-01-30','N-50',11135),('2026-01-30','N-51',36362),
('2026-01-31','N-50',11171),('2026-01-31','N-51',36520),
('2026-02-01','N-50',11189),('2026-02-01','N-51',36655),
('2026-02-02','N-50',11222),('2026-02-02','N-51',36789),
('2026-02-03','N-50',11234),('2026-02-03','N-51',36951),
('2026-02-04','N-50',11258),('2026-02-04','N-51',37106),
('2026-02-05','N-50',11307),('2026-02-05','N-51',37323),
('2026-02-06','N-50',11361),('2026-02-06','N-51',37417),
('2026-02-07','N-50',11427),('2026-02-07','N-51',37561),
('2026-02-08','N-50',11510),('2026-02-08','N-51',37695),
('2026-02-09','N-50',11556),('2026-02-09','N-51',37835),
('2026-02-10','N-50',11571),('2026-02-10','N-51',37999),
('2026-02-11','N-50',11584),('2026-02-11','N-51',38103),
('2026-02-12','N-50',11629),('2026-02-12','N-51',38279),
('2026-02-13','N-50',11644),('2026-02-13','N-51',38393),
('2026-02-14','N-50',11691),('2026-02-14','N-51',38518),
('2026-02-15','N-50',11756),('2026-02-15','N-51',38557),
('2026-02-16','N-50',11770),('2026-02-16','N-51',38771),
('2026-02-17','N-50',11792),('2026-02-17','N-51',38899),
('2026-02-18','N-50',11846),('2026-02-18','N-51',39021),
('2026-02-19','N-50',11882),('2026-02-19','N-51',39187),
('2026-02-20','N-50',11901),('2026-02-20','N-51',39348),
('2026-02-21','N-50',11925),('2026-02-21','N-51',39464),
('2026-02-22','N-50',11988),('2026-02-22','N-51',39597),
('2026-02-23','N-50',12007),('2026-02-23','N-51',39764),
('2026-02-24','N-50',12019),('2026-02-24','N-51',39991),
('2026-02-25','N-50',12050),('2026-02-25','N-51',40156),
('2026-02-26','N-50',12093),('2026-02-26','N-51',40322),
('2026-02-27','N-50',12147),('2026-02-27','N-51',40457),
('2026-02-28','N-50',12215),('2026-02-28','N-51',40554),
('2026-03-01','N-50',12234),('2026-03-01','N-51',40796),
('2026-03-02','N-50',12271),('2026-03-02','N-51',40898),
('2026-03-03','N-50',12291),('2026-03-03','N-51',41002),
('2026-03-04','N-50',12359),('2026-03-04','N-51',41133),
('2026-03-05','N-50',12402),('2026-03-05','N-51',41284),
('2026-03-06','N-50',12425),('2026-03-06','N-51',41427),
('2026-03-07','N-50',12468),('2026-03-07','N-51',41562),
('2026-03-08','N-50',12506),('2026-03-08','N-51',41688),
('2026-03-09','N-50',12533),('2026-03-09','N-51',41894),
('2026-03-10','N-50',12552),('2026-03-10','N-51',42056),
('2026-03-11','N-50',12582),('2026-03-11','N-51',42222),
('2026-03-12','N-50',12616),('2026-03-12','N-51',42367),
('2026-03-13','N-50',12624),('2026-03-13','N-51',42445),
('2026-03-14','N-50',12637),('2026-03-14','N-51',42668),
('2026-03-15','N-50',12713),('2026-03-15','N-51',42890),
('2026-03-16','N-50',12718),('2026-03-16','N-51',43062),
('2026-03-17','N-50',12748),('2026-03-17','N-51',43146),
('2026-03-18','N-50',12761),('2026-03-18','N-51',43290),
('2026-03-19','N-50',12816),('2026-03-19','N-51',43398),
('2026-03-20','N-50',12852),('2026-03-20','N-51',43520),
('2026-03-21','N-50',12884),('2026-03-21','N-51',43677),
('2026-03-22','N-50',12884),('2026-03-22','N-51',43891),
('2026-03-23','N-50',12884),('2026-03-23','N-51',44067),
('2026-03-24','N-50',12902),('2026-03-24','N-51',44250),
('2026-03-25','N-50',12922),('2026-03-25','N-51',44511),
('2026-03-26','N-50',12960),('2026-03-26','N-51',44578),
('2026-03-27','N-50',12980),('2026-03-27','N-51',44662),
('2026-03-28','N-50',12988),('2026-03-28','N-51',44902),
('2026-03-29','N-50',13022),('2026-03-29','N-51',45104),
('2026-03-30','N-50',13048),('2026-03-30','N-51',45245),
('2026-03-31','N-50',13107),('2026-03-31','N-51',45367),
('2026-04-01','N-50',13131),('2026-04-01','N-51',45539),
('2026-04-02','N-50',13162),('2026-04-02','N-51',45794);

-- Transform cumulative readings into nozzle_inventory (open/close/sales via LAG)
INSERT INTO nozzle_inventory (scid, date, nozzle_id, open_meter_reading, close_meter_reading, sales, created_at, updated_at)
SELECT 1,
    r.reading_date,
    n.id,
    LAG(r.close_meter_reading) OVER (PARTITION BY r.nozzle_name ORDER BY r.reading_date),
    r.close_meter_reading,
    r.close_meter_reading - COALESCE(LAG(r.close_meter_reading) OVER (PARTITION BY r.nozzle_name ORDER BY r.reading_date), r.close_meter_reading),
    NOW(), NOW()
FROM _nozzle_readings r
JOIN nozzle n ON n.nozzle_name = r.nozzle_name
WHERE NOT EXISTS (SELECT 1 FROM nozzle_inventory LIMIT 1)
ORDER BY r.nozzle_name, r.reading_date;

SELECT setval('nozzle_inventory_id_seq', (SELECT COALESCE(MAX(id),1) FROM nozzle_inventory));

DROP TABLE IF EXISTS _nozzle_readings;

-- ============================================================
-- VERIFICATION
-- ============================================================
\echo '=============================='
\echo 'VERIFICATION'
\echo '=============================='

SELECT '--- MASTER ---' AS section, '' AS count
UNION ALL SELECT 'customers', COUNT(*)::text FROM customer
UNION ALL SELECT 'employees', COUNT(*)::text FROM employees
UNION ALL SELECT 'products', COUNT(*)::text FROM product
UNION ALL SELECT 'vehicles', COUNT(*)::text FROM vehicle
UNION ALL SELECT '--- INVOICES ---', ''
UNION ALL SELECT 'invoice_bill (CREDIT)', COUNT(*)::text FROM invoice_bill WHERE bill_type='CREDIT'
UNION ALL SELECT 'invoice_bill (CASH)', COUNT(*)::text FROM invoice_bill WHERE bill_type='CASH'
UNION ALL SELECT 'invoice_product', COUNT(*)::text FROM invoice_product
UNION ALL SELECT 'NULL customer (should be 0)', COUNT(*)::text FROM invoice_bill WHERE bill_type='CREDIT' AND customer_id IS NULL
UNION ALL SELECT 'NULL product_id (should be 0)', COUNT(*)::text FROM invoice_product WHERE product_id IS NULL
UNION ALL SELECT '--- PAYMENTS ---', ''
UNION ALL SELECT 'statements', COUNT(*)::text FROM statement
UNION ALL SELECT 'bills→statements', COUNT(*)::text FROM invoice_bill WHERE statement_id IS NOT NULL
UNION ALL SELECT 'payments (bill)', COUNT(*)::text FROM payment WHERE invoice_bill_id IS NOT NULL
UNION ALL SELECT 'payments (statement)', COUNT(*)::text FROM payment WHERE statement_id IS NOT NULL AND invoice_bill_id IS NULL
UNION ALL SELECT '--- ADVANCES ---', ''
UNION ALL SELECT 'e_advance', COUNT(*)::text FROM e_advance
UNION ALL SELECT 'operational_advance', COUNT(*)::text FROM operational_advance
UNION ALL SELECT '--- SHIFTS ---', ''
UNION ALL SELECT 'shifts', COUNT(*)::text FROM shifts
UNION ALL SELECT '--- INCENTIVES ---', ''
UNION ALL SELECT 'incentive_payment (linked)', COUNT(*)::text FROM incentive_payment WHERE invoice_bill_id IS NOT NULL
UNION ALL SELECT 'incentive_payment (unlinked)', COUNT(*)::text FROM incentive_payment WHERE invoice_bill_id IS NULL
UNION ALL SELECT '--- EXPENSES ---', ''
UNION ALL SELECT 'expense_types', COUNT(*)::text FROM expense_type
UNION ALL SELECT 'expenses', COUNT(*)::text FROM expense
UNION ALL SELECT '--- PRICE HISTORY ---', ''
UNION ALL SELECT 'product_price_history', COUNT(*)::text FROM product_price_history
UNION ALL SELECT '--- INVENTORY ---', ''
UNION ALL SELECT 'tank_inventory', COUNT(*)::text FROM tank_inventory
UNION ALL SELECT 'nozzle_inventory', COUNT(*)::text FROM nozzle_inventory
UNION ALL SELECT '--- SETUP ---', ''
UNION ALL SELECT 'tanks', COUNT(*)::text FROM tank
UNION ALL SELECT 'pumps', COUNT(*)::text FROM pump
UNION ALL SELECT 'nozzles', COUNT(*)::text FROM nozzle;

\echo ''
\echo '--- PAYMENT STATUS DISTRIBUTION ---'
SELECT payment_status, COUNT(*) FROM invoice_bill WHERE bill_type='CREDIT' GROUP BY payment_status ORDER BY payment_status;
SELECT status, COUNT(*) FROM statement GROUP BY status ORDER BY status;

\echo ''
\echo '--- ENRICHMENT COVERAGE ---'
SELECT 'payments_with_reference' AS metric, COUNT(*)::text FROM payment WHERE reference_no IS NOT NULL
UNION ALL SELECT 'payments_with_remarks', COUNT(*)::text FROM payment WHERE remarks IS NOT NULL
UNION ALL SELECT 'payments_with_received_by', COUNT(*)::text FROM payment WHERE received_by_id IS NOT NULL;

\echo ''
\echo '--- INTEGRITY CHECKS ---'
SELECT 'stmt-bills with own payments (expect 0)' AS check, COUNT(DISTINCT p.invoice_bill_id)::text
FROM payment p JOIN invoice_bill ib ON p.invoice_bill_id=ib.id WHERE ib.statement_id IS NOT NULL;

\echo ''
\echo '--- AMOUNT RECONCILIATION ---'
SELECT 'bill_payment_total' AS metric, SUM(amount)::numeric(19,2)::text FROM payment WHERE invoice_bill_id IS NOT NULL
UNION ALL SELECT 'stmt_payment_total', SUM(amount)::numeric(19,2)::text FROM payment WHERE statement_id IS NOT NULL
UNION ALL SELECT 'stmt_net_total', SUM(net_amount)::numeric(19,2)::text FROM statement
UNION ALL SELECT 'credit_bill_net_total', SUM(net_amount)::numeric(19,2)::text FROM invoice_bill WHERE bill_type='CREDIT';

\echo '=============================='
\echo 'MIGRATION ENGINE — COMPLETE'
\echo '=============================='
