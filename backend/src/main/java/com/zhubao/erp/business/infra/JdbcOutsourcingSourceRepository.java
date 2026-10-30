package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.OutsourcingCounterpart;
import com.zhubao.erp.business.domain.OutsourcingParty;
import com.zhubao.erp.business.domain.OutsourcingSourceRepository;
import com.zhubao.erp.business.domain.OutsourcingTask;
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
@ConditionalOnProperty(name = "zhubao.outsourcing-source.repository", havingValue = "jdbc")
public class JdbcOutsourcingSourceRepository implements OutsourcingSourceRepository {

    private final DataSource dataSource;

    public JdbcOutsourcingSourceRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void saveParty(OutsourcingParty party, int expectedVersion, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try {
            Integer current = lockVersion(connection, party.partyId());
            int actual = current == null ? 0 : current;
            if (actual != expectedVersion) {
                throw conflict(expectedVersion, actual);
            }
            if (current == null) {
                insertParty(connection, party);
            } else {
                updateParty(connection, party);
            }
        } catch (SQLException ex) {
            if (duplicate(ex)) {
                throw new BusinessException("外协方已存在");
            }
            throw new IllegalStateException("无法保存外协方", ex);
        }
    }

    @Override
    public Optional<OutsourcingParty> findParty(String partyId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select * from biz_outsourcing_party where party_id=?")) {
            statement.setString(1, partyId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(mapParty(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取外协方", ex);
        }
    }

    @Override
    public Optional<OutsourcingParty> lockParty(String partyId, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement(
                "select * from biz_outsourcing_party where party_id=? for update")) {
            statement.setString(1, partyId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(mapParty(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法锁定外协方", ex);
        }
    }

    @Override
    public void saveTask(OutsourcingTask task, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into biz_outsourcing_task(
                    task_id, request_id, party_id, party_name_snapshot, business_kind, target_code,
                    node_id, bag_id, owner_type, owner_ref, version_no)
                values (?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, task.taskId());
            statement.setString(2, task.requestId());
            statement.setString(3, task.partyId());
            statement.setString(4, task.partyNameSnapshot());
            statement.setString(5, task.businessKind().name());
            statement.setString(6, task.targetCode());
            statement.setString(7, task.nodeId());
            statement.setString(8, task.bagId());
            statement.setString(9, task.ownerType().name());
            statement.setString(10, task.ownerRef());
            statement.setInt(11, task.version());
            statement.executeUpdate();
        } catch (SQLException ex) {
            if (duplicate(ex)) {
                throw new BusinessException("外协任务已存在");
            }
            throw new IllegalStateException("无法保存外协任务", ex);
        }
    }

    @Override
    public Optional<OutsourcingTask> findTask(String taskId) {
        return list("task_id=?", taskId).stream().findFirst();
    }

    @Override
    public Optional<OutsourcingTask> findTaskByRequest(String requestId) {
        return list("request_id=?", requestId).stream().findFirst();
    }

    @Override
    public List<OutsourcingTask> listByTarget(String targetCode) {
        return list("target_code=?", targetCode);
    }

    private List<OutsourcingTask> list(String where, String value) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select * from biz_outsourcing_task where " + where + " order by task_id")) {
            statement.setString(1, value);
            try (ResultSet rows = statement.executeQuery()) {
                List<OutsourcingTask> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(mapTask(rows));
                }
                return result;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取外协任务", ex);
        }
    }

    private Integer lockVersion(Connection connection, String partyId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select version_no from biz_outsourcing_party where party_id=? for update")) {
            statement.setString(1, partyId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : null;
            }
        }
    }

    private void insertParty(Connection connection, OutsourcingParty party) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into biz_outsourcing_party(party_id, party_name, counterpart, version_no)
                values (?,?,?,?)
                """)) {
            statement.setString(1, party.partyId());
            statement.setString(2, party.name());
            statement.setString(3, OutsourcingCounterpart.OUTSOURCING_PARTY.name());
            statement.setInt(4, party.version());
            statement.executeUpdate();
        }
    }

    private void updateParty(Connection connection, OutsourcingParty party) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                update biz_outsourcing_party
                   set party_name=?, counterpart=?, version_no=?
                 where party_id=?
                """)) {
            statement.setString(1, party.name());
            statement.setString(2, OutsourcingCounterpart.OUTSOURCING_PARTY.name());
            statement.setInt(3, party.version());
            statement.setString(4, party.partyId());
            statement.executeUpdate();
        }
    }

    private OutsourcingParty mapParty(ResultSet rows) throws SQLException {
        String counterpart = rows.getString("counterpart");
        if (!OutsourcingCounterpart.OUTSOURCING_PARTY.name().equals(counterpart)) {
            throw new BusinessException("外协方不能登记成客户或员工");
        }
        return new OutsourcingParty(rows.getString("party_id"), rows.getString("party_name"), rows.getInt("version_no"));
    }

    private OutsourcingTask mapTask(ResultSet rows) throws SQLException {
        return new OutsourcingTask(
                rows.getString("task_id"),
                rows.getString("request_id"),
                rows.getString("party_id"),
                rows.getString("party_name_snapshot"),
                BusinessKind.valueOf(rows.getString("business_kind")),
                rows.getString("target_code"),
                rows.getString("node_id"),
                rows.getString("bag_id"),
                OwnerType.valueOf(rows.getString("owner_type")),
                rows.getString("owner_ref"),
                rows.getInt("version_no")
        );
    }

    private static BusinessException conflict(int expectedVersion, int current) {
        if (expectedVersion == 0 && current > 0) {
            return new BusinessException("外协方已存在");
        }
        return new BusinessException("外协方已被其他人修改，请刷新后重试");
    }

    private static boolean duplicate(SQLException ex) {
        String state = ex.getSQLState();
        return ex.getErrorCode() == 1062 || ex.getErrorCode() == 23505
                || "23000".equals(state) || "23505".equals(state);
    }
}
