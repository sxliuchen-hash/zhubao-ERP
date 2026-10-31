package com.zhubao.erp.goldwork.infra;

import com.zhubao.erp.goldwork.domain.GoldworkQualityDecision;
import com.zhubao.erp.goldwork.domain.GoldworkQualityOutcome;
import com.zhubao.erp.goldwork.domain.GoldworkQualityRepository;
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
@ConditionalOnProperty(name = "zhubao.goldwork-quality.repository", havingValue = "jdbc")
public class JdbcGoldworkQualityRepository implements GoldworkQualityRepository {

    private final DataSource dataSource;

    public JdbcGoldworkQualityRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void insert(GoldworkQualityDecision decision, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into gld_goldwork_quality(
                    quality_id, goldwork_code, report_id, prior_quality_id, round_no, outcome,
                    piece_count, net_weight, weight_unit, purity_text, note_text)
                values (?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, decision.qualityId());
            statement.setString(2, decision.goldworkCode());
            statement.setString(3, decision.reportId());
            setText(statement, 4, decision.priorQualityId());
            statement.setInt(5, decision.roundNo());
            statement.setString(6, decision.outcome().name());
            setDecimal(statement, 7, decision.pieceCount());
            setDecimal(statement, 8, decision.netWeight());
            setText(statement, 9, decision.weightUnit());
            setText(statement, 10, decision.purity());
            setText(statement, 11, decision.note());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存质量记录", ex);
        }
    }

    @Override
    public Optional<GoldworkQualityDecision> find(String qualityId) {
        if (qualityId == null || qualityId.isBlank()) {
            return Optional.empty();
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectSql() + " where quality_id=?")) {
            statement.setString(1, qualityId.trim());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取质量记录", ex);
        }
    }

    @Override
    public List<GoldworkQualityDecision> list(String goldworkCode) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     selectSql() + " where goldwork_code=? order by quality_id")) {
            statement.setString(1, goldworkCode);
            try (ResultSet rows = statement.executeQuery()) {
                List<GoldworkQualityDecision> found = new ArrayList<>();
                while (rows.next()) {
                    found.add(map(rows));
                }
                return found;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取质量记录", ex);
        }
    }

    private static GoldworkQualityDecision map(ResultSet rows) throws SQLException {
        return new GoldworkQualityDecision(
                rows.getString("quality_id"),
                rows.getString("goldwork_code"),
                rows.getString("report_id"),
                rows.getString("prior_quality_id"),
                rows.getInt("round_no"),
                GoldworkQualityOutcome.valueOf(rows.getString("outcome")),
                rows.getBigDecimal("piece_count"),
                rows.getBigDecimal("net_weight"),
                rows.getString("weight_unit"),
                rows.getString("purity_text"),
                rows.getString("note_text")
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
                select quality_id, goldwork_code, report_id, prior_quality_id, round_no, outcome,
                       piece_count, net_weight, weight_unit, purity_text, note_text
                  from gld_goldwork_quality
                """;
    }
}
