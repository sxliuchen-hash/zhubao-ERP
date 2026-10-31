package com.zhubao.erp.finance.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.FixedAssetPhysical;
import com.zhubao.erp.finance.domain.FixedAssetPhysicalRepository;
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
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.fixed-asset-card.repository", havingValue = "jdbc")
public class JdbcFixedAssetPhysicalRepository implements FixedAssetPhysicalRepository {

    private final DataSource dataSource;

    public JdbcFixedAssetPhysicalRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<FixedAssetPhysical> find(String assetCode) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectSql() + " where asset_code=?")) {
            statement.setString(1, assetCode);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取资产实物字段", ex);
        }
    }

    @Override
    public Optional<FixedAssetPhysical> lock(String assetCode, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement(selectSql() + " where asset_code=? for update")) {
            statement.setString(1, assetCode);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法锁定资产实物字段", ex);
        }
    }

    @Override
    public void insert(FixedAssetPhysical row, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into fin_fixed_asset_physical(
                    asset_code, model_name, serial_number, location_name, custodian_name, use_status, version_no)
                values (?,?,?,?,?,?,?)
                """)) {
            bind(statement, row);
            statement.executeUpdate();
        } catch (SQLException ex) {
            if (duplicate(ex)) {
                throw new BusinessException("旧卡已接续，不能重建");
            }
            throw new IllegalStateException("无法保存资产实物字段", ex);
        }
    }

    @Override
    public void replace(FixedAssetPhysical row, long expectedVersion, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                update fin_fixed_asset_physical
                   set model_name=?, serial_number=?, location_name=?, custodian_name=?, use_status=?, version_no=?
                 where asset_code=? and version_no=?
                """)) {
            setText(statement, 1, row.modelName());
            setText(statement, 2, row.serialNumber());
            setText(statement, 3, row.locationName());
            setText(statement, 4, row.custodianName());
            setText(statement, 5, row.useStatus());
            statement.setLong(6, row.version());
            statement.setString(7, row.assetCode());
            statement.setLong(8, expectedVersion);
            if (statement.executeUpdate() != 1) {
                throw new BusinessException("资产卡片版本已变化");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (SQLException ex) {
            throw new IllegalStateException("无法更新资产实物字段", ex);
        }
    }

    private static void bind(PreparedStatement statement, FixedAssetPhysical row) throws SQLException {
        statement.setString(1, row.assetCode());
        setText(statement, 2, row.modelName());
        setText(statement, 3, row.serialNumber());
        setText(statement, 4, row.locationName());
        setText(statement, 5, row.custodianName());
        setText(statement, 6, row.useStatus());
        statement.setLong(7, row.version());
    }

    private static void setText(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null || value.isEmpty()) {
            statement.setNull(index, Types.VARCHAR);
            return;
        }
        statement.setString(index, value);
    }

    private static FixedAssetPhysical map(ResultSet rows) throws SQLException {
        return new FixedAssetPhysical(
                rows.getString("asset_code"),
                rows.getString("model_name"),
                rows.getString("serial_number"),
                rows.getString("location_name"),
                rows.getString("custodian_name"),
                rows.getString("use_status"),
                rows.getLong("version_no")
        );
    }

    private static String selectSql() {
        return """
                select asset_code, model_name, serial_number, location_name, custodian_name, use_status, version_no
                  from fin_fixed_asset_physical
                """;
    }

    private static boolean duplicate(SQLException ex) {
        String state = ex.getSQLState();
        return ex.getErrorCode() == 1062 || ex.getErrorCode() == 23505
                || "23000".equals(state) || "23505".equals(state)
                || (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("unique"));
    }
}
