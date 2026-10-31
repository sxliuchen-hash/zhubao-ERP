package com.zhubao.erp.sampling.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.sampling.domain.SamplingDelivery;
import com.zhubao.erp.sampling.domain.SamplingDeliveryRepository;
import com.zhubao.erp.sampling.domain.SamplingVersionKind;
import com.zhubao.erp.work.app.WorkSessionBindings;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.sampling-delivery.repository", havingValue = "jdbc")
public class JdbcSamplingDeliveryRepository implements SamplingDeliveryRepository {

    private final DataSource dataSource;

    public JdbcSamplingDeliveryRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void insert(SamplingDelivery delivery, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into smp_sampling_delivery(
                    delivery_id, sampling_code, version_kind, version_no, material_use_id,
                    quantity, unit_name, credential_code, delivered_on)
                values (?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, delivery.deliveryId());
            statement.setString(2, delivery.samplingCode());
            statement.setString(3, delivery.versionKind().name());
            statement.setInt(4, delivery.versionNo());
            if (delivery.materialUseId().isEmpty()) {
                statement.setNull(5, Types.VARCHAR);
            } else {
                statement.setString(5, delivery.materialUseId());
            }
            statement.setBigDecimal(6, delivery.quantity());
            statement.setString(7, delivery.unit());
            statement.setString(8, delivery.credentialCode());
            statement.setString(9, delivery.deliveredOn());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存打样交付", ex);
        }
    }

    @Override
    public Optional<SamplingDelivery> find(String deliveryId) {
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
            throw new IllegalStateException("无法读取打样交付", ex);
        }
    }

    @Override
    public List<SamplingDelivery> list(String samplingCode) {
        return query(selectSql() + " where sampling_code=? order by delivery_id", samplingCode, null, 0, false);
    }

    @Override
    public List<SamplingDelivery> lockVersion(String samplingCode, SamplingVersionKind kind, int versionNo, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement(
                selectSql() + " where sampling_code=? and version_kind=? and version_no=? for update")) {
            statement.setString(1, samplingCode);
            statement.setString(2, kind.name());
            statement.setInt(3, versionNo);
            try (ResultSet rows = statement.executeQuery()) {
                List<SamplingDelivery> found = new ArrayList<>();
                while (rows.next()) {
                    found.add(map(rows));
                }
                return found;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法锁定打样交付", ex);
        }
    }

    private List<SamplingDelivery> query(String sql, String samplingCode, SamplingVersionKind kind, int versionNo, boolean versioned) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, samplingCode);
            if (versioned) {
                statement.setString(2, kind.name());
                statement.setInt(3, versionNo);
            }
            try (ResultSet rows = statement.executeQuery()) {
                List<SamplingDelivery> found = new ArrayList<>();
                while (rows.next()) {
                    found.add(map(rows));
                }
                return found;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取打样交付", ex);
        }
    }

    private static SamplingDelivery map(ResultSet rows) throws SQLException {
        return new SamplingDelivery(
                rows.getString("delivery_id"),
                rows.getString("sampling_code"),
                SamplingVersionKind.valueOf(rows.getString("version_kind")),
                rows.getInt("version_no"),
                rows.getString("material_use_id"),
                rows.getBigDecimal("quantity"),
                rows.getString("unit_name"),
                rows.getString("credential_code"),
                rows.getString("delivered_on")
        );
    }

    private static String selectSql() {
        return """
                select delivery_id, sampling_code, version_kind, version_no, material_use_id,
                       quantity, unit_name, credential_code, delivered_on
                  from smp_sampling_delivery
                """;
    }
}
