-- 分批数量与整道工序完成事实分开保存；按工单串行核对累计可交数量。
CREATE TABLE IF NOT EXISTS biz_process_batch_lock (
    order_code VARCHAR(90) PRIMARY KEY
);
CREATE TABLE IF NOT EXISTS biz_process_batch_handoff (
    batch_code VARCHAR(80) PRIMARY KEY,
    order_code VARCHAR(90) NOT NULL,
    process_name VARCHAR(20) NOT NULL,
    quantity DECIMAL(18,3) NOT NULL,
    final_weight DECIMAL(18,3) NULL,
    operator VARCHAR(80) NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    KEY idx_process_batch_order (order_code, process_name)
);
