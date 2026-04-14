-- Grant REPORT_VIEW permission to CASHIER role (idempotent).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.role_type = 'CASHIER'
  AND p.code = 'REPORT_VIEW'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

SELECT r.role_type, p.code
FROM role_permissions rp
JOIN roles r ON r.id = rp.role_id
JOIN permissions p ON p.id = rp.permission_id
WHERE r.role_type = 'CASHIER' AND p.code = 'REPORT_VIEW';
