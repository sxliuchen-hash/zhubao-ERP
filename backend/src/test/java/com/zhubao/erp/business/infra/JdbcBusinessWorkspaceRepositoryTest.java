package com.zhubao.erp.business.infra;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcBusinessWorkspaceRepositoryTest {

    @Test
    void loadsWorkspaceReviewDataFromJdbcContractTables() throws SQLException {
        JdbcDataSource dataSource = dataSource();
        createSchema(dataSource);
        insertSeedRows(dataSource);

        JdbcBusinessWorkspaceRepository repository = new JdbcBusinessWorkspaceRepository(dataSource, "SNAP-20260706-DAY");

        assertThat(repository.inbound().orders())
                .anySatisfy(order -> {
                    assertThat(order.code()).isEqualTo("MD-20260706-001");
                    assertThat(order.expressCompany()).isEqualTo("顺丰");
                    assertThat(order.unpackSigner()).isEqualTo("何琳");
                });
        assertThat(repository.production().workOrders())
                .anySatisfy(order -> {
                    assertThat(order.process()).isEqualTo("雕刻");
                    assertThat(order.machineNo()).isEqualTo("ENG-03");
                });
        assertThat(repository.production().handoffs())
                .anySatisfy(handoff -> assertThat(handoff.code()).isEqualTo("HO-20260706-021"));
        assertThat(repository.quality().lossApprovals())
                .anySatisfy(loss -> assertThat(loss.code()).isEqualTo("LOSS-20260706-009"));
        assertThat(repository.inventory().timeline())
                .anySatisfy(event -> assertThat(event.bizCode()).isEqualTo("MD-20260706-001"));
        assertThat(repository.mobile().offlineDrafts())
                .anySatisfy(draft -> assertThat(draft.code()).isEqualTo("DRAFT-QC-003"));
    }

    @Test
    void usesLatestSnapshotWhenNoSnapshotCodeIsConfigured() throws SQLException {
        JdbcDataSource dataSource = dataSource();
        createSchema(dataSource);
        insertSeedRows(dataSource);
        execute(dataSource, """
                insert into biz_workspace_snapshot(snapshot_code, snapshot_name, biz_date, workshop_name, shift_name)
                values ('SNAP-20260707-DAY', '次日白班', date '2026-07-07', '总装一线', '白班')
                """);
        execute(dataSource, """
                insert into biz_workspace_inbound_order(snapshot_code, mother_order_code, customer_name, material_name,
                    inbound_date_display, express_company, courier_no, sender_name, receiver_name, unpack_signer, goods_name,
                    quantity, weight_display, order_status, video_status, owner_type, inbound_remark)
                values ('SNAP-20260707-DAY', 'MD-20260707-001', '恒信珠宝', '18K 金戒托',
                    '07-07', '顺丰', 'SF1048329002', '张先生', '何琳', '何琳', '戒托来料',
                    20, '31.20 g', '待拆包', '待开始', '客户所有', '次日样例')
                """);

        JdbcBusinessWorkspaceRepository repository = new JdbcBusinessWorkspaceRepository(dataSource, "");

        assertThat(repository.inbound().orders())
                .extracting("code")
                .containsExactly("MD-20260707-001");
    }

    private JdbcDataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private void createSchema(JdbcDataSource dataSource) throws SQLException {
        String[] statements = {
                """
                create table biz_workspace_snapshot (
                    id bigint primary key auto_increment,
                    snapshot_code varchar(80) not null unique,
                    snapshot_name varchar(120) not null,
                    biz_date date not null,
                    workshop_name varchar(120) not null,
                    shift_name varchar(80) not null
                )
                """,
                """
                create table biz_workspace_stat (
                    id bigint primary key auto_increment,
                    snapshot_code varchar(80) not null,
                    workspace_code varchar(40) not null,
                    stat_key varchar(80) not null,
                    stat_label varchar(120) not null,
                    stat_value varchar(120) not null,
                    detail varchar(500),
                    tone varchar(30) not null
                )
                """,
                """
                create table biz_workspace_action_item (
                    id bigint primary key auto_increment,
                    snapshot_code varchar(80) not null,
                    workspace_code varchar(40) not null,
                    item_code varchar(80) not null,
                    title varchar(160) not null,
                    detail varchar(500) not null,
                    owner_name varchar(80) not null,
                    next_action varchar(160) not null,
                    tone varchar(30) not null
                )
                """,
                """
                create table biz_workspace_inbound_order (
                    id bigint primary key auto_increment,
                    snapshot_code varchar(80) not null,
                    mother_order_code varchar(80) not null,
                    customer_name varchar(120) not null,
                    material_name varchar(160) not null,
                    inbound_date_display varchar(40),
                    express_company varchar(80),
                    courier_no varchar(80) not null,
                    sender_name varchar(120),
                    receiver_name varchar(120),
                    unpack_signer varchar(120),
                    goods_name varchar(160),
                    quantity int not null,
                    weight_display varchar(80) not null,
                    order_status varchar(40) not null,
                    video_status varchar(40) not null,
                    owner_type varchar(40) not null,
                    inbound_remark varchar(500)
                )
                """,
                """
                create table biz_workspace_work_order (
                    id bigint primary key auto_increment,
                    snapshot_code varchar(80) not null,
                    work_order_code varchar(80) not null,
                    customer_name varchar(120) not null,
                    material_name varchar(160) not null,
                    process_name varchar(120) not null,
                    process_category varchar(60),
                    product_category varchar(120),
                    machine_no varchar(80),
                    work_item_name varchar(120),
                    started_at_display varchar(40),
                    finished_at_display varchar(40),
                    completion_status varchar(120),
                    handoff_audit_status varchar(80),
                    assignee_name varchar(80) not null,
                    planned_quantity int not null,
                    finished_quantity int not null,
                    work_status varchar(40) not null,
                    progress int not null
                )
                """,
                """
                create table biz_workspace_handoff (
                    id bigint primary key auto_increment,
                    snapshot_code varchar(80) not null,
                    handoff_code varchar(80) not null,
                    from_node varchar(160) not null,
                    to_node varchar(160) not null,
                    input_weight_display varchar(80) not null,
                    output_weight_display varchar(80) not null,
                    loss_rate_display varchar(80) not null,
                    handoff_status varchar(40) not null,
                    tone varchar(30) not null
                )
                """,
                """
                create table biz_workspace_quality_case (
                    id bigint primary key auto_increment,
                    snapshot_code varchar(80) not null,
                    quality_code varchar(80) not null,
                    work_order_code varchar(80) not null,
                    process_name varchar(120) not null,
                    inspector_name varchar(80) not null,
                    defect varchar(500) not null,
                    quality_action varchar(80) not null,
                    quality_status varchar(80) not null
                )
                """,
                """
                create table biz_workspace_loss_approval (
                    id bigint primary key auto_increment,
                    snapshot_code varchar(80) not null,
                    approval_code varchar(80) not null,
                    work_order_code varchar(80) not null,
                    process_name varchar(120) not null,
                    standard_loss varchar(80) not null,
                    actual_loss varchar(80) not null,
                    evidence varchar(500) not null,
                    approval_action varchar(120) not null,
                    tone varchar(30) not null
                )
                """,
                """
                create table biz_workspace_inventory_balance (
                    id bigint primary key auto_increment,
                    snapshot_code varchar(80) not null,
                    owner_name varchar(120) not null,
                    batch_code varchar(80) not null,
                    material_name varchar(160) not null,
                    location_name varchar(160) not null,
                    stock_status varchar(40) not null,
                    quantity int not null,
                    weight_display varchar(80) not null,
                    frozen_display varchar(80) not null
                )
                """,
                """
                create table biz_workspace_inventory_ledger (
                    id bigint primary key auto_increment,
                    snapshot_code varchar(80) not null,
                    ledger_code varchar(80) not null,
                    occurred_time varchar(40) not null,
                    movement varchar(80) not null,
                    before_balance varchar(160) not null,
                    after_balance varchar(160) not null,
                    source_document varchar(80) not null,
                    operator_name varchar(80) not null
                )
                """,
                """
                create table biz_workspace_trace_event (
                    id bigint primary key auto_increment,
                    snapshot_code varchar(80) not null,
                    event_time varchar(40) not null,
                    node_name varchar(120) not null,
                    biz_code varchar(80) not null,
                    operator_name varchar(80) not null,
                    evidence varchar(200) not null
                )
                """,
                """
                create table biz_workspace_mobile_task (
                    id bigint primary key auto_increment,
                    snapshot_code varchar(80) not null,
                    task_code varchar(80) not null,
                    scene varchar(120) not null,
                    next_action varchar(200) not null,
                    required_fields varchar(500) not null,
                    task_status varchar(80) not null
                )
                """
        };
        for (String statement : statements) {
            execute(dataSource, statement);
        }
    }

    private void insertSeedRows(JdbcDataSource dataSource) throws SQLException {
        String snapshotCode = "SNAP-20260706-DAY";
        execute(dataSource, """
                insert into biz_workspace_snapshot(snapshot_code, snapshot_name, biz_date, workshop_name, shift_name)
                values ('SNAP-20260706-DAY', '今日白班', date '2026-07-06', '总装一线', '白班')
                """);
        execute(dataSource, """
                insert into biz_workspace_stat(snapshot_code, workspace_code, stat_key, stat_label, stat_value, detail, tone)
                values
                    ('%s', 'inbound', 'video', '视频完整率', '92%%', '1 个截取任务失败', 'amber'),
                    ('%s', 'production', 'engraving', '雕刻', '3 台 / 68%%', '1 台缺少操机签字', 'amber'),
                    ('%s', 'quality', 'loss', '损耗阻断', '1 条', '超过工序标准 1.8%%', 'red'),
                    ('%s', 'mobile', 'offline', '离线草稿', '设计中', '弱网时暂存报工和质检', 'amber')
                """.formatted(snapshotCode, snapshotCode, snapshotCode, snapshotCode));
        execute(dataSource, """
                insert into biz_workspace_action_item(snapshot_code, workspace_code, item_code, title, detail, owner_name, next_action, tone)
                values
                    ('%s', 'inbound', 'MD-20260706-004', '拆包视频截取失败', 'NVR 时间段任务返回失败', '库管员', '重试截取', 'red'),
                    ('%s', 'inventory', 'EV-VIDEO-001', '视频证据', 'INT-VID-001', '库管员', '1 个截取失败', 'red'),
                    ('%s', 'mobile', 'DRAFT-QC-003', '缺陷拍照', '照片已缓存，质检结果未提交', '周娜', '15:06', 'red')
                """.formatted(snapshotCode, snapshotCode, snapshotCode));
        execute(dataSource, """
                insert into biz_workspace_inbound_order(snapshot_code, mother_order_code, customer_name, material_name,
                    inbound_date_display, express_company, courier_no, sender_name, receiver_name, unpack_signer, goods_name,
                    quantity, weight_display, order_status, video_status, owner_type, inbound_remark)
                values ('%s', 'MD-20260706-001', '恒信珠宝', '18K 金戒托',
                    '07-06', '顺丰', 'SF1048329001', '张先生', '何琳', '何琳', '戒托来料',
                    320, '486.25 g', '待拆包', '待开始', '客户所有', '拆箱签字待复核')
                """.formatted(snapshotCode));
        execute(dataSource, """
                insert into biz_workspace_work_order(snapshot_code, work_order_code, customer_name, material_name, process_name,
                    process_category, product_category, machine_no, work_item_name, started_at_display, finished_at_display,
                    completion_status, handoff_audit_status, assignee_name, planned_quantity, finished_quantity, work_status, progress)
                values ('%s', 'ZD-20260706-009', '恒信珠宝', '18K 金戒托', '雕刻',
                    '设备', '戒托', 'ENG-03', '雕刻', '09:00', '10:15',
                    '已完成 168 件', '操机签字待补', '周凯', 200, 168, '待交单审核', 84)
                """.formatted(snapshotCode));
        execute(dataSource, """
                insert into biz_workspace_handoff(snapshot_code, handoff_code, from_node, to_node, input_weight_display,
                    output_weight_display, loss_rate_display, handoff_status, tone)
                values ('%s', 'HO-20260706-021', '抛光 / 赵磊', '返工复检 / 刘洋',
                    '52.40 g', '50.41 g', '3.80%%', '审批阻断', 'red')
                """.formatted(snapshotCode));
        execute(dataSource, """
                insert into biz_workspace_quality_case(snapshot_code, quality_code, work_order_code, process_name,
                    inspector_name, defect, quality_action, quality_status)
                values ('%s', 'QC-20260706-021', 'ZD-20260706-002', '镶石',
                    '周娜', '松石 3 件，划痕 2 件', '返工', '待生成返工工单')
                """.formatted(snapshotCode));
        execute(dataSource, """
                insert into biz_workspace_loss_approval(snapshot_code, approval_code, work_order_code, process_name,
                    standard_loss, actual_loss, evidence, approval_action, tone)
                values ('%s', 'LOSS-20260706-009', 'ZD-20260706-001', '抛光',
                    '2.0%%', '3.8%%', '投入 121.15g / 产出 116.55g', '待组长审批', 'red')
                """.formatted(snapshotCode));
        execute(dataSource, """
                insert into biz_workspace_inventory_balance(snapshot_code, owner_name, batch_code, material_name,
                    location_name, stock_status, quantity, weight_display, frozen_display)
                values ('%s', '恒信珠宝', 'ZD-20260706-001', '18K 金戒托',
                    '车间-抛光', '在制', 80, '121.15 g', '0')
                """.formatted(snapshotCode));
        execute(dataSource, """
                insert into biz_workspace_inventory_ledger(snapshot_code, ledger_code, occurred_time, movement,
                    before_balance, after_balance, source_document, operator_name)
                values ('%s', 'LEDGER-20260706-001', '09:05', '来料入库',
                    '0 件 / 0 g', '320 件 / 486.25 g', 'MD-20260706-001', '何琳')
                """.formatted(snapshotCode));
        execute(dataSource, """
                insert into biz_workspace_trace_event(snapshot_code, event_time, node_name, biz_code, operator_name, evidence)
                values ('%s', '09:05', '拆包称重', 'MD-20260706-001', '库管员 何琳', '拆包视频 05:14')
                """.formatted(snapshotCode));
        execute(dataSource, """
                insert into biz_workspace_mobile_task(snapshot_code, task_code, scene, next_action, required_fields, task_status)
                values ('%s', 'ZD-20260706-006', '称重交接', '补录投入/产出重量',
                    '投入重量、产出重量、交接人', '必填阻断')
                """.formatted(snapshotCode));
    }

    private void execute(JdbcDataSource dataSource, String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
