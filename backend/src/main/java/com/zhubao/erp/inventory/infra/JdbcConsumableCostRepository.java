package com.zhubao.erp.inventory.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.CompanyMaterialAmountState;
import com.zhubao.erp.inventory.domain.ConsumableCostEvent;
import com.zhubao.erp.inventory.domain.ConsumableCostRepository;
import com.zhubao.erp.inventory.domain.ConsumptionTiming;
import com.zhubao.erp.work.app.WorkSessionBindings;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "zhubao.consumable-cost.repository", havingValue = "jdbc")
public class JdbcConsumableCostRepository implements ConsumableCostRepository {

    private final DataSource dataSource;

    public JdbcConsumableCostRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void saveRule(String issueId, ConsumptionTiming timing, boolean costPosted, String receiptId, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into inv_consumable_issue_rule(issue_id, timing, cost_posted, receipt_id) values (?,?,?,?)
                """)) {
            statement.setString(1, issueId);
            statement.setString(2, timing.name());
            statement.setBoolean(3, costPosted);
            statement.setString(4, receiptId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存耗材计耗规则", ex);
        }
    }

    @Override
    public String receiptIdOf(String issueId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select receipt_id from inv_consumable_issue_rule where issue_id=?")) {
            statement.setString(1, issueId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : "";
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取耗材计耗规则", ex);
        }
    }

    @Override
    public ConsumptionTiming timingOf(String issueId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select timing from inv_consumable_issue_rule where issue_id=?")) {
            statement.setString(1, issueId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? ConsumptionTiming.valueOf(rows.getString(1)) : null;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取耗材计耗规则", ex);
        }
    }

    @Override
    public boolean costPosted(String issueId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select cost_posted from inv_consumable_issue_rule where issue_id=?")) {
            statement.setString(1, issueId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() && rows.getBoolean(1);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取耗材计耗规则", ex);
        }
    }

    @Override
    public void markCostPosted(String issueId, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement(
                "update inv_consumable_issue_rule set cost_posted=true where issue_id=?")) {
            statement.setString(1, issueId);
            if (statement.executeUpdate() != 1) {
                throw new BusinessException("领用计耗规则不存在");
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法更新耗材计耗规则", ex);
        }
    }

    @Override
    public void insert(ConsumableCostEvent event, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into inv_consumable_cost_event(
                    event_id, issue_id, kind, amount_state, amount_value, quantity)
                values (?,?,?,?,?,?)
                """)) {
            statement.setString(1, event.eventId());
            statement.setString(2, event.issueId());
            statement.setString(3, event.kind());
            statement.setString(4, event.amountState().name());
            if (event.amount() == null) {
                statement.setBigDecimal(5, null);
            } else {
                statement.setBigDecimal(5, event.amount());
            }
            statement.setBigDecimal(6, event.quantity());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存耗材费用", ex);
        }
    }

    @Override
    public List<ConsumableCostEvent> events(String issueId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select * from inv_consumable_cost_event where issue_id=? order by event_id")) {
            statement.setString(1, issueId);
            try (ResultSet rows = statement.executeQuery()) {
                List<ConsumableCostEvent> found = new ArrayList<>();
                while (rows.next()) {
                    found.add(new ConsumableCostEvent(
                            rows.getString("event_id"),
                            rows.getString("issue_id"),
                            rows.getString("kind"),
                            CompanyMaterialAmountState.valueOf(rows.getString("amount_state")),
                            rows.getBigDecimal("amount_value"),
                            rows.getBigDecimal("quantity")));
                }
                return List.copyOf(found);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取耗材费用", ex);
        }
    }
}
