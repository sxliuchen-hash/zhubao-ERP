package com.zhubao.erp.inventory.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.CompanyMaterialIssue;
import com.zhubao.erp.inventory.domain.CompanyMaterialIssueRepository;
import com.zhubao.erp.inventory.domain.CompanyMaterialReturn;
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
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.company-material-issue.repository", havingValue = "jdbc")
public class JdbcCompanyMaterialIssueRepository implements CompanyMaterialIssueRepository {

    private final DataSource dataSource;

    public JdbcCompanyMaterialIssueRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void insert(CompanyMaterialIssue issue, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into inv_company_material_issue(
                    issue_id, item_id, batch_code, from_warehouse, workshop_code, quantity, returned_quantity,
                    quantity_unit, weight_state, weight_value, target_code, version_no)
                values (?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, issue.issueId());
            statement.setString(2, issue.itemId());
            statement.setString(3, issue.batchCode());
            statement.setString(4, issue.fromWarehouse());
            statement.setString(5, issue.workshopCode());
            statement.setBigDecimal(6, issue.quantity());
            statement.setBigDecimal(7, issue.returnedQuantity());
            statement.setString(8, issue.unit());
            statement.setString(9, issue.weight().state().name());
            setDecimal(statement, 10, issue.weight().value());
            statement.setString(11, issue.targetCode());
            statement.setInt(12, issue.version());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存公司料领用", ex);
        }
    }

    @Override
    public Optional<CompanyMaterialIssue> find(String issueId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select * from inv_company_material_issue where issue_id=?")) {
            statement.setString(1, issueId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(mapIssue(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取公司料领用", ex);
        }
    }

    @Override
    public CompanyMaterialIssue addReturned(String issueId, BigDecimal quantity, int expectedVersion, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                select * from inv_company_material_issue where issue_id=? for update
                """)) {
            statement.setString(1, issueId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new BusinessException("领用单不存在");
                }
                CompanyMaterialIssue current = mapIssue(rows);
                if (current.version() != expectedVersion) {
                    throw new BusinessException("领用单已被其他人修改，请刷新后重试");
                }
                if (current.returnedQuantity().add(quantity).compareTo(current.quantity()) > 0) {
                    throw new BusinessException("退回数量不能超过可退");
                }
                try (PreparedStatement update = connection.prepareStatement("""
                        update inv_company_material_issue
                           set returned_quantity=returned_quantity+?, version_no=version_no+1
                         where issue_id=? and version_no=?
                        """)) {
                    update.setBigDecimal(1, quantity);
                    update.setString(2, issueId);
                    update.setInt(3, expectedVersion);
                    if (update.executeUpdate() != 1) {
                        throw new BusinessException("领用单已被其他人修改，请刷新后重试");
                    }
                }
                return new CompanyMaterialIssue(
                        current.issueId(), current.itemId(), current.batchCode(), current.fromWarehouse(), current.workshopCode(),
                        current.quantity(), current.returnedQuantity().add(quantity), current.unit(), current.weight(),
                        current.targetCode(), current.version() + 1);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法退回公司料领用", ex);
        }
    }

    @Override
    public void insert(CompanyMaterialReturn returned, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into inv_company_material_return(
                    return_id, issue_id, quantity, quantity_unit, weight_state, weight_value)
                values (?,?,?,?,?,?)
                """)) {
            statement.setString(1, returned.returnId());
            statement.setString(2, returned.issueId());
            statement.setBigDecimal(3, returned.quantity());
            statement.setString(4, returned.unit());
            statement.setString(5, returned.weight().state().name());
            setDecimal(statement, 6, returned.weight().value());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存公司料退回", ex);
        }
    }

    @Override
    public Optional<CompanyMaterialReturn> findReturn(String returnId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select * from inv_company_material_return where return_id=?")) {
            statement.setString(1, returnId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(new CompanyMaterialReturn(
                        rows.getString("return_id"),
                        rows.getString("issue_id"),
                        rows.getBigDecimal("quantity"),
                        rows.getString("quantity_unit"),
                        "KNOWN".equals(rows.getString("weight_state"))
                                ? StockMeasurement.known(rows.getBigDecimal("weight_value"))
                                : StockMeasurement.unknown()));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取公司料退回", ex);
        }
    }

    private static CompanyMaterialIssue mapIssue(ResultSet rows) throws SQLException {
        return new CompanyMaterialIssue(
                rows.getString("issue_id"),
                rows.getString("item_id"),
                rows.getString("batch_code"),
                rows.getString("from_warehouse"),
                rows.getString("workshop_code"),
                rows.getBigDecimal("quantity"),
                rows.getBigDecimal("returned_quantity"),
                rows.getString("quantity_unit"),
                weight(rows.getString("weight_state"), rows.getBigDecimal("weight_value")),
                rows.getString("target_code"),
                rows.getInt("version_no")
        );
    }

    private static StockMeasurement.Weight weight(String state, BigDecimal value) {
        return "KNOWN".equals(state) ? StockMeasurement.known(value) : StockMeasurement.unknown();
    }

    private static void setDecimal(PreparedStatement statement, int index, BigDecimal value) throws SQLException {
        statement.setBigDecimal(index, value);
    }
}
