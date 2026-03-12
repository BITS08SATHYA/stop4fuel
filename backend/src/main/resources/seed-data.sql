-- Seed Vehicle Types
INSERT INTO vehicle_type (type_name, description) VALUES
('Car', 'Standard passenger car'),
('Bus', 'Public or private bus'),
('Truck', 'Heavy duty truck'),
('Jeep', 'Off-road vehicle'),
('Bike', 'Motorcycle or scooter')
ON CONFLICT DO NOTHING;

-- Seed Products (Fuel Types)
INSERT INTO product (scid, name, hsn_code, price, category, unit, brand, active) VALUES
(1, 'Petrol', '27101211', 107.50, 'FUEL', 'LITERS', 'IOCL', true),
(1, 'Diesel', '27101990', 93.20, 'FUEL', 'LITERS', 'IOCL', true),
(1, 'Xtra Premium', '27101210', 112.00, 'FUEL', 'LITERS', 'IOCL', true)
ON CONFLICT DO NOTHING;
