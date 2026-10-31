-- 打样菜单只给管理员。不授予原有岗位，也不改登记导入。
insert into sys_navigation_menu (code, label, module, route, button_codes, sort_order)
select 'sampling', '打样', '独立样品', '/sampling', 'SAMPLING_VIEW,SAMPLING_MAINTAIN', 35
where not exists (select 1 from sys_navigation_menu where code = 'sampling');

insert into sys_role_menu (role_code, menu_code)
select 'ADMIN', 'sampling'
where not exists (select 1 from sys_role_menu where role_code = 'ADMIN' and menu_code = 'sampling');
