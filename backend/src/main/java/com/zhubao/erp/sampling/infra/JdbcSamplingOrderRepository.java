package com.zhubao.erp.sampling.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.sampling.domain.SamplingMaterialIntent;
import com.zhubao.erp.sampling.domain.SamplingOrder;
import com.zhubao.erp.sampling.domain.SamplingOrderRepository;
import com.zhubao.erp.sampling.domain.SamplingOrderStatus;
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
@ConditionalOnProperty(name = "zhubao.sampling-order.repository", havingValue = "jdbc")
public class JdbcSamplingOrderRepository implements SamplingOrderRepository {

    private final DataSource dataSource;

    public JdbcSamplingOrderRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void insert(SamplingOrder order, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into smp_sampling_order(
                    sampling_code, customer_id, customer_name, material_intent, requirement_text,
                    owner_name, status_code, version_no, opened_by, opened_on)
                values (?,?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, order.samplingCode());
            statement.setLong(2, order.customerId());
            statement.setString(3, order.customerName());
            statement.setString(4, order.materialIntent().name());
            statement.setString(5, order.requirement());
            statement.setString(6, order.ownerName());
            statement.setString(7, order.status().name());
            statement.setLong(8, order.version());
            statement.setString(9, order.openedBy());
            statement.setString(10, order.openedOn());
            statement.executeUpdate();
        } catch (SQLException ex) {
            if (duplicate(ex)) {
                throw new BusinessException("打样单号冲突，请稍后重试");
            }
            throw new IllegalStateException("无法保存打样单", ex);
        }
    }

    @Override
    public Optional<SamplingOrder> find(String samplingCode) {
        if (samplingCode == null || samplingCode.isBlank()) {
            return Optional.empty();
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectSql() + " where sampling_code=?")) {
            statement.setString(1, samplingCode.trim());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取打样单", ex);
        }
    }

    @Override
    public List<SamplingOrder> list() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectSql() + " order by sampling_code");
             ResultSet rows = statement.executeQuery()) {
            List<SamplingOrder> found = new ArrayList<>();
            while (rows.next()) {
                found.add(map(rows));
            }
            return found;
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取打样单", ex);
        }
    }

    @Override
    public List<String> lockDay(LocalDate day, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        String prefix = "DY-" + day.format(DateTimeFormatter.BASIC_ISO_DATE) + "-%";
        try (PreparedStatement statement = connection.prepareStatement("""
                select sampling_code from smp_sampling_order
                 where sampling_code like ?
                 order by sampling_code
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
            throw new IllegalStateException("无法锁定当天打样单号", ex);
        }
    }

    private static SamplingOrder map(ResultSet rows) throws SQLException {
        return new SamplingOrder(
                rows.getString("sampling_code"),
                rows.getLong("customer_id"),
                rows.getString("customer_name"),
                SamplingMaterialIntent.valueOf(rows.getString("material_intent")),
                rows.getString("requirement_text"),
                rows.getString("owner_name"),
                SamplingOrderStatus.valueOf(rows.getString("status_code")),
                rows.getLong("version_no"),
                rows.getString("opened_by"),
                rows.getString("opened_on")
        );
    }

    private static String selectSql() {
        return """
                select sampling_code, customer_id, customer_name, material_intent, requirement_text,
                       owner_name, status_code, version_no, opened_by, opened_on
                  from smp_sampling_order
                """;
    }

    private static boolean duplicate(SQLException ex) {
        String state = ex.getSQLState();
        return ex.getErrorCode() == 1062 || ex.getErrorCode() == 23505
                || "23000".equals(state) || "23505".equals(state)
                || (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("unique"));
    }
}
