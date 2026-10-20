-- 客户档案页。查看和新建仍受档案权限与客户范围约束，操作工不看这页。

INSERT INTO sys_navigation_menu (code, label, module, route, button_codes, sort_order)
SELECT 'customers', '客户档案', '客户 / 材质', '/customers', 'MASTER_DATA_MANAGE', 22
WHERE NOT EXISTS (SELECT 1 FROM sys_navigation_menu WHERE code = 'customers');

INSERT INTO sys_role_menu (role_code, menu_code)
SELECT role_code, 'customers'
FROM (
    SELECT 'ADMIN' AS role_code
    UNION ALL SELECT 'GENERAL_MANAGER'
    UNION ALL SELECT 'BUSINESS_MANAGER'
    UNION ALL SELECT 'SALES'
    UNION ALL SELECT 'RECEIVER'
    UNION ALL SELECT 'WAREHOUSE_KEEPER'
    UNION ALL SELECT 'WORKSHOP_LEADER'
    UNION ALL SELECT 'FINANCE_STATISTICIAN'
) roles
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu existing
    WHERE existing.role_code = roles.role_code AND existing.menu_code = 'customers'
);
