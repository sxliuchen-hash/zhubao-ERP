package com.zhubao.erp.finance.infra;

import com.zhubao.erp.finance.domain.FixedAssetCustodyRepository;
import com.zhubao.erp.finance.domain.FixedAssetCustodyTransfer;
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

@Repository
@ConditionalOnProperty(name = "zhubao.fixed-asset-custody.repository", havingValue = "jdbc")
public class JdbcFixedAssetCustodyRepository implements FixedAssetCustodyRepository {

    private final DataSource dataSource;

    public JdbcFixedAssetCustodyRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void insert(FixedAssetCustodyTransfer transfer, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into fin_fixed_asset_custody(
                    transfer_id, asset_code, from_location, to_location, from_custodian, to_custodian,
                    from_status, to_status, version_no)
                values (?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, transfer.transferId());
            statement.setString(2, transfer.assetCode());
            setText(statement, 3, transfer.fromLocation());
            setText(statement, 4, transfer.toLocation());
            setText(statement, 5, transfer.fromCustodian());
            setText(statement, 6, transfer.toCustodian());
            setText(statement, 7, transfer.fromStatus());
            setText(statement, 8, transfer.toStatus());
            statement.setLong(9, transfer.version());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存资产交接", ex);
        }
    }

    @Override
    public List<FixedAssetCustodyTransfer> list(String assetCode) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select transfer_id, asset_code, from_location, to_location, from_custodian, to_custodian,
                            from_status, to_status, version_no
                       from fin_fixed_asset_custody
                      where asset_code=?
                      order by version_no
                     """)) {
            statement.setString(1, assetCode);
            try (ResultSet rows = statement.executeQuery()) {
                List<FixedAssetCustodyTransfer> found = new ArrayList<>();
                while (rows.next()) {
                    found.add(new FixedAssetCustodyTransfer(
                            rows.getString("transfer_id"),
                            rows.getString("asset_code"),
                            rows.getString("from_location"),
                            rows.getString("to_location"),
                            rows.getString("from_custodian"),
                            rows.getString("to_custodian"),
                            rows.getString("from_status"),
                            rows.getString("to_status"),
                            rows.getLong("version_no")
                    ));
                }
                return found;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取资产交接", ex);
        }
    }

    private static void setText(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null || value.isEmpty()) {
            statement.setNull(index, Types.VARCHAR);
            return;
        }
        statement.setString(index, value);
    }
}
