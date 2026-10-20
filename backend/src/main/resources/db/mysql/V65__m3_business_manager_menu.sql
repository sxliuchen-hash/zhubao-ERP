-- 业务管理可以顶上收货、报工和质检。不开放财务、审批、用户管理和运维。

INSERT INTO sys_role_menu (role_code, menu_code)
SELECT 'BUSINESS_MANAGER', menu_code
FROM (
    SELECT 'dashboard' AS menu_code
    UNION ALL SELECT 'inbound'
    UNION ALL SELECT 'label'
    UNION ALL SELECT 'production'
    UNION ALL SELECT 'progress'
    UNION ALL SELECT 'quality'
    UNION ALL SELECT 'inventory'
    UNION ALL SELECT 'approvals'
    UNION ALL SELECT 'documents'
    UNION ALL SELECT 'sample'
    UNION ALL SELECT 'hardware'
    UNION ALL SELECT 'mobile'
) menus
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu existing
    WHERE existing.role_code = 'BUSINESS_MANAGER' AND existing.menu_code = menus.menu_code
);
