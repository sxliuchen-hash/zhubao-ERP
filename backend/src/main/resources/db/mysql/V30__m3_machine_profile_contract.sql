-- M3.135 机器档案主数据表。
-- 为设备产量绑定（M3.134）提供真实可选的机器档案来源。machine_no 对应设备产量记录的
-- machine_no_snapshot，id 即绑定命令中的 machineId。seed 雕刻 1-8 号机基线档案。

CREATE TABLE IF NOT EXISTS sys_machine (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    machine_no VARCHAR(80) NOT NULL,
    machine_name VARCHAR(120) NOT NULL,
    machine_type VARCHAR(80),
    location VARCHAR(120),
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    remark VARCHAR(255),
    created_by VARCHAR(80),
    created_at VARCHAR(40),
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_machine_no (machine_no)
);

INSERT INTO sys_machine (machine_no, machine_name, machine_type, location, enabled, remark, created_by, created_at) VALUES
    ('ENG-01', '雕刻机 1 号', '雕刻', '雕刻车间', 1, '一期评审 seed', 'system', '2026-07-09T10:00:00'),
    ('ENG-02', '雕刻机 2 号', '雕刻', '雕刻车间', 1, '一期评审 seed', 'system', '2026-07-09T10:00:00'),
    ('ENG-03', '雕刻机 3 号', '雕刻', '雕刻车间', 1, '一期评审 seed', 'system', '2026-07-09T10:00:00'),
    ('ENG-04', '雕刻机 4 号', '雕刻', '雕刻车间', 1, '一期评审 seed', 'system', '2026-07-09T10:00:00'),
    ('ENG-05', '雕刻机 5 号', '雕刻', '雕刻车间', 1, '一期评审 seed', 'system', '2026-07-09T10:00:00'),
    ('ENG-06', '雕刻机 6 号', '雕刻', '雕刻车间', 1, '一期评审 seed', 'system', '2026-07-09T10:00:00'),
    ('ENG-07', '雕刻机 7 号', '雕刻', '雕刻车间', 1, '一期评审 seed', 'system', '2026-07-09T10:00:00'),
    ('ENG-08', '雕刻机 8 号', '雕刻', '雕刻车间', 1, '一期评审 seed', 'system', '2026-07-09T10:00:00');
