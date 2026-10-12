-- 标签页菜单。来料和仓配角色可以打开打印与模板，操作工仍不看这页。

INSERT INTO sys_navigation_menu (code, label, module, route, button_codes, sort_order)
SELECT 'label', '标签', '打印 / 模板', '/label', '', 25
WHERE NOT EXISTS (SELECT 1 FROM sys_navigation_menu WHERE code = 'label');

INSERT INTO sys_role_menu (role_code, menu_code)
SELECT role_code, 'label'
FROM (
    SELECT 'ADMIN' AS role_code
    UNION ALL SELECT 'GENERAL_MANAGER'
    UNION ALL SELECT 'SALES'
    UNION ALL SELECT 'RECEIVER'
    UNION ALL SELECT 'WAREHOUSE_KEEPER'
) roles
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu existing
    WHERE existing.role_code = roles.role_code AND existing.menu_code = 'label'
);
