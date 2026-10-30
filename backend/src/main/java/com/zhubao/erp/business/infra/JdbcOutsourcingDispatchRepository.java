package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.OutsourcingDispatch;
import com.zhubao.erp.business.domain.OutsourcingDispatchRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.work.app.WorkSessionBindings;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.outsourcing-dispatch.repository", havingValue = "jdbc")
public class JdbcOutsourcingDispatchRepository implements OutsourcingDispatchRepository {

    private final DataSource dataSource;

    public JdbcOutsourcingDispatchRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(OutsourcingDispatch dispatch, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into biz_outsourcing_dispatch(
                    dispatch_id, request_id, task_id, party_id, source_bag_id, sent_bag_id,
                    quantity, source_key, owner_type, owner_ref, version_no)
                values (?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, dispatch.dispatchId());
            statement.setString(2, dispatch.requestId());
            statement.setString(3, dispatch.taskId());
            statement.setString(4, dispatch.partyId());
            statement.setString(5, dispatch.sourceBagId());
            statement.setString(6, dispatch.sentBagId());
            statement.setBigDecimal(7, dispatch.quantity());
            statement.setString(8, dispatch.sourceKey());
            statement.setString(9, dispatch.ownerType().name());
            statement.setString(10, dispatch.ownerRef());
            statement.setInt(11, dispatch.version());
            statement.executeUpdate();
        } catch (SQLException ex) {
            if (duplicate(ex)) {
                throw new BusinessException("该袋已发外");
            }
            throw new IllegalStateException("无法保存外协发外", ex);
        }
    }

    @Override
    public Optional<OutsourcingDispatch> find(String dispatchId) {
        return list("dispatch_id=?", dispatchId).stream().findFirst();
    }

    @Override
    public Optional<OutsourcingDispatch> findByRequest(String requestId) {
        return list("request_id=?", requestId).stream().findFirst();
    }

    @Override
    public Optional<OutsourcingDispatch> findBySentBag(String bagId) {
        return list("sent_bag_id=?", bagId).stream().findFirst();
    }

    @Override
    public List<OutsourcingDispatch> listByTask(String taskId) {
        return list("task_id=?", taskId);
    }

    @Override
    public List<OutsourcingDispatch> listByParty(String partyId) {
        return list("party_id=?", partyId);
    }

    private List<OutsourcingDispatch> list(String where, String value) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select * from biz_outsourcing_dispatch where " + where + " order by dispatch_id")) {
            statement.setString(1, value);
            try (ResultSet rows = statement.executeQuery()) {
                List<OutsourcingDispatch> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(map(rows));
                }
                return result;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取外协发外", ex);
        }
    }

    private OutsourcingDispatch map(ResultSet rows) throws SQLException {
        return new OutsourcingDispatch(
                rows.getString("dispatch_id"),
                rows.getString("request_id"),
                rows.getString("task_id"),
                rows.getString("party_id"),
                rows.getString("source_bag_id"),
                rows.getString("sent_bag_id"),
                rows.getBigDecimal("quantity"),
                rows.getString("source_key"),
                OwnerType.valueOf(rows.getString("owner_type")),
                rows.getString("owner_ref"),
                rows.getInt("version_no")
        );
    }

    private static boolean duplicate(SQLException ex) {
        String state = ex.getSQLState();
        return ex.getErrorCode() == 1062 || ex.getErrorCode() == 23505
                || "23000".equals(state) || "23505".equals(state);
    }
}
