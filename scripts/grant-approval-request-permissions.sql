-- Seed APPROVAL_REQUEST_* permissions and grant them to the appropriate roles (idempotent).

-- 1. Insert permissions if missing
INSERT INTO permissions (code, description, module, action, system_default)
SELECT 'APPROVAL_REQUEST_CREATE', 'Submit approval requests (cashier)', 'APPROVAL', 'CREATE', true
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'APPROVAL_REQUEST_CREATE');

INSERT INTO permissions (code, description, module, action, system_default)
SELECT 'APPROVAL_REQUEST_VIEW', 'View the approval request queue', 'APPROVAL', 'VIEW', true
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'APPROVAL_REQUEST_VIEW');

INSERT INTO permissions (code, description, module, action, system_default)
SELECT 'APPROVAL_REQUEST_APPROVE', 'Approve or reject approval requests', 'APPROVAL', 'APPROVE', true
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'APPROVAL_REQUEST_APPROVE');

-- 2. Grant CREATE to CASHIER, OWNER, ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.role_type IN ('CASHIER', 'OWNER', 'ADMIN')
  AND p.code = 'APPROVAL_REQUEST_CREATE'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- 3. Grant VIEW + APPROVE to OWNER, ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.role_type IN ('OWNER', 'ADMIN')
  AND p.code IN ('APPROVAL_REQUEST_VIEW', 'APPROVAL_REQUEST_APPROVE')
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- 4. Verify
SELECT r.role_type, p.code
FROM role_permissions rp
JOIN roles r ON r.id = rp.role_id
JOIN permissions p ON p.id = rp.permission_id
WHERE p.code LIKE 'APPROVAL_REQUEST_%'
ORDER BY r.role_type, p.code;
