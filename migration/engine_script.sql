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
        -- Products & tanks
        'product_names', 'tank_inventory',
        -- Invoices
        'creditbill_test', 'creditbill_products_test', 'cashbill', 'cashbill_products',
        -- Statements & payments
        'credit_statement', 'statement_association_1',
        'incomebill', 'intermediatebills',
        'income_creditbill_statement', 'intermediate_creditbill_statement',
        -- Payment details (for enrichment)
        'cheque_incomebill', 'card_incomebill', 'ccms_incomebill', 'bank_transfer_incomebill',
        -- Advances & shifts
        'card_advances', 'cheque_advance', 'ccms_advance', 'bank_transfer_advance',
        'home_advances', 'cash_advances',
        'shift_closing_timings',
        -- Incentives
        'incentive_table'
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
        VALUES (v_id,(SELECT id FROM designations WHERE name=v_desig LIMIT 1),COALESCE(r.employee_salary,0),v_a,
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
SELECT 1,n,true,NOW(),NOW() FROM (VALUES ('Pump-1'),('Pump-2'),('Pump-3')) AS t(n)
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

INSERT INTO tank (scid,name,capacity,available_stock,product_id,active,created_at,updated_at)
SELECT 1,'Tank-1',12000,0,(SELECT id FROM product WHERE name='Petrol' LIMIT 1),true,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM tank WHERE name='Tank-1');
INSERT INTO tank (scid,name,capacity,available_stock,product_id,active,created_at,updated_at)
SELECT 1,'Tank-2',6000,0,(SELECT id FROM product WHERE name='Xtra Premium' LIMIT 1),true,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM tank WHERE name='Tank-2');
INSERT INTO tank (scid,name,capacity,available_stock,product_id,active,created_at,updated_at)
SELECT 1,'Tank-3',12000,0,(SELECT id FROM product WHERE name='Diesel' LIMIT 1),true,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM tank WHERE name='Tank-3');

INSERT INTO nozzle (scid,nozzle_name,tank_id,pump_id,active,created_at,updated_at)
SELECT 1,n.nn,t.id,p.id,true,NOW(),NOW() FROM (VALUES
    ('N-1','Tank-1','Pump-1'),('N-2','Tank-1','Pump-1'),('N-11','Tank-2','Pump-1'),('N-12','Tank-2','Pump-1'),
    ('N-3','Tank-1','Pump-2'),('N-4','Tank-1','Pump-2'),('N-9','Tank-3','Pump-2'),('N-10','Tank-3','Pump-2'),
    ('N-5','Tank-1','Pump-3'),('N-6','Tank-1','Pump-3'),('N-7','Tank-3','Pump-3'),('N-8','Tank-3','Pump-3')
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
UPDATE payment p SET reference_no=NULLIF(TRIM(cd.chequeno),''), remarks=NULLIF(TRIM(COALESCE(cd.chequebankname,'')||' '||COALESCE(cd.cheqcusname,'')),'')
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
UPDATE customer SET status = UPPER(TRIM(status)) WHERE status IS NOT NULL AND status != UPPER(TRIM(status));
UPDATE vehicle SET status = UPPER(TRIM(status)) WHERE status IS NOT NULL AND status != UPPER(TRIM(status));

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
UNION ALL SELECT 'incentive_payment (unlinked)', COUNT(*)::text FROM incentive_payment WHERE invoice_bill_id IS NULL;

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
