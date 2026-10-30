package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.OutsourcingReturn;
import com.zhubao.erp.business.domain.OutsourcingReturnRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockMeasurement;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.outsourcing-return.repository", havingValue = "jdbc")
public class JdbcOutsourcingReturnRepository implements OutsourcingReturnRepository {

    private final DataSource dataSource;

    public JdbcOutsourcingReturnRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(OutsourcingReturn row, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into biz_outsourcing_return(
                    return_id, request_id, dispatch_id, party_id, dispatch_version,
                    received_qty, unprocessed_qty, loss_qty, weight, weight_known,
                    received_bag_id, unprocessed_bag_id, loss_bag_id, remainder_bag_id)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, row.returnId());
            statement.setString(2, row.requestId());
            statement.setString(3, row.dispatchId());
            statement.setString(4, row.partyId());
            statement.setInt(5, row.dispatchVersion());
            statement.setBigDecimal(6, row.receivedQty());
            statement.setBigDecimal(7, row.unprocessedQty());
            statement.setBigDecimal(8, row.lossQty());
            if (row.weight().state() == StockMeasurement.WeightState.KNOWN) {
                statement.setBigDecimal(9, row.weight().value());
                statement.setBoolean(10, true);
            } else {
                statement.setBigDecimal(9, null);
                statement.setBoolean(10, false);
            }
            statement.setString(11, row.receivedBagId());
            statement.setString(12, row.unprocessedBagId());
            statement.setString(13, row.lossBagId());
            statement.setString(14, row.remainderBagId());
            statement.executeUpdate();
        } catch (SQLException ex) {
            if (duplicate(ex)) {
                throw new BusinessException("外协收回已存在");
            }
            throw new IllegalStateException("无法保存外协收回", ex);
        }
    }

    @Override
    public Optional<OutsourcingReturn> find(String returnId) {
        return list("return_id=?", returnId).stream().findFirst();
    }

    @Override
    public Optional<OutsourcingReturn> findByRequest(String requestId) {
        return list("request_id=?", requestId).stream().findFirst();
    }

    @Override
    public List<OutsourcingReturn> listByDispatch(String dispatchId) {
        return list("dispatch_id=?", dispatchId);
    }

    @Override
    public boolean inHouse(String bagId) {
        if (bagId == null || bagId.isBlank()) {
            return false;
        }
        return listByBag(bagId).isPresent();
    }

    @Override
    public Optional<String> currentOutParty(String bagId) {
        if (bagId == null || bagId.isBlank()) {
            return Optional.empty();
        }
        for (OutsourcingReturn row : list("remainder_bag_id=?", bagId)) {
            int max = listByDispatch(row.dispatchId()).stream().mapToInt(OutsourcingReturn::dispatchVersion).max().orElse(0);
            if (row.dispatchVersion() == max) {
                return Optional.of(row.partyId());
            }
        }
        return Optional.empty();
    }

    private Optional<OutsourcingReturn> listByBag(String bagId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select * from biz_outsourcing_return
                      where received_bag_id=? or unprocessed_bag_id=?
                     """)) {
            statement.setString(1, bagId);
            statement.setString(2, bagId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取外协收回", ex);
        }
    }

    private List<OutsourcingReturn> list(String where, String value) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select * from biz_outsourcing_return where " + where + " order by dispatch_version")) {
            statement.setString(1, value);
            try (ResultSet rows = statement.executeQuery()) {
                List<OutsourcingReturn> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(map(rows));
                }
                return result;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取外协收回", ex);
        }
    }

    private OutsourcingReturn map(ResultSet rows) throws SQLException {
        boolean known = rows.getBoolean("weight_known");
        BigDecimal weight = rows.getBigDecimal("weight");
        if (rows.wasNull()) {
            weight = null;
        }
        return new OutsourcingReturn(
                rows.getString("return_id"),
                rows.getString("request_id"),
                rows.getString("dispatch_id"),
                rows.getString("party_id"),
                rows.getInt("dispatch_version"),
                rows.getBigDecimal("received_qty"),
                rows.getBigDecimal("unprocessed_qty"),
                rows.getBigDecimal("loss_qty"),
                known ? StockMeasurement.known(weight) : StockMeasurement.unknown(),
                rows.getString("received_bag_id"),
                rows.getString("unprocessed_bag_id"),
                rows.getString("loss_bag_id"),
                rows.getString("remainder_bag_id")
        );
    }

    private static boolean duplicate(SQLException ex) {
        String state = ex.getSQLState();
        return ex.getErrorCode() == 1062 || ex.getErrorCode() == 23505
                || "23000".equals(state) || "23505".equals(state);
    }
}
