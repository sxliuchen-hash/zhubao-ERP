package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.BusinessReviewData.ActionItem;
import com.zhubao.erp.business.domain.BusinessReviewData.InboundOrder;
import com.zhubao.erp.business.domain.BusinessReviewData.InboundWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.InventoryBalance;
import com.zhubao.erp.business.domain.BusinessReviewData.InventoryLedgerEntry;
import com.zhubao.erp.business.domain.BusinessReviewData.InventoryWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.LossApproval;
import com.zhubao.erp.business.domain.BusinessReviewData.MobileTask;
import com.zhubao.erp.business.domain.BusinessReviewData.MobileWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.ProductionHandoff;
import com.zhubao.erp.business.domain.BusinessReviewData.ProductionWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.QualityCase;
import com.zhubao.erp.business.domain.BusinessReviewData.QualityWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.ReviewStat;
import com.zhubao.erp.business.domain.BusinessReviewData.TraceEvent;
import com.zhubao.erp.business.domain.BusinessReviewData.WorkOrder;
import com.zhubao.erp.business.domain.BusinessWorkspaceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "zhubao.business.repository", havingValue = "jdbc")
public class JdbcBusinessWorkspaceRepository implements BusinessWorkspaceRepository {

    private final DataSource dataSource;
    private final String configuredSnapshotCode;

    @Autowired
    public JdbcBusinessWorkspaceRepository(DataSource dataSource, Environment environment) {
        this(dataSource, environment.getProperty("zhubao.business.snapshot-code", ""));
    }

    JdbcBusinessWorkspaceRepository(DataSource dataSource, String configuredSnapshotCode) {
        this.dataSource = dataSource;
        this.configuredSnapshotCode = configuredSnapshotCode == null ? "" : configuredSnapshotCode.trim();
    }

    @Override
    public InboundWorkspaceData inbound() {
        String snapshotCode = snapshotCode();
        return new InboundWorkspaceData(
                stats(snapshotCode, "inbound"),
                inboundOrders(snapshotCode),
                actionItems(snapshotCode, "inbound")
        );
    }

    @Override
    public ProductionWorkspaceData production() {
        String snapshotCode = snapshotCode();
        return new ProductionWorkspaceData(
                stats(snapshotCode, "production"),
                workOrders(snapshotCode),
                handoffs(snapshotCode)
        );
    }

    @Override
    public QualityWorkspaceData quality() {
        String snapshotCode = snapshotCode();
        return new QualityWorkspaceData(
                stats(snapshotCode, "quality"),
                qualityCases(snapshotCode),
                lossApprovals(snapshotCode)
        );
    }

    @Override
    public InventoryWorkspaceData inventory() {
        String snapshotCode = snapshotCode();
        return new InventoryWorkspaceData(
                inventoryBalances(snapshotCode),
                inventoryLedgerEntries(snapshotCode),
                traceEvents(snapshotCode),
                actionItems(snapshotCode, "inventory")
        );
    }

    @Override
    public MobileWorkspaceData mobile() {
        String snapshotCode = snapshotCode();
        return new MobileWorkspaceData(
                mobileTasks(snapshotCode),
                stats(snapshotCode, "mobile"),
                actionItems(snapshotCode, "mobile")
        );
    }

    private List<ReviewStat> stats(String snapshotCode, String workspaceCode) {
        return query("""
                select stat_label, stat_value, detail, tone
                from biz_workspace_stat
                where snapshot_code = ? and workspace_code = ?
                order by id
                """, mapper((rs) -> new ReviewStat(
                rs.getString("stat_label"),
                rs.getString("stat_value"),
                rs.getString("detail"),
                rs.getString("tone")
        )), snapshotCode, workspaceCode);
    }

    private List<ActionItem> actionItems(String snapshotCode, String workspaceCode) {
        return query("""
                select item_code, title, detail, owner_name, next_action, tone
                from biz_workspace_action_item
                where snapshot_code = ? and workspace_code = ?
                order by id
                """, mapper((rs) -> new ActionItem(
                rs.getString("item_code"),
                rs.getString("title"),
                rs.getString("detail"),
                rs.getString("owner_name"),
                rs.getString("next_action"),
                rs.getString("tone")
        )), snapshotCode, workspaceCode);
    }

    private List<InboundOrder> inboundOrders(String snapshotCode) {
        return query("""
                select mother_order_code, customer_name, material_name, courier_no, quantity,
                       weight_display, order_status, video_status, owner_type
                from biz_workspace_inbound_order
                where snapshot_code = ?
                order by id
                """, mapper((rs) -> new InboundOrder(
                rs.getString("mother_order_code"),
                rs.getString("customer_name"),
                rs.getString("material_name"),
                rs.getString("courier_no"),
                rs.getInt("quantity"),
                rs.getString("weight_display"),
                rs.getString("order_status"),
                rs.getString("video_status"),
                rs.getString("owner_type")
        )), snapshotCode);
    }

    private List<WorkOrder> workOrders(String snapshotCode) {
        return query("""
                select work_order_code, customer_name, material_name, process_name, assignee_name,
                       planned_quantity, finished_quantity, work_status, progress
                from biz_workspace_work_order
                where snapshot_code = ?
                order by id
                """, mapper((rs) -> new WorkOrder(
                rs.getString("work_order_code"),
                rs.getString("customer_name"),
                rs.getString("material_name"),
                rs.getString("process_name"),
                rs.getString("assignee_name"),
                rs.getInt("planned_quantity"),
                rs.getInt("finished_quantity"),
                rs.getString("work_status"),
                rs.getInt("progress")
        )), snapshotCode);
    }

    private List<ProductionHandoff> handoffs(String snapshotCode) {
        return query("""
                select handoff_code, from_node, to_node, input_weight_display, output_weight_display,
                       loss_rate_display, handoff_status, tone
                from biz_workspace_handoff
                where snapshot_code = ?
                order by id
                """, mapper((rs) -> new ProductionHandoff(
                rs.getString("handoff_code"),
                rs.getString("from_node"),
                rs.getString("to_node"),
                rs.getString("input_weight_display"),
                rs.getString("output_weight_display"),
                rs.getString("loss_rate_display"),
                rs.getString("handoff_status"),
                rs.getString("tone")
        )), snapshotCode);
    }

    private List<QualityCase> qualityCases(String snapshotCode) {
        return query("""
                select quality_code, work_order_code, process_name, inspector_name,
                       defect, quality_action, quality_status
                from biz_workspace_quality_case
                where snapshot_code = ?
                order by id
                """, mapper((rs) -> new QualityCase(
                rs.getString("quality_code"),
                rs.getString("work_order_code"),
                rs.getString("process_name"),
                rs.getString("inspector_name"),
                rs.getString("defect"),
                rs.getString("quality_action"),
                rs.getString("quality_status")
        )), snapshotCode);
    }

    private List<LossApproval> lossApprovals(String snapshotCode) {
        return query("""
                select approval_code, work_order_code, process_name, standard_loss, actual_loss,
                       evidence, approval_action, tone
                from biz_workspace_loss_approval
                where snapshot_code = ?
                order by id
                """, mapper((rs) -> new LossApproval(
                rs.getString("approval_code"),
                rs.getString("work_order_code"),
                rs.getString("process_name"),
                rs.getString("standard_loss"),
                rs.getString("actual_loss"),
                rs.getString("evidence"),
                rs.getString("approval_action"),
                rs.getString("tone")
        )), snapshotCode);
    }

    private List<InventoryBalance> inventoryBalances(String snapshotCode) {
        return query("""
                select owner_name, batch_code, material_name, location_name, stock_status,
                       quantity, weight_display, frozen_display
                from biz_workspace_inventory_balance
                where snapshot_code = ?
                order by id
                """, mapper((rs) -> new InventoryBalance(
                rs.getString("owner_name"),
                rs.getString("batch_code"),
                rs.getString("material_name"),
                rs.getString("location_name"),
                rs.getString("stock_status"),
                rs.getInt("quantity"),
                rs.getString("weight_display"),
                rs.getString("frozen_display")
        )), snapshotCode);
    }

    private List<InventoryLedgerEntry> inventoryLedgerEntries(String snapshotCode) {
        return query("""
                select occurred_time, ledger_code, movement, before_balance, after_balance,
                       source_document, operator_name
                from biz_workspace_inventory_ledger
                where snapshot_code = ?
                order by id
                """, mapper((rs) -> new InventoryLedgerEntry(
                rs.getString("occurred_time"),
                rs.getString("ledger_code"),
                rs.getString("movement"),
                rs.getString("before_balance"),
                rs.getString("after_balance"),
                rs.getString("source_document"),
                rs.getString("operator_name")
        )), snapshotCode);
    }

    private List<TraceEvent> traceEvents(String snapshotCode) {
        return query("""
                select event_time, node_name, biz_code, operator_name, evidence
                from biz_workspace_trace_event
                where snapshot_code = ?
                order by id
                """, mapper((rs) -> new TraceEvent(
                rs.getString("event_time"),
                rs.getString("node_name"),
                rs.getString("biz_code"),
                rs.getString("operator_name"),
                rs.getString("evidence")
        )), snapshotCode);
    }

    private List<MobileTask> mobileTasks(String snapshotCode) {
        return query("""
                select task_code, scene, next_action, required_fields, task_status
                from biz_workspace_mobile_task
                where snapshot_code = ?
                order by id
                """, mapper((rs) -> new MobileTask(
                rs.getString("task_code"),
                rs.getString("scene"),
                rs.getString("next_action"),
                rs.getString("required_fields"),
                rs.getString("task_status")
        )), snapshotCode);
    }

    private String snapshotCode() {
        if (!configuredSnapshotCode.isBlank()) {
            return configuredSnapshotCode;
        }
        return query("""
                select snapshot_code
                from biz_workspace_snapshot
                order by biz_date desc, id desc
                limit 1
                """, mapper((rs) -> rs.getString("snapshot_code")))
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No business workspace snapshot found"));
    }

    private <T> List<T> query(String sql, JdbcRowMapper<T> mapper, Object... params) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i += 1) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                java.util.ArrayList<T> rows = new java.util.ArrayList<>();
                while (resultSet.next()) {
                    rows.add(mapper.map(resultSet));
                }
                return List.copyOf(rows);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to query business workspace repository", ex);
        }
    }

    private <T> JdbcRowMapper<T> mapper(JdbcRowMapper<T> mapper) {
        return mapper;
    }

    @FunctionalInterface
    private interface JdbcRowMapper<T> {
        T map(ResultSet resultSet) throws SQLException;
    }
}
