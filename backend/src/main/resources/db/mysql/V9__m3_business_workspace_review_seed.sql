-- M3 review seed for /api/business/workspaces when zhubao.business.repository=jdbc.
-- This is not final transactional ERP data; it is a deterministic acceptance snapshot.

insert into biz_workspace_snapshot(snapshot_code, snapshot_name, biz_date, workshop_name, shift_name)
values ('SNAP-20260706-DAY', '2026-07-06 白班评审快照', '2026-07-06', '总装一线', '白班');

insert into biz_workspace_stat(snapshot_code, workspace_code, stat_key, stat_label, stat_value, detail, tone)
values
('SNAP-20260706-DAY', 'inbound', 'scan_pending', '待扫码收货', '2 单', '快递单未绑定母单', 'amber'),
('SNAP-20260706-DAY', 'inbound', 'weigh_pending', '待拆包称重', '3 单', '需记录拆包前后重量', 'blue'),
('SNAP-20260706-DAY', 'inbound', 'video_integrity', '视频完整率', '92%', '1 个截取任务失败', 'amber'),
('SNAP-20260706-DAY', 'inbound', 'label_ready', '标签可打印', '4 张', '含 1 张待补打', 'green'),
('SNAP-20260706-DAY', 'production', 'engraving', '雕刻', '3 台 / 68%', '1 台缺少操机签字', 'amber'),
('SNAP-20260706-DAY', 'production', 'polish', '抛光', '6 张 / 78%', '2 张等待交单审核', 'amber'),
('SNAP-20260706-DAY', 'production', 'setting', '镶石', '5 张 / 92%', '质检积压，暂缓派新单', 'red'),
('SNAP-20260706-DAY', 'production', 'welding', '焊接', '4 张 / 64%', '设备正常，可继续派工', 'green'),
('SNAP-20260706-DAY', 'production', 'final_check', '终检', '3 张 / 55%', '等待缺陷照片归档', 'blue'),
('SNAP-20260706-DAY', 'quality', 'dispatch_pending', '待派检', '7 批', '最早提交 09:40', 'amber'),
('SNAP-20260706-DAY', 'quality', 'recheck', '返工复检', '2 批', '均已生成返工工单', 'violet'),
('SNAP-20260706-DAY', 'quality', 'defect_photos', '缺陷照片', '15 张', '2 张待补拍', 'blue'),
('SNAP-20260706-DAY', 'quality', 'loss_blocked', '损耗阻断', '1 条', '超过工序标准 1.8%', 'red'),
('SNAP-20260706-DAY', 'mobile', 'scan', '扫码识别', '可用', 'BarcodeDetector 优先，ZXing 兜底，失败可手输编码。', 'green'),
('SNAP-20260706-DAY', 'mobile', 'offline_draft', '离线草稿', '设计中', '弱网时暂存报工和质检，恢复网络后补提交。', 'amber'),
('SNAP-20260706-DAY', 'mobile', 'photo_evidence', '拍照留证', '一期必做', '缺陷、称重、人工视频兜底都需要附件。', 'blue'),
('SNAP-20260706-DAY', 'mobile', 'permission_boundary', '权限边界', '待接入', '当前为样例身份，M3 接入真实角色权限。', 'gray');

insert into biz_workspace_action_item(snapshot_code, workspace_code, item_code, title, detail, owner_name, next_action, tone)
values
('SNAP-20260706-DAY', 'inbound', 'MD-20260706-003', '物料名称待确认', '宝石托盘客户描述与历史物料档案不一致，需要业务员确认后才能入账。', '业务员', '确认物料档案', 'amber'),
('SNAP-20260706-DAY', 'inbound', 'MD-20260706-004', '拆包视频截取失败', 'NVR 时间段任务返回失败，系统允许重试一次，仍失败则人工上传视频。', '库管员', '重试截取', 'red'),
('SNAP-20260706-DAY', 'inventory', 'EV-BIZ-001', '业务单据', '母单 / 子工单 / 报工 / 质检 / 台账 / 出库', '系统', '已关联 6 张', 'green'),
('SNAP-20260706-DAY', 'inventory', 'EV-WEIGHT-001', '称重记录', 'HO-20260706-018 / HO-20260706-021', '车间组长', '2 条待补照片', 'amber'),
('SNAP-20260706-DAY', 'inventory', 'EV-VIDEO-001', '视频证据', 'INT-VID-001', '库管员', '1 个截取失败', 'red'),
('SNAP-20260706-DAY', 'inventory', 'EV-AUDIT-001', '操作审计', '扫码、补打、审批均已留痕', '系统', '完整', 'green'),
('SNAP-20260706-DAY', 'mobile', 'DRAFT-BG-001', '抛光报工', '恢复网络后需复核工单状态', '李华', '14:28', 'amber'),
('SNAP-20260706-DAY', 'mobile', 'DRAFT-QC-003', '缺陷拍照', '照片已缓存，质检结果未提交', '周娜', '15:06', 'red');

insert into biz_workspace_inbound_order(snapshot_code, mother_order_code, customer_name, material_name, inbound_date_display, express_company, courier_no, sender_name, receiver_name, unpack_signer, goods_name, quantity, weight_display, order_status, video_status, owner_type, inbound_remark)
values
('SNAP-20260706-DAY', 'MD-20260706-001', '恒信珠宝', '18K 金戒托', '07-06', '顺丰', 'SF1048329001', '张先生', '何琳', '何琳', '戒托来料', 320, '486.25 g', '待拆包', '待开始', '客户所有', '拆箱签字待复核'),
('SNAP-20260706-DAY', 'MD-20260706-002', '雅辰饰品', '银链半成品', '07-06', '圆通', 'YT7740192207', '陈女士', '何琳', '何琳', '银链半成品', 1200, '2,840.00 g', '已入库', '已关联', '客户所有', '寄收件人已核对'),
('SNAP-20260706-DAY', 'MD-20260706-003', '璟瑞工贸', '宝石托坯', '07-06', '京东', 'JD9301827446', '客户仓库', '李敏', '待补签', '宝石托盘', 540, '718.30 g', '待确认物料', '截取中', '客户所有', '货品名称与物料档案待匹配'),
('SNAP-20260706-DAY', 'MD-20260706-004', '瑞禾珠宝', 'PT950 吊坠坯', '07-06', '德邦', 'DB7730018291', '王先生', '何琳', '何琳', 'PT950 吊坠坯', 180, '392.10 g', '异常待处理', '失败', '客户所有', '视频截取失败，需人工上传');

insert into biz_workspace_work_order(snapshot_code, work_order_code, customer_name, material_name, process_name, process_category, product_category, machine_no, work_item_name, started_at_display, finished_at_display, completion_status, handoff_audit_status, assignee_name, planned_quantity, finished_quantity, work_status, progress)
values
('SNAP-20260706-DAY', 'ZD-20260706-001', '恒信珠宝', '18K 金戒托', '抛光', '人工', '戒托', 'POL-01', '精抛', '13:40', '14:30', '部分完成', '待交单审核', '李华', 80, 56, '生产中', 70),
('SNAP-20260706-DAY', 'ZD-20260706-002', '恒信珠宝', '18K 金戒托', '镶石', '人工', '戒托', '人工工位', '镶石', '10:10', '11:25', '已完成', '已接收', '陈敏', 80, 80, '待质检', 100),
('SNAP-20260706-DAY', 'ZD-20260706-006', '雅辰饰品', '银链半成品', '焊接', '人工', '银链', 'WELD-02', '焊接', '15:00', '进行中', '称重待补', '待交单审核', '王强', 300, 210, '称重待补', 70),
('SNAP-20260706-DAY', 'ZD-20260706-009', '恒信珠宝', '18K 金戒托', '雕刻', '设备', '戒托', 'ENG-03', '雕刻', '09:00', '10:15', '已完成 168 件', '操机签字待补', '周凯', 200, 168, '待交单审核', 84),
('SNAP-20260706-DAY', 'FG-20260706-002', '瑞禾珠宝', 'PT950 吊坠坯', '返工抛光', '人工', '吊坠坯', 'POL-02', '返工抛光', '15:05', '进行中', '返工中', '待复检', '赵磊', 24, 12, '返工中', 50);

insert into biz_workspace_handoff(snapshot_code, handoff_code, from_node, to_node, input_weight_display, output_weight_display, loss_rate_display, handoff_status, tone)
values
('SNAP-20260706-DAY', 'HO-20260706-014', '粗抛 / 李华', '精抛 / 陈敏', '121.15 g', '119.92 g', '1.02%', '可交接', 'green'),
('SNAP-20260706-DAY', 'HO-20260706-018', '焊接 / 王强', '质检 / 周娜', '684.40 g', '683.20 g', '0.18%', '待补照片', 'amber'),
('SNAP-20260706-DAY', 'HO-20260706-021', '抛光 / 赵磊', '返工复检 / 刘洋', '52.40 g', '50.41 g', '3.80%', '审批阻断', 'red');

insert into biz_workspace_quality_case(snapshot_code, quality_code, work_order_code, process_name, inspector_name, defect, quality_action, quality_status)
values
('SNAP-20260706-DAY', 'QC-20260706-021', 'ZD-20260706-002', '镶石', '周娜', '松石 3 件，划痕 2 件', '返工', '待生成返工工单'),
('SNAP-20260706-DAY', 'QC-20260706-022', 'ZD-20260706-006', '焊接', '周娜', '焊点偏移 6 件', '复检', '待确认称重'),
('SNAP-20260706-DAY', 'QC-20260706-023', 'FG-20260706-001', '返工抛光', '刘洋', '返工通过', '放行', '已完成');

insert into biz_workspace_loss_approval(snapshot_code, approval_code, work_order_code, process_name, standard_loss, actual_loss, evidence, approval_action, tone)
values
('SNAP-20260706-DAY', 'LOSS-20260706-009', 'ZD-20260706-001', '抛光', '2.0%', '3.8%', '投入 121.15g / 产出 116.55g / 缺陷照片 5 张', '待组长审批', 'red'),
('SNAP-20260706-DAY', 'LOSS-20260706-010', 'ZD-20260706-006', '焊接', '0.8%', '待确认', '缺少产出称重照片', '待补称重', 'amber');

insert into biz_workspace_inventory_balance(snapshot_code, owner_name, batch_code, material_name, location_name, stock_status, quantity, weight_display, frozen_display)
values
('SNAP-20260706-DAY', '恒信珠宝', 'MD-20260706-001', '18K 金戒托', '原料库 A-01', '待加工', 240, '365.10 g', '0'),
('SNAP-20260706-DAY', '恒信珠宝', 'ZD-20260706-001', '18K 金戒托', '车间-抛光', '在制', 80, '121.15 g', '0'),
('SNAP-20260706-DAY', '雅辰饰品', 'MD-20260706-002', '银链半成品', '成品库 F-02', '成品', 620, '1,451.20 g', '200 件'),
('SNAP-20260706-DAY', '瑞禾珠宝', 'FG-20260706-002', 'PT950 吊坠坯', '返工区 R-01', '返工在制', 24, '52.40 g', '0');

insert into biz_workspace_inventory_ledger(snapshot_code, ledger_code, occurred_time, movement, before_balance, after_balance, source_document, operator_name)
values
('SNAP-20260706-DAY', 'LEDGER-20260706-001', '09:05', '来料入库', '0 件 / 0 g', '320 件 / 486.25 g', 'MD-20260706-001', '何琳'),
('SNAP-20260706-DAY', 'LEDGER-20260706-007', '10:20', '工单领料', '320 件 / 486.25 g', '240 件 / 365.10 g', 'ZD-20260706-001', '马杰'),
('SNAP-20260706-DAY', 'LEDGER-20260706-018', '14:30', '工序产出', '80 件 / 121.15 g', '56 件 / 84.95 g', 'BG-20260706-044', '李华'),
('SNAP-20260706-DAY', 'LEDGER-20260706-022', '15:10', '质检冻结', '56 件 / 84.95 g', '5 件冻结 / 7.20 g', 'QC-20260706-021', '周娜');

insert into biz_workspace_trace_event(snapshot_code, event_time, node_name, biz_code, operator_name, evidence)
values
('SNAP-20260706-DAY', '08:42', '扫码收货', 'SF1048329001', '库管员 何琳', '快递单照片'),
('SNAP-20260706-DAY', '09:05', '拆包称重', 'MD-20260706-001', '库管员 何琳', '拆包视频 05:14'),
('SNAP-20260706-DAY', '10:20', '生成子工单', 'ZD-20260706-001', '车间组长 马杰', '母单标签扫描'),
('SNAP-20260706-DAY', '14:30', '工序报工', 'BG-20260706-044', '操作工 李华', '称重交接 121.15g'),
('SNAP-20260706-DAY', '15:10', '质检异常', 'QC-20260706-021', '质检员 周娜', '缺陷照片 5 张');

insert into biz_workspace_mobile_task(snapshot_code, task_code, scene, next_action, required_fields, task_status)
values
('SNAP-20260706-DAY', 'ZD-20260706-001', '扫码进入报工', '提交抛光数量 24 件', '数量、重量、照片可选', '可操作'),
('SNAP-20260706-DAY', 'ZD-20260706-006', '称重交接', '补录投入/产出重量', '投入重量、产出重量、交接人', '必填阻断'),
('SNAP-20260706-DAY', 'QC-20260706-021', '移动质检', '选择返工原因并拍照', '不合格数量、原因、处理方式', '待质检');
