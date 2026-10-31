package com.zhubao.erp.goldwork.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.goldwork.domain.GoldworkMaterialLine;
import com.zhubao.erp.goldwork.domain.GoldworkMaterialRepository;
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
@ConditionalOnProperty(name = "zhubao.goldwork-material.repository", havingValue = "jdbc")
public class JdbcGoldworkMaterialRepository implements GoldworkMaterialRepository {

    private final DataSource dataSource;

    public JdbcGoldworkMaterialRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void insert(GoldworkMaterialLine line, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into gld_goldwork_material(
                    line_id, goldwork_code, owner_type, source_id, source_line_no,
                    piece_count, net_weight, gross_weight, weight_unit, purity_text, precision_state)
                values (?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, line.lineId());
            statement.setString(2, line.goldworkCode());
            statement.setString(3, line.owner().name());
            statement.setString(4, line.sourceId());
            if (line.sourceLineNo() == null) {
                statement.setNull(5, Types.INTEGER);
            } else {
                statement.setInt(5, line.sourceLineNo());
            }
            setDecimal(statement, 6, line.pieceCount());
            setDecimal(statement, 7, line.netWeight());
            setDecimal(statement, 8, line.grossWeight());
            if (line.weightUnit().isEmpty()) {
                statement.setNull(9, Types.VARCHAR);
            } else {
                statement.setString(9, line.weightUnit());
            }
            if (line.purity().isEmpty()) {
                statement.setNull(10, Types.VARCHAR);
            } else {
                statement.setString(10, line.purity());
            }
            statement.setString(11, line.precisionState());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存金料明细", ex);
        }
    }

    @Override
    public Optional<GoldworkMaterialLine> find(String lineId) {
        if (lineId == null || lineId.isBlank()) {
            return Optional.empty();
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectSql() + " where line_id=?")) {
            statement.setString(1, lineId.trim());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取金料明细", ex);
        }
    }

    @Override
    public List<GoldworkMaterialLine> list(String goldworkCode) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectSql() + " where goldwork_code=? order by line_id")) {
            statement.setString(1, goldworkCode);
            try (ResultSet rows = statement.executeQuery()) {
                List<GoldworkMaterialLine> found = new ArrayList<>();
                while (rows.next()) {
                    found.add(map(rows));
                }
                return found;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取金料明细", ex);
        }
    }

    private static GoldworkMaterialLine map(ResultSet rows) throws SQLException {
        int line = rows.getInt("source_line_no");
        return new GoldworkMaterialLine(
                rows.getString("line_id"),
                rows.getString("goldwork_code"),
                OwnerType.valueOf(rows.getString("owner_type")),
                rows.getString("source_id"),
                rows.wasNull() ? null : line,
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

    private static String selectSql() {
        return """
                select line_id, goldwork_code, owner_type, source_id, source_line_no,
                       piece_count, net_weight, gross_weight, weight_unit, purity_text, precision_state
                  from gld_goldwork_material
                """;
    }
}
