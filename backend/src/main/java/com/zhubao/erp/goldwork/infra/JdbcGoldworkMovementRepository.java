package com.zhubao.erp.goldwork.infra;

import com.zhubao.erp.goldwork.domain.GoldworkMovement;
import com.zhubao.erp.goldwork.domain.GoldworkMovementKind;
import com.zhubao.erp.goldwork.domain.GoldworkMovementRepository;
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
@ConditionalOnProperty(name = "zhubao.goldwork-movement.repository", havingValue = "jdbc")
public class JdbcGoldworkMovementRepository implements GoldworkMovementRepository {

    private final DataSource dataSource;

    public JdbcGoldworkMovementRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void insert(GoldworkMovement movement, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into gld_goldwork_movement(
                    movement_id, goldwork_code, step_id, movement_kind, source_line_id, owner_type,
                    piece_count, net_weight, gross_weight, weight_unit, purity_text,
                    disposition, conversion_note, precision_state)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, movement.movementId());
            statement.setString(2, movement.goldworkCode());
            statement.setString(3, movement.stepId());
            statement.setString(4, movement.kind().name());
            statement.setString(5, movement.sourceLineId());
            statement.setString(6, movement.owner().name());
            setDecimal(statement, 7, movement.pieceCount());
            setDecimal(statement, 8, movement.netWeight());
            setDecimal(statement, 9, movement.grossWeight());
            setText(statement, 10, movement.weightUnit());
            setText(statement, 11, movement.purity());
            setText(statement, 12, movement.disposition());
            setText(statement, 13, movement.conversionNote());
            statement.setString(14, movement.precisionState());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存投入产出", ex);
        }
    }

    @Override
    public Optional<GoldworkMovement> find(String movementId) {
        if (movementId == null || movementId.isBlank()) {
            return Optional.empty();
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectSql() + " where movement_id=?")) {
            statement.setString(1, movementId.trim());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取投入产出", ex);
        }
    }

    @Override
    public List<GoldworkMovement> list(String goldworkCode) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     selectSql() + " where goldwork_code=? order by movement_id")) {
            statement.setString(1, goldworkCode);
            try (ResultSet rows = statement.executeQuery()) {
                List<GoldworkMovement> found = new ArrayList<>();
                while (rows.next()) {
                    found.add(map(rows));
                }
                return found;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取投入产出", ex);
        }
    }

    private static GoldworkMovement map(ResultSet rows) throws SQLException {
        return new GoldworkMovement(
                rows.getString("movement_id"),
                rows.getString("goldwork_code"),
                rows.getString("step_id"),
                GoldworkMovementKind.valueOf(rows.getString("movement_kind")),
                rows.getString("source_line_id"),
                OwnerType.valueOf(rows.getString("owner_type")),
                rows.getBigDecimal("piece_count"),
                rows.getBigDecimal("net_weight"),
                rows.getBigDecimal("gross_weight"),
                rows.getString("weight_unit"),
                rows.getString("purity_text"),
                rows.getString("disposition"),
                rows.getString("conversion_note"),
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
                select movement_id, goldwork_code, step_id, movement_kind, source_line_id, owner_type,
                       piece_count, net_weight, gross_weight, weight_unit, purity_text,
                       disposition, conversion_note, precision_state
                  from gld_goldwork_movement
                """;
    }
}
