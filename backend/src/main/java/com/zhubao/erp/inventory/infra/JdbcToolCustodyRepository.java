package com.zhubao.erp.inventory.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.ToolCustodyRepository;
import com.zhubao.erp.inventory.domain.ToolLoan;
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
@ConditionalOnProperty(name = "zhubao.tool-custody.repository", havingValue = "jdbc")
public class JdbcToolCustodyRepository implements ToolCustodyRepository {

    private final DataSource dataSource;

    public JdbcToolCustodyRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void addStock(String itemId, String warehouseCode, BigDecimal quantity, String unit, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement query = connection.prepareStatement(
                "select quantity from inv_tool_stock where item_id=? and warehouse_code=? for update")) {
            query.setString(1, itemId);
            query.setString(2, warehouseCode);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) {
                    if (quantity.signum() < 0) {
                        throw new BusinessException("可借数量不足");
                    }
                    try (PreparedStatement insert = connection.prepareStatement(
                            "insert into inv_tool_stock(item_id, warehouse_code, quantity, quantity_unit) values (?,?,?,?)")) {
                        insert.setString(1, itemId);
                        insert.setString(2, warehouseCode);
                        insert.setBigDecimal(3, quantity);
                        insert.setString(4, unit);
                        insert.executeUpdate();
                    }
                    return;
                }
                BigDecimal next = rows.getBigDecimal(1).add(quantity);
                if (next.signum() < 0) {
                    throw new BusinessException("可借数量不足");
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "update inv_tool_stock set quantity=? where item_id=? and warehouse_code=?")) {
                    update.setBigDecimal(1, next);
                    update.setString(2, itemId);
                    update.setString(3, warehouseCode);
                    update.executeUpdate();
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法更新工具库存", ex);
        }
    }

    @Override
    public BigDecimal stock(String itemId, String warehouseCode) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select quantity from inv_tool_stock where item_id=? and warehouse_code=?")) {
            statement.setString(1, itemId);
            statement.setString(2, warehouseCode);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getBigDecimal(1) : BigDecimal.ZERO;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取工具库存", ex);
        }
    }

    @Override
    public void placePiece(String pieceCode, String itemId, String warehouseCode, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into inv_tool_piece(piece_code, item_id, warehouse_code, holder_id) values (?,?,?,?)")) {
            statement.setString(1, pieceCode);
            statement.setString(2, itemId);
            statement.setString(3, warehouseCode);
            statement.setString(4, "");
            statement.executeUpdate();
        } catch (SQLException ex) {
            if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("unique")) {
                throw new BusinessException("工具编号已存在");
            }
            throw new IllegalStateException("无法登记工具编号", ex);
        }
    }

    @Override
    public void loan(ToolLoan loan, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try {
            if (!loan.pieceCode().isEmpty()) {
                try (PreparedStatement query = connection.prepareStatement(
                        "select holder_id from inv_tool_piece where piece_code=? for update")) {
                    query.setString(1, loan.pieceCode());
                    try (ResultSet rows = query.executeQuery()) {
                        if (!rows.next() || !rows.getString(1).isEmpty()) {
                            throw new BusinessException("该工具已借出");
                        }
                    }
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "update inv_tool_piece set holder_id=? where piece_code=?")) {
                    update.setString(1, loan.holderId());
                    update.setString(2, loan.pieceCode());
                    update.executeUpdate();
                }
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    insert into inv_tool_loan(
                        loan_id, item_id, holder_id, holder_name, quantity, returned_quantity,
                        quantity_unit, piece_code, warehouse_code, version_no)
                    values (?,?,?,?,?,?,?,?,?,?)
                    """)) {
                insert.setString(1, loan.loanId());
                insert.setString(2, loan.itemId());
                insert.setString(3, loan.holderId());
                insert.setString(4, loan.holderName());
                insert.setBigDecimal(5, loan.quantity());
                insert.setBigDecimal(6, loan.returnedQuantity());
                insert.setString(7, loan.unit());
                insert.setString(8, loan.pieceCode());
                insert.setString(9, loan.warehouseCode());
                insert.setInt(10, loan.version());
                insert.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存工具借出", ex);
        }
    }

    @Override
    public Optional<ToolLoan> find(String loanId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("select * from inv_tool_loan where loan_id=?")) {
            statement.setString(1, loanId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取工具借出", ex);
        }
    }

    @Override
    public List<ToolLoan> loansOf(String itemId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("select * from inv_tool_loan where item_id=?")) {
            statement.setString(1, itemId);
            try (ResultSet rows = statement.executeQuery()) {
                List<ToolLoan> found = new ArrayList<>();
                while (rows.next()) {
                    found.add(map(rows));
                }
                return List.copyOf(found);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法列出工具借出", ex);
        }
    }

    @Override
    public BigDecimal outstanding(String itemId, String holderId) {
        return loansOf(itemId).stream()
                .filter(loan -> loan.holderId().equals(holderId))
                .map(ToolLoan::outstanding)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static ToolLoan map(ResultSet rows) throws SQLException {
        return new ToolLoan(
                rows.getString("loan_id"),
                rows.getString("item_id"),
                rows.getString("holder_id"),
                rows.getString("holder_name"),
                rows.getBigDecimal("quantity"),
                rows.getBigDecimal("returned_quantity"),
                rows.getString("quantity_unit"),
                rows.getString("piece_code"),
                rows.getString("warehouse_code"),
                rows.getInt("version_no")
        );
    }
}
