package com.zhubao.erp.scan.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.scan.domain.PublicDeliveryAccess;
import com.zhubao.erp.scan.domain.PublicDeliveryAccessRepository;
import com.zhubao.erp.work.app.WorkSessionBindings;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.public-delivery.repository", havingValue = "jdbc")
public class JdbcPublicDeliveryAccessRepository implements PublicDeliveryAccessRepository {

    private final DataSource dataSource;

    public JdbcPublicDeliveryAccessRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(PublicDeliveryAccess access, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into biz_public_delivery_access(
                    access_id, delivery_id, customer_id, bag_codes, photo_ids, enabled, version_no)
                values (?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, access.accessId());
            statement.setString(2, access.deliveryId());
            statement.setLong(3, access.customerId());
            statement.setString(4, String.join(",", access.bagCodes()));
            statement.setString(5, String.join(",", access.publicPhotoIds()));
            statement.setBoolean(6, access.enabled());
            statement.setInt(7, access.version());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存公开访问标识", ex);
        }
    }

    @Override
    public Optional<PublicDeliveryAccess> find(String accessId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select * from biz_public_delivery_access where access_id=?")) {
            statement.setString(1, accessId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取公开访问标识", ex);
        }
    }

    @Override
    public void disable(String accessId, int expectedVersion, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                update biz_public_delivery_access
                   set enabled=false, version_no=version_no+1
                 where access_id=? and version_no=? and enabled=true
                """)) {
            statement.setString(1, accessId);
            statement.setInt(2, expectedVersion);
            if (statement.executeUpdate() != 1) {
                throw new BusinessException("公开链接已停用");
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法停用公开访问", ex);
        }
    }

    private PublicDeliveryAccess map(ResultSet rows) throws SQLException {
        return new PublicDeliveryAccess(
                rows.getString("access_id"),
                rows.getString("delivery_id"),
                rows.getLong("customer_id"),
                split(rows.getString("bag_codes")),
                split(rows.getString("photo_ids")),
                rows.getBoolean("enabled"),
                rows.getInt("version_no")
        );
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).filter(item -> !item.isBlank()).toList();
    }
}
