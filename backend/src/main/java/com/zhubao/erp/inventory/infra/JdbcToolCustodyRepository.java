package com.zhubao.erp.inventory.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.ToolCustodyRepository;
import com.zhubao.erp.inventory.domain.ToolDisposition;
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

    @Override public java.util.List<com.zhubao.erp.inventory.domain.ToolPiece> piecesOf(String itemId) {
        try (Connection connection=dataSource.getConnection(); PreparedStatement query=connection.prepareStatement("select * from inv_tool_piece where item_id=?")) {
            query.setString(1,itemId);
            try (ResultSet rows=query.executeQuery()) {
                java.util.List<com.zhubao.erp.inventory.domain.ToolPiece> result=new java.util.ArrayList<>();
                while (rows.next()) result.add(new com.zhubao.erp.inventory.domain.ToolPiece(rows.getString("piece_code"),rows.getString("item_id"),rows.getString("warehouse_code"),rows.getString("holder_id"),rows.getBoolean("scrapped")));
                return java.util.List.copyOf(result);
            }
        } catch (SQLException ex) { throw new IllegalStateException("无法读取本厂物品存量明细",ex); }
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
                        "select * from inv_tool_piece where piece_code=? for update")) {
                    query.setString(1, loan.pieceCode());
                    try (ResultSet rows = query.executeQuery()) {
                        if (!rows.next() || !rows.getString("item_id").equals(loan.itemId())
                                || !rows.getString("warehouse_code").equals(loan.warehouseCode())) {
                            throw new BusinessException("工具编号与物品或位置不一致");
                        }
                        if (!rows.getString("holder_id").isEmpty() || rows.getBoolean("scrapped")) {
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
    public ToolLoan settle(String loanId, BigDecimal quantity, int expectedVersion, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement query = connection.prepareStatement("select * from inv_tool_loan where loan_id=? for update")) {
            query.setString(1, loanId);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) {
                    throw new BusinessException("借出单不存在");
                }
                ToolLoan current = map(rows);
                if (current.version() != expectedVersion) {
                    throw new BusinessException("借出单已被其他人修改，请刷新后重试");
                }
                if (current.returnedQuantity().add(quantity).compareTo(current.quantity()) > 0) {
                    throw new BusinessException("归还数量不能超过未还");
                }
                try (PreparedStatement update = connection.prepareStatement("""
                        update inv_tool_loan
                           set returned_quantity=returned_quantity+?, version_no=version_no+1
                         where loan_id=? and version_no=?
                        """)) {
                    update.setBigDecimal(1, quantity);
                    update.setString(2, loanId);
                    update.setInt(3, expectedVersion);
                    if (update.executeUpdate() != 1) {
                        throw new BusinessException("借出单已被其他人修改，请刷新后重试");
                    }
                }
                return new ToolLoan(
                        current.loanId(), current.itemId(), current.holderId(), current.holderName(), current.quantity(),
                        current.returnedQuantity().add(quantity), current.unit(), current.pieceCode(), current.warehouseCode(),
                        current.version() + 1);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法结清工具借出", ex);
        }
    }

    @Override
    public void record(ToolDisposition disposition, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into inv_tool_disposition(
                    disposition_id, loan_id, holder_id, kind, quantity, reason)
                values (?,?,?,?,?,?)
                """)) {
            statement.setString(1, disposition.dispositionId());
            statement.setString(2, disposition.loanId());
            statement.setString(3, disposition.holderId());
            statement.setString(4, disposition.kind());
            statement.setBigDecimal(5, disposition.quantity());
            statement.setString(6, disposition.reason());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存工具处置", ex);
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

    @Override
    public BigDecimal lockStock(String itemId, String warehouseCode, WorkSession session) {
        try (PreparedStatement query = WorkSessionBindings.requiredJdbc(session).prepareStatement(
                "select quantity from inv_tool_stock where item_id=? and warehouse_code=? for update")) {
            query.setString(1, itemId);
            query.setString(2, warehouseCode);
            try (ResultSet rows = query.executeQuery()) {
                return rows.next() ? rows.getBigDecimal(1) : BigDecimal.ZERO;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法锁定盘点工具", ex);
        }
    }

    @Override
    public void settlePiece(ToolLoan loan, boolean scrap, WorkSession session) {
        try (PreparedStatement update = WorkSessionBindings.requiredJdbc(session).prepareStatement("""
                update inv_tool_piece set holder_id='', scrapped=?
                 where piece_code=? and item_id=? and warehouse_code=? and holder_id=? and scrapped=false
                """)) {
            update.setBoolean(1, scrap);
            update.setString(2, loan.pieceCode());
            update.setString(3, loan.itemId());
            update.setString(4, loan.warehouseCode());
            update.setString(5, loan.holderId());
            if (update.executeUpdate() != 1) {
                throw new BusinessException("工具编号的保管状态与借单不一致");
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法更新逐件工具状态", ex);
        }
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
