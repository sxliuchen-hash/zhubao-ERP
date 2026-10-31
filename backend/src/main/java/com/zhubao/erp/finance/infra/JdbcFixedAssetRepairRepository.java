package com.zhubao.erp.finance.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.FixedAssetRepair;
import com.zhubao.erp.finance.domain.FixedAssetRepairRepository;
import com.zhubao.erp.finance.domain.FixedAssetRepairState;
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
@ConditionalOnProperty(name = "zhubao.fixed-asset-repair.repository", havingValue = "jdbc")
public class JdbcFixedAssetRepairRepository implements FixedAssetRepairRepository {

    private final DataSource dataSource;

    public JdbcFixedAssetRepairRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void insert(FixedAssetRepair repair, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into fin_fixed_asset_repair(
                    repair_id, asset_code, repaired_on, handover_name, evidence_code, repair_state, payable_code)
                values (?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, repair.repairId());
            statement.setString(2, repair.assetCode());
            statement.setString(3, repair.repairedOn());
            statement.setString(4, repair.handoverName());
            statement.setString(5, repair.evidenceCode());
            statement.setString(6, repair.state().name());
            setText(statement, 7, repair.payableCode());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存维修记录", ex);
        }
    }

    @Override
    public Optional<FixedAssetRepair> find(String repairId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectSql() + " where repair_id=?")) {
            statement.setString(1, repairId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取维修记录", ex);
        }
    }

    @Override
    public Optional<FixedAssetRepair> lock(String repairId, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement(selectSql() + " where repair_id=? for update")) {
            statement.setString(1, repairId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法锁定维修记录", ex);
        }
    }

    @Override
    public void confirm(String repairId, String evidenceCode, String payableCode, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                update fin_fixed_asset_repair
                   set evidence_code=?, payable_code=?, repair_state=?
                 where repair_id=? and repair_state=? and payable_code is null
                """)) {
            statement.setString(1, evidenceCode);
            statement.setString(2, payableCode);
            statement.setString(3, FixedAssetRepairState.CONFIRMED.name());
            statement.setString(4, repairId);
            statement.setString(5, FixedAssetRepairState.RECORDED.name());
            if (statement.executeUpdate() != 1) {
                throw new BusinessException("维修费用已经确认");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (SQLException ex) {
            if (duplicate(ex)) {
                throw new BusinessException("这笔应付已经对应过维修");
            }
            throw new IllegalStateException("无法确认维修费用", ex);
        }
    }

    @Override
    public List<FixedAssetRepair> list(String assetCode) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectSql() + " where asset_code=? order by repair_id")) {
            statement.setString(1, assetCode);
            try (ResultSet rows = statement.executeQuery()) {
                List<FixedAssetRepair> found = new ArrayList<>();
                while (rows.next()) {
                    found.add(map(rows));
                }
                return found;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取维修记录", ex);
        }
    }

    private static FixedAssetRepair map(ResultSet rows) throws SQLException {
        String payable = rows.getString("payable_code");
        return new FixedAssetRepair(
                rows.getString("repair_id"),
                rows.getString("asset_code"),
                rows.getString("repaired_on") == null ? "" : rows.getString("repaired_on").trim(),
                rows.getString("handover_name") == null ? "" : rows.getString("handover_name").trim(),
                rows.getString("evidence_code") == null ? "" : rows.getString("evidence_code"),
                FixedAssetRepairState.valueOf(rows.getString("repair_state")),
                payable == null ? "" : payable
        );
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
                select repair_id, asset_code, repaired_on, handover_name, evidence_code, repair_state, payable_code
                  from fin_fixed_asset_repair
                """;
    }

    private static boolean duplicate(SQLException ex) {
        String state = ex.getSQLState();
        return ex.getErrorCode() == 1062 || ex.getErrorCode() == 23505
                || "23000".equals(state) || "23505".equals(state)
                || (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("unique"));
    }
}
