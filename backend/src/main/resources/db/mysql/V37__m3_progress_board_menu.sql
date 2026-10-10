-- 进度一览菜单。办公室角色可见，操作工不看这张表。

INSERT INTO sys_navigation_menu (code, label, module, route, button_codes, sort_order)
SELECT 'progress', '进度', '在制 / 客户', '/progress', '', 35
WHERE NOT EXISTS (SELECT 1 FROM sys_navigation_menu WHERE code = 'progress');

INSERT INTO sys_role_menu (role_code, menu_code)
SELECT role_code, 'progress'
FROM (
    SELECT 'ADMIN' AS role_code
    UNION ALL SELECT 'GENERAL_MANAGER'
    UNION ALL SELECT 'SALES'
    UNION ALL SELECT 'RECEIVER'
    UNION ALL SELECT 'WORKSHOP_LEADER'
    UNION ALL SELECT 'QUALITY_INSPECTOR'
    UNION ALL SELECT 'WAREHOUSE_KEEPER'
    UNION ALL SELECT 'FINANCE_STATISTICIAN'
) roles
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu existing
    WHERE existing.role_code = roles.role_code AND existing.menu_code = 'progress'
);
