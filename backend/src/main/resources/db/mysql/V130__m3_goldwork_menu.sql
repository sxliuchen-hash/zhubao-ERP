-- 金工菜单只给管理员。不授予原有岗位，也不改打样或登记导入。
insert into sys_navigation_menu (code, label, module, route, button_codes, sort_order)
select 'goldwork', '金工', '独立金工', '/goldwork', 'GOLDWORK_VIEW,GOLDWORK_MAINTAIN', 36
where not exists (select 1 from sys_navigation_menu where code = 'goldwork');

insert into sys_role_menu (role_code, menu_code)
select 'ADMIN', 'goldwork'
where not exists (select 1 from sys_role_menu where role_code = 'ADMIN' and menu_code = 'goldwork');
