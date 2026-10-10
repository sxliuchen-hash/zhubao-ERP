-- 母单补上手机收货要带着走的加工要求、完成期限和客户名称。
ALTER TABLE biz_mother_order
    ADD COLUMN processing_requirement VARCHAR(500) NOT NULL DEFAULT '' AFTER inbound_remark,
    ADD COLUMN due_date VARCHAR(20) NOT NULL DEFAULT '' AFTER processing_requirement,
    ADD COLUMN customer_name VARCHAR(120) NOT NULL DEFAULT '' AFTER due_date;
