-- 外协费用依据可以选填应付款。空着不生成应付，也不回写成 0。
alter table fin_outsourcing_fee_basis add column amount decimal(18,2) null;
