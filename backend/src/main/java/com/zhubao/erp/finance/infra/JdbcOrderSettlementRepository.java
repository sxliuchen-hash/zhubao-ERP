package com.zhubao.erp.finance.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.OrderSettlement;
import com.zhubao.erp.finance.domain.OrderSettlementItem;
import com.zhubao.erp.finance.domain.OrderSettlementPosting;
import com.zhubao.erp.finance.domain.OrderSettlementRepository;
import com.zhubao.erp.finance.domain.OrderSettlementSource;
import com.zhubao.erp.finance.domain.SettlementItemKind;
import com.zhubao.erp.finance.domain.SettlementStatus;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC 结单仓储。所有写入都留在工作单元连接上，失败由外层整单回滚。
 */
@Repository
@ConditionalOnProperty(name = "zhubao.finance.repository", havingValue = "jdbc")
public class JdbcOrderSettlementRepository implements OrderSettlementRepository {

    private final DataSource dataSource;

    public JdbcOrderSettlementRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean policyEnabled() {
        String sql = "select enabled from fin_settlement_policy where policy_code = 'POST_DELIVERY'";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            return rs.next() && "Y".equalsIgnoreCase(rs.getString(1));
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取结单启用状态", ex);
        }
    }

    @Override
    public void enablePolicy(String enabledAt, String operator, WorkSession session) {
        String sql = """
                update fin_settlement_policy
                   set enabled = 'Y', enabled_at = ?, operator_name = ?
                 where policy_code = 'POST_DELIVERY' and enabled <> 'Y'
                """;
        try (PreparedStatement statement = require(session).prepareStatement(sql)) {
            statement.setString(1, enabledAt == null ? "" : enabledAt);
            statement.setString(2, operator == null ? "" : operator);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法启用交货后结单", ex);
        }
    }

    @Override
    public Optional<OrderSettlement> find(String code, WorkSession session) {
        return queryOne(session, "select * from fin_order_settlement where code = ?", code);
    }

    @Override
    public Optional<OrderSettlement> latest(String targetType, String targetCode, WorkSession session) {
        String sql = """
                select * from fin_order_settlement
                 where target_type = ? and target_code = ?
                 order by version_no desc limit 1
                """;
        try (PreparedStatement statement = connection(session).prepareStatement(sql)) {
            statement.setString(1, targetType);
            statement.setString(2, targetCode);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取结单", ex);
        } finally {
            closeOwn(session);
        }
    }

    @Override
    public List<OrderSettlementItem> items(String settlementCode, WorkSession session) {
        String sql = "select * from fin_order_settlement_item where settlement_code = ? order by line_no";
        try (PreparedStatement statement = connection(session).prepareStatement(sql)) {
            statement.setString(1, settlementCode);
            try (ResultSet rs = statement.executeQuery()) {
                List<OrderSettlementItem> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new OrderSettlementItem(
                            rs.getInt("line_no"),
                            SettlementItemKind.valueOf(rs.getString("kind")),
                            rs.getString("title"),
                            rs.getString("source_ref"),
                            rs.getString("charge_mode"),
                            rs.getBigDecimal("quantity"),
                            rs.getString("quantity_unit"),
                            rs.getString("charge_unit"),
                            rs.getBigDecimal("unit_price"),
                            rs.getBigDecimal("gross_amount"),
                            rs.getBigDecimal("deduction"),
                            rs.getBigDecimal("net_amount"),
                            rs.getString("payee_account"),
                            rs.getString("payee_name"),
                            rs.getString("remark"),
                            rs.getString("loss_source_code")
                    ));
                }
                return List.copyOf(rows);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取结单明细", ex);
        } finally {
            closeOwn(session);
        }
    }

    @Override
    public List<OrderSettlementSource> sources(String settlementCode, WorkSession session) {
        String sql = "select * from fin_order_settlement_source where settlement_code = ? order by line_no";
        try (PreparedStatement statement = connection(session).prepareStatement(sql)) {
            statement.setString(1, settlementCode);
            try (ResultSet rs = statement.executeQuery()) {
                List<OrderSettlementSource> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new OrderSettlementSource(
                            rs.getInt("line_no"),
                            rs.getString("source_kind"),
                            rs.getString("source_code"),
                            rs.getString("snapshot_text")
                    ));
                }
                return List.copyOf(rows);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取结单来源", ex);
        } finally {
            closeOwn(session);
        }
    }

    @Override
    public List<OrderSettlementPosting> postings(String settlementCode, WorkSession session) {
        String sql = "select * from fin_order_settlement_posting where settlement_code = ? order by item_line_no, ledger_kind";
        try (PreparedStatement statement = connection(session).prepareStatement(sql)) {
            statement.setString(1, settlementCode);
            try (ResultSet rs = statement.executeQuery()) {
                List<OrderSettlementPosting> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new OrderSettlementPosting(
                            rs.getInt("item_line_no"),
                            rs.getString("ledger_kind"),
                            rs.getString("ledger_code"),
                            rs.getBigDecimal("amount")
                    ));
                }
                return List.copyOf(rows);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取结单入账关系", ex);
        } finally {
            closeOwn(session);
        }
    }

    @Override
    public List<OrderSettlement> list(String status, int offset, int limit) {
        String expected = status == null ? "" : status.trim();
        String sql = expected.isEmpty()
                ? "select * from fin_order_settlement order by code limit ? offset ?"
                : "select * from fin_order_settlement where status = ? order by code limit ? offset ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            if (!expected.isEmpty()) {
                statement.setString(index++, expected);
            }
            statement.setInt(index++, limit);
            statement.setInt(index, Math.max(0, offset));
            try (ResultSet rs = statement.executeQuery()) {
                List<OrderSettlement> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(map(rs));
                }
                return List.copyOf(rows);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法列出结单", ex);
        }
    }

    @Override
    public int count(String status) {
        String expected = status == null ? "" : status.trim();
        String sql = expected.isEmpty()
                ? "select count(*) from fin_order_settlement"
                : "select count(*) from fin_order_settlement where status = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (!expected.isEmpty()) {
                statement.setString(1, expected);
            }
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法统计结单", ex);
        }
    }

    @Override
    public OrderSettlement saveDraft(
            OrderSettlement header,
            List<OrderSettlementItem> itemRows,
            List<OrderSettlementSource> sourceRows,
            int expectedVersion,
            WorkSession session
    ) {
        Connection connection = require(session);
        try {
            lockTarget(header.targetType(), header.targetCode(), session);
            Optional<OrderSettlement> current = latest(header.targetType(), header.targetCode(), session);
            if (current.isEmpty()) {
                if (expectedVersion != 0) {
                    throw new BusinessException("结单草稿不存在，请刷新后重试");
                }
                OrderSettlement created = new OrderSettlement(
                        header.code(), header.code(), 1, "", header.targetType(), header.targetCode(),
                        header.motherOrderCode(), header.customerId(), header.customerName(), SettlementStatus.DRAFT, 1,
                        header.deliveryRevision(), header.customerNet(), header.employeeNet(), header.customerWaived(),
                        header.waiveReason(), header.noInternalLabor(), header.noLaborReason(), header.preparer(),
                        "", header.createdAt(), "", header.postingMonth(), header.legacyState()
                );
                insertHeader(connection, created);
                replaceChildren(connection, created.code(), itemRows, sourceRows);
                return created;
            }
            OrderSettlement existing = current.get();
            if (existing.status() == SettlementStatus.CONFIRMED) {
                throw new BusinessException("该单已确认结单，不能再覆盖原版本");
            }
            if (expectedVersion == 0) {
                throw new BusinessException("已有结单草稿，请按当前版本保存");
            }
            if (existing.rowVersion() != expectedVersion) {
                throw new BusinessException("结单草稿已变化，请刷新后重新保存");
            }
            String sql = """
                    update fin_order_settlement
                       set customer_id = ?, customer_name = ?, row_version = ?, delivery_revision = ?,
                           customer_net = ?, employee_net = ?, customer_waived = ?, waive_reason = ?,
                           no_internal_labor = ?, no_labor_reason = ?, preparer = ?, legacy_state = ?
                     where code = ? and status = 'DRAFT' and row_version = ?
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, header.customerId());
                statement.setString(2, header.customerName());
                statement.setInt(3, existing.rowVersion() + 1);
                statement.setString(4, header.deliveryRevision());
                statement.setBigDecimal(5, header.customerNet());
                statement.setBigDecimal(6, header.employeeNet());
                statement.setInt(7, header.customerWaived() ? 1 : 0);
                statement.setString(8, header.waiveReason());
                statement.setInt(9, header.noInternalLabor() ? 1 : 0);
                statement.setString(10, header.noLaborReason());
                statement.setString(11, header.preparer());
                statement.setString(12, header.legacyState());
                statement.setString(13, existing.code());
                statement.setInt(14, expectedVersion);
                if (statement.executeUpdate() != 1) {
                    throw new BusinessException("结单草稿已变化，请刷新后重新保存");
                }
            }
            replaceChildren(connection, existing.code(), itemRows, sourceRows);
            return find(existing.code(), session).orElseThrow(() -> new IllegalStateException("结单草稿保存后无法读取"));
        } catch (SQLException ex) {
            if (duplicate(ex)) {
                throw new BusinessException("结单草稿冲突，请刷新后重试");
            }
            throw new IllegalStateException("无法保存结单草稿", ex);
        }
    }

    @Override
    public OrderSettlement markConfirmed(
            String code, int expectedVersion, String confirmedBy, String postingMonth, String confirmedAt, WorkSession session
    ) {
        String sql = """
                update fin_order_settlement
                   set status = 'CONFIRMED', row_version = row_version + 1, confirmed_by = ?, confirmed_at = ?,
                       posting_month = ?, draft_key = null,
                       opening_key = case when previous_code = '' then 'OPEN' else null end
                 where code = ? and status = 'DRAFT' and row_version = ?
                """;
        try (PreparedStatement statement = require(session).prepareStatement(sql)) {
            statement.setString(1, confirmedBy == null ? "" : confirmedBy);
            statement.setString(2, confirmedAt == null ? "" : confirmedAt);
            statement.setString(3, postingMonth == null ? "" : postingMonth);
            statement.setString(4, code);
            statement.setInt(5, expectedVersion);
            if (statement.executeUpdate() != 1) {
                throw new BusinessException("只能确认尚未入账且版本一致的结单草稿");
            }
            return find(code, session).orElseThrow(() -> new IllegalStateException("结单确认后无法读取"));
        } catch (BusinessException ex) {
            throw ex;
        } catch (SQLException ex) {
            if (duplicate(ex)) {
                throw new BusinessException("该单已经有确认结单，不能再次确认首份费用");
            }
            throw new IllegalStateException("无法确认结单", ex);
        }
    }

    @Override
    public void savePostings(String settlementCode, List<OrderSettlementPosting> rows, WorkSession session) {
        Connection connection = require(session);
        try {
            try (PreparedStatement delete = connection.prepareStatement(
                    "delete from fin_order_settlement_posting where settlement_code = ?")) {
                delete.setString(1, settlementCode);
                delete.executeUpdate();
            }
            String sql = """
                    insert into fin_order_settlement_posting
                        (settlement_code, item_line_no, ledger_kind, ledger_code, amount)
                    values (?, ?, ?, ?, ?)
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (OrderSettlementPosting row : rows) {
                    statement.setString(1, settlementCode);
                    statement.setInt(2, row.itemLineNo());
                    statement.setString(3, row.ledgerKind());
                    statement.setString(4, row.ledgerCode());
                    statement.setBigDecimal(5, row.amount());
                    statement.addBatch();
                }
                if (!rows.isEmpty()) {
                    statement.executeBatch();
                }
            }
        } catch (SQLException ex) {
            if (duplicate(ex)) {
                throw new BusinessException("结单入账关系重复");
            }
            throw new IllegalStateException("无法保存结单入账关系", ex);
        }
    }

    @Override
    public void lockTarget(String targetType, String targetCode, WorkSession session) {
        String sql = "select code from fin_order_settlement where target_type = ? and target_code = ? for update";
        try (PreparedStatement statement = require(session).prepareStatement(sql)) {
            statement.setString(1, targetType);
            statement.setString(2, targetCode);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rs.getString(1);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法锁定结单", ex);
        }
    }

    private void insertHeader(Connection connection, OrderSettlement row) throws SQLException {
        String sql = """
                insert into fin_order_settlement (
                    code, root_code, version_no, previous_code, target_type, target_code, mother_order_code,
                    customer_id, customer_name, status, row_version, delivery_revision, customer_net, employee_net,
                    customer_waived, waive_reason, no_internal_labor, no_labor_reason, preparer, confirmed_by,
                    created_at, confirmed_at, posting_month, legacy_state, draft_key, opening_key
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, row.code());
            statement.setString(2, row.rootCode());
            statement.setInt(3, row.versionNo());
            statement.setString(4, row.previousCode());
            statement.setString(5, row.targetType());
            statement.setString(6, row.targetCode());
            statement.setString(7, row.motherOrderCode());
            statement.setLong(8, row.customerId());
            statement.setString(9, row.customerName());
            statement.setString(10, row.status().name());
            statement.setInt(11, row.rowVersion());
            statement.setString(12, row.deliveryRevision());
            statement.setBigDecimal(13, row.customerNet());
            statement.setBigDecimal(14, row.employeeNet());
            statement.setInt(15, row.customerWaived() ? 1 : 0);
            statement.setString(16, row.waiveReason());
            statement.setInt(17, row.noInternalLabor() ? 1 : 0);
            statement.setString(18, row.noLaborReason());
            statement.setString(19, row.preparer());
            statement.setString(20, row.confirmedBy());
            statement.setString(21, row.createdAt());
            statement.setString(22, row.confirmedAt());
            statement.setString(23, row.postingMonth());
            statement.setString(24, row.legacyState());
            if (row.status() == SettlementStatus.DRAFT) {
                statement.setString(25, row.targetType() + "|" + row.targetCode());
            } else {
                statement.setNull(25, Types.VARCHAR);
            }
            statement.setNull(26, Types.VARCHAR);
            statement.executeUpdate();
        }
    }

    private void replaceChildren(Connection connection, String code, List<OrderSettlementItem> itemRows,
                                 List<OrderSettlementSource> sourceRows) throws SQLException {
        try (PreparedStatement deleteItems = connection.prepareStatement(
                "delete from fin_order_settlement_item where settlement_code = ?");
             PreparedStatement deleteSources = connection.prepareStatement(
                     "delete from fin_order_settlement_source where settlement_code = ?")) {
            deleteItems.setString(1, code);
            deleteItems.executeUpdate();
            deleteSources.setString(1, code);
            deleteSources.executeUpdate();
        }
        String itemSql = """
                insert into fin_order_settlement_item (
                    settlement_code, line_no, kind, title, source_ref, charge_mode, quantity, quantity_unit,
                    charge_unit, unit_price, gross_amount, deduction, net_amount, payee_account, payee_name,
                    remark, loss_source_code
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(itemSql)) {
            for (OrderSettlementItem row : itemRows) {
                statement.setString(1, code);
                statement.setInt(2, row.lineNo());
                statement.setString(3, row.kind().name());
                statement.setString(4, row.title());
                statement.setString(5, row.sourceRef());
                statement.setString(6, row.chargeMode());
                decimal(statement, 7, row.quantity());
                statement.setString(8, row.quantityUnit());
                statement.setString(9, row.chargeUnit());
                decimal(statement, 10, row.unitPrice());
                decimal(statement, 11, row.grossAmount());
                statement.setBigDecimal(12, row.deduction());
                decimal(statement, 13, row.netAmount());
                statement.setString(14, row.payeeAccount());
                statement.setString(15, row.payeeName());
                statement.setString(16, row.remark());
                statement.setString(17, row.lossSourceCode());
                statement.addBatch();
            }
            if (!itemRows.isEmpty()) {
                statement.executeBatch();
            }
        }
        String sourceSql = """
                insert into fin_order_settlement_source (settlement_code, line_no, source_kind, source_code, snapshot_text)
                values (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sourceSql)) {
            for (OrderSettlementSource row : sourceRows) {
                statement.setString(1, code);
                statement.setInt(2, row.lineNo());
                statement.setString(3, row.sourceKind());
                statement.setString(4, row.sourceCode());
                statement.setString(5, limit(row.snapshotText(), 500));
                statement.addBatch();
            }
            if (!sourceRows.isEmpty()) {
                statement.executeBatch();
            }
        }
    }

    private Optional<OrderSettlement> queryOne(WorkSession session, String sql, String code) {
        try (PreparedStatement statement = connection(session).prepareStatement(sql)) {
            statement.setString(1, code);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取结单", ex);
        } finally {
            closeOwn(session);
        }
    }

    private OrderSettlement map(ResultSet rs) throws SQLException {
        return new OrderSettlement(
                rs.getString("code"),
                rs.getString("root_code"),
                rs.getInt("version_no"),
                rs.getString("previous_code"),
                rs.getString("target_type"),
                rs.getString("target_code"),
                rs.getString("mother_order_code"),
                rs.getLong("customer_id"),
                rs.getString("customer_name"),
                SettlementStatus.valueOf(rs.getString("status")),
                rs.getInt("row_version"),
                rs.getString("delivery_revision"),
                rs.getBigDecimal("customer_net"),
                rs.getBigDecimal("employee_net"),
                rs.getInt("customer_waived") == 1,
                rs.getString("waive_reason"),
                rs.getInt("no_internal_labor") == 1,
                rs.getString("no_labor_reason"),
                rs.getString("preparer"),
                rs.getString("confirmed_by"),
                rs.getString("created_at"),
                rs.getString("confirmed_at"),
                rs.getString("posting_month"),
                rs.getString("legacy_state")
        );
    }

    private final ThreadLocal<Connection> owned = new ThreadLocal<>();

    private Connection connection(WorkSession session) throws SQLException {
        if (session != null && session.connection().isPresent()) {
            return session.connection().get();
        }
        if (session != null) {
            throw new IllegalStateException("结单仓储与工作单元持久化不一致，拒绝写入");
        }
        Connection connection = dataSource.getConnection();
        owned.set(connection);
        return connection;
    }

    private void closeOwn(WorkSession session) {
        if (session != null) {
            return;
        }
        Connection connection = owned.get();
        owned.remove();
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ex) {
                throw new IllegalStateException("无法关闭结单查询连接", ex);
            }
        }
    }

    @Override
    public OrderSettlement beginAdjustment(
            OrderSettlement header,
            List<OrderSettlementItem> itemRows,
            List<OrderSettlementSource> sourceRows,
            WorkSession session
    ) {
        Connection connection = require(session);
        try {
            lockTarget(header.targetType(), header.targetCode(), session);
            OrderSettlement current = latest(header.targetType(), header.targetCode(), session)
                    .orElseThrow(() -> new BusinessException("没有已确认结单可调整"));
            if (current.status() != SettlementStatus.CONFIRMED) {
                throw new BusinessException("请先确认结单再调整");
            }
            OrderSettlement created = new OrderSettlement(
                    header.code(), current.rootCode(), current.versionNo() + 1, current.code(),
                    header.targetType(), header.targetCode(), header.motherOrderCode(), header.customerId(),
                    header.customerName(), SettlementStatus.DRAFT, 1, header.deliveryRevision(),
                    header.customerNet(), header.employeeNet(), header.customerWaived(), header.waiveReason(),
                    header.noInternalLabor(), header.noLaborReason(), header.preparer(), "", header.createdAt(),
                    "", "", header.legacyState()
            );
            insertHeader(connection, created);
            replaceChildren(connection, created.code(), itemRows, sourceRows);
            return created;
        } catch (BusinessException ex) {
            throw ex;
        } catch (SQLException ex) {
            if (duplicate(ex)) {
                throw new BusinessException("结单调整冲突，请刷新后重试");
            }
            throw new IllegalStateException("无法保存结单调整", ex);
        }
    }

    @Override
    public void saveLegacyLink(
            String code,
            String settlementCode,
            String ledgerKind,
            String ledgerCode,
            BigDecimal coveredAmount,
            WorkSession session
    ) {
        String sql = """
                insert into fin_settlement_legacy_link
                    (code, settlement_code, ledger_kind, ledger_code, covered_amount)
                values (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = require(session).prepareStatement(sql)) {
            statement.setString(1, code);
            statement.setString(2, settlementCode);
            statement.setString(3, ledgerKind);
            statement.setString(4, ledgerCode);
            statement.setBigDecimal(5, coveredAmount);
            statement.executeUpdate();
        } catch (SQLException ex) {
            if (duplicate(ex)) {
                throw new BusinessException("同一旧账不能重复接续");
            }
            throw new IllegalStateException("无法接续历史账", ex);
        }
    }

    @Override
    public boolean legacyLinked(String ledgerKind, String ledgerCode, WorkSession session) {
        String sql = "select code from fin_settlement_legacy_link where ledger_kind = ? and ledger_code = ?";
        try (PreparedStatement statement = require(session).prepareStatement(sql)) {
            statement.setString(1, ledgerKind);
            statement.setString(2, ledgerCode);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取历史账接续", ex);
        }
    }

    private Connection require(WorkSession session) {
        if (session == null || session.connection().isEmpty()) {
            throw new IllegalStateException("结单写入必须处于同一业务工作单元");
        }
        return session.connection().get();
    }

    private static void decimal(PreparedStatement statement, int index, BigDecimal value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DECIMAL);
        } else {
            statement.setBigDecimal(index, value);
        }
    }

    private static String limit(String value, int max) {
        String text = value == null ? "" : value;
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static boolean duplicate(SQLException ex) {
        String state = ex.getSQLState();
        if ("23000".equals(state) || "23505".equals(state)) {
            return true;
        }
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        return message.contains("duplicate") || message.contains("unique");
    }
}
