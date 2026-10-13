-- 用户管理独立入口，仅系统管理员可以管理账号及客户授权。
INSERT INTO sys_navigation_menu (code, label, module, route, button_codes, sort_order)
SELECT 'users', '用户管理', '账号 / 角色权限', '/users', 'AUTH_ACCOUNT_MANAGE,CUSTOMER_AUTHORIZATION_MANAGE', 105
WHERE NOT EXISTS (SELECT 1 FROM sys_navigation_menu WHERE code = 'users');

DELETE FROM sys_role_menu WHERE menu_code = 'users' AND role_code <> 'ADMIN';

INSERT INTO sys_role_menu (role_code, menu_code)
SELECT 'ADMIN', 'users'
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_code = 'ADMIN' AND menu_code = 'users');

UPDATE sys_navigation_menu SET button_codes = ''
WHERE code = 'configuration' AND button_codes = 'AUTH_ACCOUNT_MANAGE';
