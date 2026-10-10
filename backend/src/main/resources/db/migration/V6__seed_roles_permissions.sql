-- Reference data required in every environment: roles, permissions and the role/permission mapping.

INSERT INTO roles (code, name, description) VALUES
    ('ADMIN',             'Administrator',     'Admin Console only: users, roles, activation, dealer onboarding, audit logs.'),
    ('SUPERVISOR',        'Supervisor',        'All modules, plus audited overrides, reassignment, cancellation and production/dispatch scheduling.'),
    ('MARKETING_MANAGER', 'Marketing Manager', 'Fleet evaluation and stock mapping.'),
    ('MARKETING_HEAD',    'Marketing Head',    'Executive approval and the executive dashboard.'),
    ('FINANCE_TEAM',      'Finance Team',      'Financial clearance and quotations.');

INSERT INTO permissions (code, description) VALUES
    ('ADMIN_USERS_MANAGE',       'Manage users, roles and activation'),
    ('ADMIN_DEALERS_MANAGE',     'Onboard and manage dealers'),
    ('AUDIT_LOG_READ',           'View audit logs'),
    ('FLEET_REQUEST_READ',       'View fleet requests'),
    ('FLEET_REQUEST_WRITE',      'Create and edit fleet requests in marketing review'),
    ('STOCK_MAP',                'Map stock and compute production needs'),
    ('MARKETING_FORWARD',        'Qualify and forward requests to the Marketing Head'),
    ('EXECUTIVE_APPROVE',        'Approve or reject marketing qualification'),
    ('DEPOSIT_REVIEW',           'Review deposits and forward to Finance'),
    ('EXECUTIVE_DASHBOARD_READ', 'View the executive analytics dashboard'),
    ('QUOTATION_READ',           'View quotations and quotation PDFs'),
    ('QUOTATION_ISSUE',          'Calculate and issue quotations'),
    ('PAYMENT_RECORD',           'Record payment receipts'),
    ('PAYMENT_APPROVE',          'Approve payment receipts'),
    ('FINANCE_CLEARANCE',        'Grant financial clearance'),
    ('PRODUCTION_SCHEDULE',      'Schedule production'),
    ('DISPATCH_SCHEDULE',        'Schedule and complete dispatch'),
    ('WORKFLOW_OVERRIDE',        'Override workflow steps with a reason'),
    ('REQUEST_CANCEL',           'Cancel requests with a reason'),
    ('REQUEST_REASSIGN',         'Reassign requests'),
    ('SLA_POLICY_MANAGE',        'Edit SLA policies');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = ANY (CASE r.code
    WHEN 'ADMIN' THEN ARRAY['ADMIN_USERS_MANAGE', 'ADMIN_DEALERS_MANAGE', 'AUDIT_LOG_READ']
    WHEN 'MARKETING_MANAGER' THEN ARRAY['FLEET_REQUEST_READ', 'FLEET_REQUEST_WRITE', 'STOCK_MAP', 'MARKETING_FORWARD']
    WHEN 'MARKETING_HEAD' THEN ARRAY['FLEET_REQUEST_READ', 'EXECUTIVE_APPROVE', 'DEPOSIT_REVIEW',
                                     'EXECUTIVE_DASHBOARD_READ', 'QUOTATION_READ']
    WHEN 'FINANCE_TEAM' THEN ARRAY['FLEET_REQUEST_READ', 'QUOTATION_READ', 'QUOTATION_ISSUE', 'PAYMENT_RECORD',
                                   'PAYMENT_APPROVE', 'FINANCE_CLEARANCE']
    END)
WHERE r.code <> 'SUPERVISOR';

-- SUPERVISOR holds every permission.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'SUPERVISOR';
