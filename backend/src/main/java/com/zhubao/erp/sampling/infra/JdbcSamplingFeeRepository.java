package com.zhubao.erp.sampling.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.sampling.domain.SamplingFeeDecision;
import com.zhubao.erp.sampling.domain.SamplingFeeRepository;
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
import java.sql.Types;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.sampling-fee.repository", havingValue = "jdbc")
public class JdbcSamplingFeeRepository implements SamplingFeeRepository {

    private final DataSource dataSource;

    public JdbcSamplingFeeRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void insert(SamplingFeeDecision decision, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into smp_sampling_fee(
                    fee_id, delivery_id, sampling_code, decision_code, amount, reason_text, receivable_code)
                values (?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, decision.feeId());
            statement.setString(2, decision.deliveryId());
            statement.setString(3, decision.samplingCode());
            statement.setString(4, decision.decision());
            if (decision.amount() == null) {
                statement.setNull(5, Types.DECIMAL);
            } else {
                statement.setBigDecimal(5, decision.amount());
            }
            if (decision.reason().isEmpty()) {
                statement.setNull(6, Types.VARCHAR);
            } else {
                statement.setString(6, decision.reason());
            }
            if (decision.receivableCode().isEmpty()) {
                statement.setNull(7, Types.VARCHAR);
            } else {
                statement.setString(7, decision.receivableCode());
            }
            statement.executeUpdate();
        } catch (SQLException ex) {
            if (duplicate(ex)) {
                throw new BusinessException("这次交付的样费已经确认");
            }
            throw new IllegalStateException("无法保存打样样费", ex);
        }
    }

    @Override
    public Optional<SamplingFeeDecision> findByDelivery(String deliveryId) {
        if (deliveryId == null || deliveryId.isBlank()) {
            return Optional.empty();
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectSql() + " where delivery_id=?")) {
            statement.setString(1, deliveryId.trim());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取打样样费", ex);
        }
    }

    @Override
    public Optional<SamplingFeeDecision> lockByDelivery(String deliveryId, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement(selectSql() + " where delivery_id=? for update")) {
            statement.setString(1, deliveryId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法锁定打样样费", ex);
        }
    }

    private static SamplingFeeDecision map(ResultSet rows) throws SQLException {
        BigDecimal amount = rows.getBigDecimal("amount");
        return new SamplingFeeDecision(
                rows.getString("fee_id"),
                rows.getString("delivery_id"),
                rows.getString("sampling_code"),
                rows.getString("decision_code"),
                amount,
                rows.getString("reason_text"),
                rows.getString("receivable_code")
        );
    }

    private static String selectSql() {
        return """
                select fee_id, delivery_id, sampling_code, decision_code, amount, reason_text, receivable_code
                  from smp_sampling_fee
                """;
    }

    private static boolean duplicate(SQLException ex) {
        String state = ex.getSQLState();
        return ex.getErrorCode() == 1062 || ex.getErrorCode() == 23505
                || "23000".equals(state) || "23505".equals(state)
                || (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("unique"));
    }
}
