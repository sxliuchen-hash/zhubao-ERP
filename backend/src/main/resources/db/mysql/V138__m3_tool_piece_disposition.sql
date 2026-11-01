-- 报废工具保留原编号，禁止再次借出。
alter table inv_tool_piece add column scrapped boolean not null default false;
update inv_tool_piece set scrapped=true where piece_code in (
    select l.piece_code from inv_tool_loan l join inv_tool_disposition d on d.loan_id=l.loan_id where d.kind='SCRAP'
);
