-- 打样节点报工。不写普通工序份额，照片编号只能使用一次。
create table smp_sampling_operation (
    report_id varchar(40) not null primary key,
    sampling_code varchar(32) not null,
    step_id varchar(32) not null,
    version_kind varchar(16) not null,
    version_no int not null,
    making_choice varchar(16) null,
    quantity decimal(18,3) not null,
    unit_name varchar(16) not null,
    attachment_code varchar(80) not null,
    worker_name varchar(64) not null,
    reported_on char(10) not null,
    unique key uk_smp_operation_photo (attachment_code),
    constraint fk_smp_operation_order foreign key (sampling_code) references smp_sampling_order (sampling_code)
);
