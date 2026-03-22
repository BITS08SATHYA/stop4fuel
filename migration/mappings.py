"""
Manual mappings between MySQL legacy data and PostgreSQL schema.
These require domain knowledge of the fuel station's physical layout and products.
"""

# MySQL oil table name -> (product_name, hsn_code, category, oil_type, grade, brand, volume, unit)
# This maps the ~42 individual MySQL product tables to normalized product records.
# category: FUEL, LUBRICANT, ACCESSORY, CONSUMABLE
OIL_TABLE_TO_PRODUCT = {
    # 2T oils (2-stroke engine oil)
    '2t_loose_oil':       ('2T Loose Oil', '27101980', 'LUBRICANT', 'Engine Oil', None, 'Servo', None, 'LITERS'),
    '2t_20ml_pouch':      ('2T 20ml Pouch', '27101980', 'LUBRICANT', 'Engine Oil', None, 'Servo', 0.02, 'PIECES'),
    '2t_40ml_pouch':      ('2T 40ml Pouch', '27101980', 'LUBRICANT', 'Engine Oil', None, 'Servo', 0.04, 'PIECES'),
    '2t_1by2_liter':      ('2T 1/2 Liter', '27101980', 'LUBRICANT', 'Engine Oil', None, 'Servo', 0.5, 'PIECES'),
    '2t_1_liter':         ('2T 1 Liter', '27101980', 'LUBRICANT', 'Engine Oil', None, 'Servo', 1.0, 'PIECES'),

    # 4T oils (4-stroke engine oil)
    '4t_1_liter':         ('4T 1 Liter', '27101980', 'LUBRICANT', 'Engine Oil', '20W-40', 'Servo', 1.0, 'PIECES'),
    '4toil_zoom':         ('4T Oil Zoom', '271019', 'LUBRICANT', 'Engine Oil', '20W-40', 'Servo', 1.0, 'PIECES'),
    '4toil_20w_50w':      ('4T Oil 20W-50', '271019', 'LUBRICANT', 'Engine Oil', '20W-50', 'Servo', 1.0, 'PIECES'),
    '4toil_josh_10w_30w': ('Servo Honda Josh 10W-30', '271019', 'LUBRICANT', 'Engine Oil', '10W-30', 'Servo', 1.0, 'PIECES'),
    '4txtra_20w_40_bs6':  ('4T Xtra 20W-40 BS6', '271019', 'LUBRICANT', 'Engine Oil', '20W-40', 'Servo', 1.0, 'PIECES'),

    # 15W-40 oils (heavy duty engine oil)
    '15_40_1_liter':      ('Pride Alt Plus 15W-40 (1L)', '27101980', 'LUBRICANT', 'Engine Oil', '15W-40', 'Servo', 1.0, 'PIECES'),
    '15_40_5_liter':      ('Pride Alt Plus 15W-40 (5L)', '27101980', 'LUBRICANT', 'Engine Oil', '15W-40', 'Servo', 5.0, 'PIECES'),
    '15_40_10_liter':     ('Pride Alt Plus 15W-40 (10L)', '27101980', 'LUBRICANT', 'Engine Oil', '15W-40', 'Servo', 10.0, 'PIECES'),
    'pride_alt_plus_15w_40': ('Pride Alt Plus 15W-40 (15L)', '27101980', 'LUBRICANT', 'Engine Oil', '15W-40', 'Servo', 15.0, 'PIECES'),

    # 20W-40 oils
    '20_40_1by2_liter':   ('20W-40 (1/2L)', '27101980', 'LUBRICANT', 'Engine Oil', '20W-40', 'Servo', 0.5, 'PIECES'),
    '20_40_1_liter':      ('20W-40 (1L)', '27101980', 'LUBRICANT', 'Engine Oil', '20W-40', 'Servo', 1.0, 'PIECES'),
    '20_40_5_liter':      ('20W-40 (5L)', '27101980', 'LUBRICANT', 'Engine Oil', '20W-40', 'Servo', 5.0, 'PIECES'),
    '20_40_10_liter':     ('20W-40 (10L)', '27101980', 'LUBRICANT', 'Engine Oil', '20W-40', 'Servo', 10.0, 'PIECES'),

    # Scooter oil
    'scootomatic_10w_30w': ('Scootomatic 10W-30 (800ml)', '271019', 'LUBRICANT', 'Engine Oil', '10W-30', 'Servo', 0.8, 'PIECES'),
    'tru4_kraaft_tvs':    ('Tru4 Kraaft TVS (900ml)', '271019', 'LUBRICANT', 'Engine Oil', '10W-30', 'Servo', 0.9, 'PIECES'),

    # Gear oils (90 grade)
    '90_1_liter':         ('Gear Oil 90 (1L)', '27101980', 'LUBRICANT', 'Gear Oil', '80W-90', 'Servo', 1.0, 'PIECES'),
    '90_5_liter':         ('Gear Oil 90 (5L)', '27101980', 'LUBRICANT', 'Gear Oil', '80W-90', 'Servo', 5.0, 'PIECES'),
    'gearoil_90_1_liter': ('Gear Oil 90 (1L)', '27101980', 'LUBRICANT', 'Gear Oil', '80W-90', 'Servo', 1.0, 'PIECES'),  # duplicate, same product

    # Gear oils (140 grade)
    '140_1_liter':        ('Gear Oil 140 (1L)', '27101980', 'LUBRICANT', 'Gear Oil', '140', 'Servo', 1.0, 'PIECES'),
    '140_5_liter':        ('Gear Oil 140 (5L)', '27101980', 'LUBRICANT', 'Gear Oil', '140', 'Servo', 5.0, 'PIECES'),

    # Grease
    'rr3_gem_1by2_kg':    ('RR3 Gem Grease (1/2 Kg)', '27101990', 'LUBRICANT', 'Grease', None, 'Servo', 0.5, 'PIECES'),
    'rr3_gem_1_kg':       ('RR3 Gem Grease (1 Kg)', '27101990', 'LUBRICANT', 'Grease', None, 'Servo', 1.0, 'PIECES'),
    'rr3_gem_3_kg':       ('RR3 Gem Grease (3 Kg)', '27101990', 'LUBRICANT', 'Grease', None, 'Servo', 3.0, 'PIECES'),

    # Coolant
    'kool_plus_1by2_liter': ('Kool Plus Coolant (1/2L)', '38200000', 'LUBRICANT', 'Coolant', None, 'Servo', 0.5, 'PIECES'),
    'kool_plus_1_liter':    ('Kool Plus Coolant (1L)', '38200000', 'LUBRICANT', 'Coolant', None, 'Servo', 1.0, 'PIECES'),

    # Brake fluid
    'break_oil_1by2_liter': ('Brake Oil (1/2L)', '28190010', 'LUBRICANT', 'Brake Fluid', None, 'Servo', 0.5, 'PIECES'),

    # Additives
    'adon_petrol':        ('Adon Petrol Additive', '38119000', 'CONSUMABLE', None, None, 'Servo', None, 'PIECES'),
    'adon_diesel':        ('Adon Diesel Additive', '38119000', 'CONSUMABLE', None, None, 'Servo', None, 'PIECES'),

    # Consumables
    'distel_water':       ('Distilled Water', '28530010', 'CONSUMABLE', None, None, 'Generic', None, 'PIECES'),
    'acid':               ('Battery Acid', '28111990', 'CONSUMABLE', None, None, 'Generic', None, 'PIECES'),
    'acv':                ('ACV', '0', 'CONSUMABLE', None, None, 'Generic', None, 'PIECES'),
    'yellow_cloth':       ('Yellow Cloth', '0', 'ACCESSORY', None, None, 'Generic', None, 'PIECES'),
    'blue_cloth':         ('Blue Cloth', '0', 'ACCESSORY', None, None, 'Generic', None, 'PIECES'),

    # Industrial oils
    'servo_68_26_lit':    ('Servo 68 (26L)', '271019', 'LUBRICANT', 'Engine Oil', None, 'Servo', 26.0, 'PIECES'),
    'clear_blue_10l':     ('Clear Blue (10L)', '31021000', 'CONSUMABLE', None, None, 'Clear Blue', 10.0, 'PIECES'),
    'clear_blue_20l':     ('Clear Blue (20L)', '0', 'CONSUMABLE', None, None, 'Clear Blue', 20.0, 'PIECES'),
    'tata_genuine_20l':   ('Tata Genuine Oil (20L)', '0', 'LUBRICANT', 'Engine Oil', None, 'Tata', 20.0, 'PIECES'),
}

# MySQL product table Product_Name -> MySQL table name for inventory lookup
MYSQL_PRODUCT_NAME_TO_TABLE = {
    'PETROL': None,  # Fuel, not an oil table
    'XTRA PREMIUM': None,
    'DIESEL': None,
    '2T LOOSE OIL(18%)': '2t_loose_oil',
    '2T 40ML POUCH(18%)': '2t_40ml_pouch',
    '2T 1/2 LITER(18%)': '2t_1by2_liter',
    '2T 1 LITER(18%)': '2t_1_liter',
    '4T 1 LITER(18%)': '4t_1_liter',
    '15/40 1 LITER(18%)': '15_40_1_liter',
    '15/40 5 LITER(18%)': '15_40_5_liter',
    '15/40 10 LITER(18%)': '15_40_10_liter',
    '20/40 1/2 LITER(18%)': '20_40_1by2_liter',
    '20/40 1 LITER(18%)': '20_40_1_liter',
    '20/40 5 LITER(18%)': '20_40_5_liter',
    '20/40 10 LITER(18%)': '20_40_10_liter',
    '90 1 LITER(18%)': '90_1_liter',
    '90 5 LITER(18%)': '90_5_liter',
    '140 1 LITER(18%)': '140_1_liter',
    '140 5 LITER(18%)': '140_5_liter',
    'RR3 GEM 1/2 KG(18%)': 'rr3_gem_1by2_kg',
    'RR3 GEM 1 KG(18%)': 'rr3_gem_1_kg',
    'ADON PETROL(18%)': 'adon_petrol',
    'ADON DIESEL(18%)': 'adon_diesel',
    'KOOL PLUS 1/2 L(28%)': 'kool_plus_1by2_liter',
    'KOOL PLUS 1 L(28%)': 'kool_plus_1_liter',
    'BREAK OIL 1/2 L(28%)': 'break_oil_1by2_liter',
    'D/ WATER': 'distel_water',
    'ACID': 'acid',
    'YELLOW CLOTH': 'yellow_cloth',
    'BLUE CLOTH': 'blue_cloth',
}

# MySQL vehicle type -> PostgreSQL vehicle_type type_name
VEHICLE_TYPE_MAP = {
    'Two-Wheeler': 'Bike',
    'Car': 'Car',
    'Auto': 'Auto',
    'Mini Auto': 'Auto',
    'Van': 'Van',
    'Spare Bus': 'Bus',
    'Line Bus': 'Bus',
    'Mini Bus': 'Bus',
    'Lorry': 'Truck',
    'Trailer': 'Truck',
    'Tanker': 'Truck',
    'JCB': 'JCB',
    'Genset': 'Genset',
    'Tractor': 'Tractor',
    'Tipper': 'Truck',
}

# MySQL designation -> PostgreSQL designation name
DESIGNATION_MAP = {
    'PETROL CASHIER': 'Cashier',
    'CASHIER': 'Cashier',
    'PUMP BOY': 'Pump Attendant',
    'PUMP ATTENDANT': 'Pump Attendant',
    'ATTENDANT': 'Attendant',
    'MANAGER': 'Manager',
    'SUPERVISOR': 'Supervisor',
    'ADMIN': 'Manager',
    'SECURITY': 'Security',
    'HELPER': 'Attendant',
    'CLEANER': 'Attendant',
}

# MySQL party type (with possible trailing spaces) -> PostgreSQL party_type
PARTY_TYPE_MAP = {
    'Local': 'Local',
    'Local ': 'Local',
    'Statement': 'Statement',
    'Statement ': 'Statement',
}

# MySQL bill product names -> fuel type (for mapping to PG product)
FUEL_PRODUCT_MAP = {
    'PETROL': 'Petrol',
    'XTRA PREMIUM': 'Xtra Premium',
    'DIESEL': 'Diesel',
}

# ============================================================
# STATION LAYOUT - TO BE PROVIDED BY USER
# ============================================================
# Tank name -> (capacity_liters, fuel_product_name)
TANK_LAYOUT = {
    # Example - user must fill in:
    # 'MS Tank': (12000, 'Petrol'),
    # 'XP Tank': (6000, 'Xtra Premium'),
    # 'HSD Tank 1': (10000, 'Diesel'),
    # 'HSD Tank 2': (10000, 'Diesel'),
    # 'HSD Tank 3': (10000, 'Diesel'),
}

# Nozzle name -> (tank_name, pump_name)
NOZZLE_LAYOUT = {
    # Example - user must fill in:
    # 'N-1': ('MS Tank', 'Pump-1'),
    # 'N-2': ('MS Tank', 'Pump-1'),
}

# Pump names (ordered)
PUMP_NAMES = [
    # Example: 'Pump-1', 'Pump-2', 'Pump-3'
]
