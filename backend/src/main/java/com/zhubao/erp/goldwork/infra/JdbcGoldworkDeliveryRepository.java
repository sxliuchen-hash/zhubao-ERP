package com.zhubao.erp.goldwork.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.goldwork.domain.GoldworkDelivery;
import com.zhubao.erp.goldwork.domain.GoldworkDeliveryRepository;
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
@ConditionalOnProperty(name = "zhubao.goldwork-delivery.repository", havingValue = "jdbc")
public class JdbcGoldworkDeliveryRepository implements GoldworkDeliveryRepository {

    private final DataSource dataSource;

    public JdbcGoldworkDeliveryRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void insert(GoldworkDelivery delivery, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into gld_goldwork_delivery(
                    delivery_id, goldwork_code, quality_id, piece_count, net_weight, weight_unit, purity_text, fee_state)
                values (?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, delivery.deliveryId());
            statement.setString(2, delivery.goldworkCode());
            statement.setString(3, delivery.qualityId());
            setDecimal(statement, 4, delivery.pieceCount());
            setDecimal(statement, 5, delivery.netWeight());
            setText(statement, 6, delivery.weightUnit());
            setText(statement, 7, delivery.purity());
            statement.setString(8, delivery.feeState());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存金工交付", ex);
        }
    }

    @Override
    public Optional<GoldworkDelivery> find(String deliveryId) {
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
            throw new IllegalStateException("无法读取金工交付", ex);
        }
    }

    @Override
    public boolean existsForQuality(String qualityId, WorkSession session) {
        if (qualityId == null || qualityId.isBlank()) {
            return false;
        }
        Connection connection = session == null ? null : WorkSessionBindings.requiredJdbc(session);
        if (connection == null) {
            try (Connection own = dataSource.getConnection()) {
                return exists(own, qualityId);
            } catch (SQLException ex) {
                throw new IllegalStateException("无法核对金工交付", ex);
            }
        }
        return exists(connection, qualityId);
    }

    @Override
    public List<GoldworkDelivery> list(String goldworkCode) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     selectSql() + " where goldwork_code=? order by delivery_id")) {
            statement.setString(1, goldworkCode);
            try (ResultSet rows = statement.executeQuery()) {
                List<GoldworkDelivery> found = new ArrayList<>();
                while (rows.next()) {
                    found.add(map(rows));
                }
                return found;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取金工交付", ex);
        }
    }

    private boolean exists(Connection connection, String qualityId) {
        try (PreparedStatement statement = connection.prepareStatement(
                "select delivery_id from gld_goldwork_delivery where quality_id=? for update")) {
            statement.setString(1, qualityId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法核对金工交付", ex);
        }
    }

    private static GoldworkDelivery map(ResultSet rows) throws SQLException {
        return new GoldworkDelivery(
                rows.getString("delivery_id"),
                rows.getString("goldwork_code"),
                rows.getString("quality_id"),
                rows.getBigDecimal("piece_count"),
                rows.getBigDecimal("net_weight"),
                rows.getString("weight_unit"),
                rows.getString("purity_text"),
                rows.getString("fee_state")
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
                select delivery_id, goldwork_code, quality_id, piece_count, net_weight, weight_unit, purity_text, fee_state
                  from gld_goldwork_delivery
                """;
    }
}
