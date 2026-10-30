package com.zhubao.erp.finance.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.OutsourcingFeeBasis;
import com.zhubao.erp.finance.domain.OutsourcingFeeBasisRepository;
import com.zhubao.erp.work.app.WorkSessionBindings;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.outsourcing-fee-basis.repository", havingValue = "jdbc")
public class JdbcOutsourcingFeeBasisRepository implements OutsourcingFeeBasisRepository {

    private final DataSource dataSource;

    public JdbcOutsourcingFeeBasisRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(OutsourcingFeeBasis basis, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into fin_outsourcing_fee_basis(
                    basis_id, completion_id, task_id, party_id, target_code, node_id, quantity)
                values (?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, basis.basisId());
            statement.setString(2, basis.completionId());
            statement.setString(3, basis.taskId());
            statement.setString(4, basis.partyId());
            statement.setString(5, basis.targetCode());
            statement.setString(6, basis.nodeId());
            statement.setBigDecimal(7, basis.quantity());
            statement.executeUpdate();
        } catch (SQLException ex) {
            if (duplicate(ex)) {
                throw new BusinessException("外协费用依据已存在");
            }
            throw new IllegalStateException("无法保存外协费用依据", ex);
        }
    }

    @Override
    public Optional<OutsourcingFeeBasis> findByCompletion(String completionId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select * from fin_outsourcing_fee_basis where completion_id=?")) {
            statement.setString(1, completionId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(new OutsourcingFeeBasis(
                        rows.getString("basis_id"), rows.getString("completion_id"), rows.getString("task_id"),
                        rows.getString("party_id"), rows.getString("target_code"), rows.getString("node_id"),
                        rows.getBigDecimal("quantity")));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取外协费用依据", ex);
        }
    }

    private static boolean duplicate(SQLException ex) {
        String state = ex.getSQLState();
        return ex.getErrorCode() == 1062 || ex.getErrorCode() == 23505
                || "23000".equals(state) || "23505".equals(state);
    }
}
