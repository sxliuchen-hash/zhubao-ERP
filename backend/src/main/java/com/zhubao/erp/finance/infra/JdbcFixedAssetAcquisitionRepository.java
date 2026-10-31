package com.zhubao.erp.finance.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.FixedAssetAcquisition;
import com.zhubao.erp.finance.domain.FixedAssetAcquisitionRepository;
import com.zhubao.erp.finance.domain.FixedAssetAcquisitionSource;
import com.zhubao.erp.finance.domain.FixedAssetAmountState;
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
@ConditionalOnProperty(name = "zhubao.fixed-asset-acquisition.repository", havingValue = "jdbc")
public class JdbcFixedAssetAcquisitionRepository implements FixedAssetAcquisitionRepository {

    private final DataSource dataSource;

    public JdbcFixedAssetAcquisitionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void insert(FixedAssetAcquisition acquisition, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into fin_fixed_asset_acquisition(
                    acquisition_id, asset_code, source_kind, amount_state, supplier_name, acquired_on,
                    handover_location, handover_receiver, payable_code, covered_payable_code, payment_code, payment_voucher)
                values (?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, acquisition.acquisitionId());
            setText(statement, 2, acquisition.assetCode());
            statement.setString(3, acquisition.source().name());
            statement.setString(4, acquisition.amountState().name());
            statement.setString(5, acquisition.supplierName());
            statement.setString(6, acquisition.acquiredOn());
            setText(statement, 7, acquisition.handoverLocation());
            setText(statement, 8, acquisition.handoverReceiver());
            setText(statement, 9, acquisition.payableCode());
            setText(statement, 10, acquisition.coveredPayableCode());
            setText(statement, 11, acquisition.paymentCode());
            setText(statement, 12, acquisition.paymentVoucher());
            statement.executeUpdate();
        } catch (SQLException ex) {
            if (duplicate(ex)) {
                throw new BusinessException("这笔应付或资产已经有购置来源");
            }
            throw new IllegalStateException("无法保存资产购置来源", ex);
        }
    }

    @Override
    public Optional<FixedAssetAcquisition> find(String acquisitionId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectSql() + " where acquisition_id=?")) {
            statement.setString(1, acquisitionId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取资产购置来源", ex);
        }
    }

    @Override
    public Optional<FixedAssetAcquisition> lockByAsset(String assetCode, WorkSession session) {
        if (assetCode == null || assetCode.isBlank()) {
            return Optional.empty();
        }
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement(
                selectSql() + " where asset_code=? for update")) {
            statement.setString(1, assetCode);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法锁定资产购置来源", ex);
        }
    }

    @Override
    public boolean coversPayable(String payableCode, WorkSession session) {
        if (payableCode == null || payableCode.isBlank()) {
            return false;
        }
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                select acquisition_id from fin_fixed_asset_acquisition
                 where payable_code=? or covered_payable_code=?
                 for update
                """)) {
            statement.setString(1, payableCode);
            statement.setString(2, payableCode);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法核对资产应付来源", ex);
        }
    }

    @Override
    public List<FixedAssetAcquisition> list() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectSql() + " order by acquisition_id");
             ResultSet rows = statement.executeQuery()) {
            List<FixedAssetAcquisition> found = new ArrayList<>();
            while (rows.next()) {
                found.add(map(rows));
            }
            return found;
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取资产购置来源", ex);
        }
    }

    private static void setText(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null || value.isEmpty()) {
            statement.setNull(index, Types.VARCHAR);
            return;
        }
        statement.setString(index, value);
    }

    private static FixedAssetAcquisition map(ResultSet rows) throws SQLException {
        return new FixedAssetAcquisition(
                rows.getString("acquisition_id"),
                text(rows.getString("asset_code")),
                FixedAssetAcquisitionSource.valueOf(rows.getString("source_kind")),
                FixedAssetAmountState.valueOf(rows.getString("amount_state")),
                text(rows.getString("supplier_name")),
                text(rows.getString("acquired_on")),
                rows.getString("handover_location"),
                rows.getString("handover_receiver"),
                text(rows.getString("payable_code")),
                text(rows.getString("covered_payable_code")),
                text(rows.getString("payment_code")),
                text(rows.getString("payment_voucher"))
        );
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String selectSql() {
        return """
                select acquisition_id, asset_code, source_kind, amount_state, supplier_name, acquired_on,
                       handover_location, handover_receiver, payable_code, covered_payable_code,
                       payment_code, payment_voucher
                  from fin_fixed_asset_acquisition
                """;
    }

    private static boolean duplicate(SQLException ex) {
        String state = ex.getSQLState();
        return ex.getErrorCode() == 1062 || ex.getErrorCode() == 23505
                || "23000".equals(state) || "23505".equals(state)
                || (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("unique"));
    }
}
