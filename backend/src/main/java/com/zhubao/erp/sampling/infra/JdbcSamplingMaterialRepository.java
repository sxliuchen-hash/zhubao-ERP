package com.zhubao.erp.sampling.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.sampling.domain.SamplingMaterialRepository;
import com.zhubao.erp.sampling.domain.SamplingMaterialUse;
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
@ConditionalOnProperty(name = "zhubao.sampling-material.repository", havingValue = "jdbc")
public class JdbcSamplingMaterialRepository implements SamplingMaterialRepository {

    private final DataSource dataSource;

    public JdbcSamplingMaterialRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void insert(SamplingMaterialUse use, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into smp_sampling_material_use(
                    use_id, sampling_code, owner_type, source_id, source_line_no, stock_move_id,
                    material_code, batch_code, quantity, unit_name, weight_state, weight_value,
                    returned_quantity, input_quantity, version_no)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            bind(statement, use);
            statement.executeUpdate();
        } catch (SQLException ex) {
            if (duplicate(ex)) {
                throw new BusinessException("领用编号冲突，请稍后重试");
            }
            throw new IllegalStateException("无法保存打样领用", ex);
        }
    }

    @Override
    public Optional<SamplingMaterialUse> find(String useId) {
        if (useId == null || useId.isBlank()) {
            return Optional.empty();
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectSql() + " where use_id=?")) {
            statement.setString(1, useId.trim());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取打样领用", ex);
        }
    }

    @Override
    public Optional<SamplingMaterialUse> lock(String useId, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement(selectSql() + " where use_id=? for update")) {
            statement.setString(1, useId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法锁定打样领用", ex);
        }
    }

    @Override
    public List<SamplingMaterialUse> list(String samplingCode) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectSql() + " where sampling_code=? order by use_id")) {
            statement.setString(1, samplingCode);
            try (ResultSet rows = statement.executeQuery()) {
                List<SamplingMaterialUse> found = new ArrayList<>();
                while (rows.next()) {
                    found.add(map(rows));
                }
                return found;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取打样领用", ex);
        }
    }

    @Override
    public List<SamplingMaterialUse> lockSource(String sourceId, Integer sourceLineNo, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement(selectSql() + """
                 where source_id=? and ((? is null and source_line_no is null) or source_line_no=?)
                 for update
                """)) {
            statement.setString(1, sourceId);
            if (sourceLineNo == null) {
                statement.setNull(2, Types.INTEGER);
                statement.setNull(3, Types.INTEGER);
            } else {
                statement.setInt(2, sourceLineNo);
                statement.setInt(3, sourceLineNo);
            }
            try (ResultSet rows = statement.executeQuery()) {
                List<SamplingMaterialUse> found = new ArrayList<>();
                while (rows.next()) {
                    found.add(map(rows));
                }
                return found;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法锁定打样来源", ex);
        }
    }

    @Override
    public SamplingMaterialUse save(SamplingMaterialUse next, long expectedVersion, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                update smp_sampling_material_use
                   set returned_quantity=?, input_quantity=?, version_no=?
                 where use_id=? and version_no=?
                """)) {
            statement.setBigDecimal(1, next.returnedQuantity());
            if (next.inputQuantity() == null) {
                statement.setNull(2, Types.DECIMAL);
            } else {
                statement.setBigDecimal(2, next.inputQuantity());
            }
            statement.setLong(3, next.version());
            statement.setString(4, next.useId());
            statement.setLong(5, expectedVersion);
            if (statement.executeUpdate() == 0) {
                throw new BusinessException("领用已变化，请刷新后重试");
            }
            return next;
        } catch (SQLException ex) {
            throw new IllegalStateException("无法更新打样领用", ex);
        }
    }

    private static void bind(PreparedStatement statement, SamplingMaterialUse use) throws SQLException {
        statement.setString(1, use.useId());
        statement.setString(2, use.samplingCode());
        statement.setString(3, use.owner().name());
        statement.setString(4, use.sourceId());
        if (use.sourceLineNo() == null) {
            statement.setNull(5, Types.INTEGER);
        } else {
            statement.setInt(5, use.sourceLineNo());
        }
        statement.setString(6, use.stockMoveId());
        statement.setString(7, use.materialCode());
        statement.setString(8, use.batchCode());
        statement.setBigDecimal(9, use.quantity());
        statement.setString(10, use.unit());
        statement.setString(11, use.weight().state().name());
        if (use.weight().value() == null) {
            statement.setNull(12, Types.DECIMAL);
        } else {
            statement.setBigDecimal(12, use.weight().value());
        }
        statement.setBigDecimal(13, use.returnedQuantity());
        if (use.inputQuantity() == null) {
            statement.setNull(14, Types.DECIMAL);
        } else {
            statement.setBigDecimal(14, use.inputQuantity());
        }
        statement.setLong(15, use.version());
    }

    private static SamplingMaterialUse map(ResultSet rows) throws SQLException {
        BigDecimal weight = rows.getBigDecimal("weight_value");
        StockMeasurement.Weight measured = "KNOWN".equals(rows.getString("weight_state"))
                ? StockMeasurement.known(weight) : StockMeasurement.unknown();
        int line = rows.getInt("source_line_no");
        Integer sourceLine = rows.wasNull() ? null : line;
        return new SamplingMaterialUse(
                rows.getString("use_id"),
                rows.getString("sampling_code"),
                OwnerType.valueOf(rows.getString("owner_type")),
                rows.getString("source_id"),
                sourceLine,
                rows.getString("stock_move_id"),
                rows.getString("material_code"),
                rows.getString("batch_code"),
                rows.getBigDecimal("quantity"),
                rows.getString("unit_name"),
                measured,
                rows.getBigDecimal("returned_quantity"),
                rows.getBigDecimal("input_quantity"),
                rows.getLong("version_no")
        );
    }

    private static String selectSql() {
        return """
                select use_id, sampling_code, owner_type, source_id, source_line_no, stock_move_id,
                       material_code, batch_code, quantity, unit_name, weight_state, weight_value,
                       returned_quantity, input_quantity, version_no
                  from smp_sampling_material_use
                """;
    }

    private static boolean duplicate(SQLException ex) {
        String state = ex.getSQLState();
        return ex.getErrorCode() == 1062 || ex.getErrorCode() == 23505
                || "23000".equals(state) || "23505".equals(state)
                || (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("unique"));
    }
}
