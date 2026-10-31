package com.zhubao.erp.goldwork.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.goldwork.domain.GoldworkMaterialIntent;
import com.zhubao.erp.goldwork.domain.GoldworkOrder;
import com.zhubao.erp.goldwork.domain.GoldworkOrderRepository;
import com.zhubao.erp.goldwork.domain.GoldworkOrderStatus;
import com.zhubao.erp.work.app.WorkSessionBindings;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.goldwork-order.repository", havingValue = "jdbc")
public class JdbcGoldworkOrderRepository implements GoldworkOrderRepository {

    private final DataSource dataSource;

    public JdbcGoldworkOrderRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void insert(GoldworkOrder order, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into gld_goldwork_order(
                    goldwork_code, customer_id, customer_name, material_intent, requirement_text,
                    owner_name, route_steps, status_code, version_no, opened_by, opened_on)
                values (?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, order.goldworkCode());
            statement.setLong(2, order.customerId());
            statement.setString(3, order.customerName());
            statement.setString(4, order.materialIntent().name());
            statement.setString(5, order.requirement());
            statement.setString(6, order.ownerName());
            statement.setString(7, order.routeSteps());
            statement.setString(8, order.status().name());
            statement.setLong(9, order.version());
            statement.setString(10, order.openedBy());
            statement.setString(11, order.openedOn());
            statement.executeUpdate();
        } catch (SQLException ex) {
            if (duplicate(ex)) {
                throw new BusinessException("金工单号冲突，请稍后重试");
            }
            throw new IllegalStateException("无法保存金工单", ex);
        }
    }

    @Override
    public Optional<GoldworkOrder> find(String goldworkCode) {
        if (goldworkCode == null || goldworkCode.isBlank()) {
            return Optional.empty();
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectSql() + " where goldwork_code=?")) {
            statement.setString(1, goldworkCode.trim());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取金工单", ex);
        }
    }

    @Override
    public List<GoldworkOrder> list() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectSql() + " order by goldwork_code");
             ResultSet rows = statement.executeQuery()) {
            List<GoldworkOrder> found = new ArrayList<>();
            while (rows.next()) {
                found.add(map(rows));
            }
            return found;
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取金工单", ex);
        }
    }

    @Override
    public List<String> lockDay(LocalDate day, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        String prefix = "GJ-" + day.format(DateTimeFormatter.BASIC_ISO_DATE) + "-%";
        try (PreparedStatement statement = connection.prepareStatement("""
                select goldwork_code from gld_goldwork_order
                 where goldwork_code like ?
                 order by goldwork_code
                 for update
                """)) {
            statement.setString(1, prefix);
            try (ResultSet rows = statement.executeQuery()) {
                List<String> found = new ArrayList<>();
                while (rows.next()) {
                    found.add(rows.getString(1));
                }
                return found;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法锁定当天金工单号", ex);
        }
    }

    private static GoldworkOrder map(ResultSet rows) throws SQLException {
        return new GoldworkOrder(
                rows.getString("goldwork_code"),
                rows.getLong("customer_id"),
                rows.getString("customer_name"),
                GoldworkMaterialIntent.valueOf(rows.getString("material_intent")),
                rows.getString("requirement_text"),
                rows.getString("owner_name"),
                rows.getString("route_steps"),
                GoldworkOrderStatus.valueOf(rows.getString("status_code")),
                rows.getLong("version_no"),
                rows.getString("opened_by"),
                rows.getString("opened_on")
        );
    }

    private static String selectSql() {
        return """
                select goldwork_code, customer_id, customer_name, material_intent, requirement_text,
                       owner_name, route_steps, status_code, version_no, opened_by, opened_on
                  from gld_goldwork_order
                """;
    }

    private static boolean duplicate(SQLException ex) {
        String state = ex.getSQLState();
        return ex.getErrorCode() == 1062 || ex.getErrorCode() == 23505
                || "23000".equals(state) || "23505".equals(state)
                || (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("unique"));
    }
}
