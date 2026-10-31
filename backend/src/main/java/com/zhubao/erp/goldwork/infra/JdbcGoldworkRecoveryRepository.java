package com.zhubao.erp.goldwork.infra;

import com.zhubao.erp.goldwork.domain.GoldworkRecovery;
import com.zhubao.erp.goldwork.domain.GoldworkRecoveryKind;
import com.zhubao.erp.goldwork.domain.GoldworkRecoveryRepository;
import com.zhubao.erp.inventory.domain.OwnerType;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.goldwork-recovery.repository", havingValue = "jdbc")
public class JdbcGoldworkRecoveryRepository implements GoldworkRecoveryRepository {

    private final DataSource dataSource;

    public JdbcGoldworkRecoveryRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void insert(GoldworkRecovery recovery, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into gld_goldwork_recovery(
                    recovery_id, goldwork_code, source_line_id, output_movement_id, recovery_kind, owner_type,
                    piece_count, net_weight, gross_weight, weight_unit, purity_text, precision_state)
                values (?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, recovery.recoveryId());
            statement.setString(2, recovery.goldworkCode());
            statement.setString(3, recovery.sourceLineId());
            statement.setString(4, recovery.outputMovementId());
            statement.setString(5, recovery.kind().name());
            statement.setString(6, recovery.owner().name());
            setDecimal(statement, 7, recovery.pieceCount());
            setDecimal(statement, 8, recovery.netWeight());
            setDecimal(statement, 9, recovery.grossWeight());
            setText(statement, 10, recovery.weightUnit());
            setText(statement, 11, recovery.purity());
            statement.setString(12, recovery.precisionState());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存回收记录", ex);
        }
    }

    @Override
    public Optional<GoldworkRecovery> find(String recoveryId) {
        if (recoveryId == null || recoveryId.isBlank()) {
            return Optional.empty();
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectSql() + " where recovery_id=?")) {
            statement.setString(1, recoveryId.trim());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取回收记录", ex);
        }
    }

    @Override
    public List<GoldworkRecovery> list(String goldworkCode) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     selectSql() + " where goldwork_code=? order by recovery_id")) {
            statement.setString(1, goldworkCode);
            try (ResultSet rows = statement.executeQuery()) {
                List<GoldworkRecovery> found = new ArrayList<>();
                while (rows.next()) {
                    found.add(map(rows));
                }
                return found;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取回收记录", ex);
        }
    }

    private static GoldworkRecovery map(ResultSet rows) throws SQLException {
        return new GoldworkRecovery(
                rows.getString("recovery_id"),
                rows.getString("goldwork_code"),
                rows.getString("source_line_id"),
                rows.getString("output_movement_id"),
                GoldworkRecoveryKind.valueOf(rows.getString("recovery_kind")),
                OwnerType.valueOf(rows.getString("owner_type")),
                rows.getBigDecimal("piece_count"),
                rows.getBigDecimal("net_weight"),
                rows.getBigDecimal("gross_weight"),
                rows.getString("weight_unit"),
                rows.getString("purity_text"),
                rows.getString("precision_state")
        );
    }

    private static void setDecimal(PreparedStatement statement, int index, BigDecimal value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DECIMAL);
            return;
        }
        statement.setBigDecimal(index, value);
    }

    private static void setText(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null || value.isEmpty()) {
            statement.setNull(index, Types.VARCHAR);
            return;
        }
        statement.setString(index, value);
    }

    private static String selectSql() {
        return """
                select recovery_id, goldwork_code, source_line_id, output_movement_id, recovery_kind, owner_type,
                       piece_count, net_weight, gross_weight, weight_unit, purity_text, precision_state
                  from gld_goldwork_recovery
                """;
    }
}
