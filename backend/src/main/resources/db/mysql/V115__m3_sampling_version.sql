-- 打样图稿版本和客户确认。版本正文不在确认时改写。模型只存引用，不存文件本体。
create table smp_sampling_version (
    sampling_code varchar(32) not null,
    version_kind varchar(16) not null,
    version_no int not null,
    parameter_text varchar(500) null,
    evidence_code varchar(80) null,
    model_file_name varchar(120) null,
    model_format varchar(24) null,
    model_version varchar(40) null,
    model_location_note varchar(200) null,
    model_evidence_code varchar(80) null,
    opened_by varchar(64) not null,
    opened_on char(10) not null,
    primary key (sampling_code, version_kind, version_no),
    constraint fk_smp_version_order foreign key (sampling_code) references smp_sampling_order (sampling_code)
);

create table smp_sampling_version_decision (
    sampling_code varchar(32) not null,
    version_kind varchar(16) not null,
    version_no int not null,
    decision_code varchar(16) not null,
    reason_text varchar(200) null,
    credential_code varchar(80) null,
    decided_by varchar(64) not null,
    decided_on char(10) not null,
    primary key (sampling_code, version_kind, version_no),
    constraint fk_smp_version_decision foreign key (sampling_code, version_kind, version_no)
        references smp_sampling_version (sampling_code, version_kind, version_no)
);
