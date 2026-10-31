package com.zhubao.erp.goldwork.infra;

import com.zhubao.erp.goldwork.domain.GoldworkSupplement;
import com.zhubao.erp.goldwork.domain.GoldworkSupplementDirection;
import com.zhubao.erp.goldwork.domain.GoldworkSupplementRepository;
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
@ConditionalOnProperty(name = "zhubao.goldwork-supplement.repository", havingValue = "jdbc")
public class JdbcGoldworkSupplementRepository implements GoldworkSupplementRepository {

    private final DataSource dataSource;

    public JdbcGoldworkSupplementRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void insert(GoldworkSupplement supplement, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into gld_goldwork_supplement(
                    supplement_id, goldwork_code, original_line_id, prior_supplement_id, direction, owner_type,
                    source_id, source_line_no, purpose_text, piece_count, net_weight, gross_weight,
                    weight_unit, purity_text, precision_state)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, supplement.supplementId());
            statement.setString(2, supplement.goldworkCode());
            statement.setString(3, supplement.originalLineId());
            setText(statement, 4, supplement.priorSupplementId());
            statement.setString(5, supplement.direction().name());
            statement.setString(6, supplement.owner().name());
            statement.setString(7, supplement.sourceId());
            if (supplement.sourceLineNo() == null) {
                statement.setNull(8, Types.INTEGER);
            } else {
                statement.setInt(8, supplement.sourceLineNo());
            }
            statement.setString(9, supplement.purpose());
            setDecimal(statement, 10, supplement.pieceCount());
            setDecimal(statement, 11, supplement.netWeight());
            setDecimal(statement, 12, supplement.grossWeight());
            setText(statement, 13, supplement.weightUnit());
            setText(statement, 14, supplement.purity());
            statement.setString(15, supplement.precisionState());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存补料", ex);
        }
    }

    @Override
    public Optional<GoldworkSupplement> find(String supplementId) {
        if (supplementId == null || supplementId.isBlank()) {
            return Optional.empty();
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectSql() + " where supplement_id=?")) {
            statement.setString(1, supplementId.trim());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取补料", ex);
        }
    }

    @Override
    public List<GoldworkSupplement> list(String goldworkCode) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     selectSql() + " where goldwork_code=? order by supplement_id")) {
            statement.setString(1, goldworkCode);
            try (ResultSet rows = statement.executeQuery()) {
                List<GoldworkSupplement> found = new ArrayList<>();
                while (rows.next()) {
                    found.add(map(rows));
                }
                return found;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取补料", ex);
        }
    }

    private static GoldworkSupplement map(ResultSet rows) throws SQLException {
        int line = rows.getInt("source_line_no");
        return new GoldworkSupplement(
                rows.getString("supplement_id"),
                rows.getString("goldwork_code"),
                rows.getString("original_line_id"),
                rows.getString("prior_supplement_id"),
                GoldworkSupplementDirection.valueOf(rows.getString("direction")),
                OwnerType.valueOf(rows.getString("owner_type")),
                rows.getString("source_id"),
                rows.wasNull() ? null : line,
                rows.getString("purpose_text"),
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
                select supplement_id, goldwork_code, original_line_id, prior_supplement_id, direction, owner_type,
                       source_id, source_line_no, purpose_text, piece_count, net_weight, gross_weight,
                       weight_unit, purity_text, precision_state
                  from gld_goldwork_supplement
                """;
    }
}
