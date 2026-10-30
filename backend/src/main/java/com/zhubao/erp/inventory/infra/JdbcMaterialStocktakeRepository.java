package com.zhubao.erp.inventory.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.MaterialStocktake;
import com.zhubao.erp.inventory.domain.MaterialStocktakeRepository;
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
@ConditionalOnProperty(name = "zhubao.material-stocktake.repository", havingValue = "jdbc")
public class JdbcMaterialStocktakeRepository implements MaterialStocktakeRepository {

    private final DataSource dataSource;

    public JdbcMaterialStocktakeRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void insert(MaterialStocktake stocktake, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into inv_material_stocktake(
                    stocktake_id, counted_at, item_id, batch_code, location_code,
                    book_quantity, counted_quantity, status, version_no)
                values (?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, stocktake.stocktakeId());
            statement.setString(2, stocktake.countedAt());
            statement.setString(3, stocktake.itemId());
            statement.setString(4, stocktake.batchCode());
            statement.setString(5, stocktake.locationCode());
            statement.setBigDecimal(6, stocktake.bookQuantity());
            if (stocktake.countedQuantity() == null) {
                statement.setBigDecimal(7, null);
            } else {
                statement.setBigDecimal(7, stocktake.countedQuantity());
            }
            statement.setString(8, stocktake.status());
            statement.setInt(9, stocktake.version());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存盘点单", ex);
        }
    }

    @Override
    public Optional<MaterialStocktake> find(String stocktakeId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select * from inv_material_stocktake where stocktake_id=?")) {
            statement.setString(1, stocktakeId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取盘点单", ex);
        }
    }

    @Override
    public void count(String stocktakeId, BigDecimal countedQuantity, int expectedVersion, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                update inv_material_stocktake
                   set counted_quantity=?, version_no=version_no+1
                 where stocktake_id=? and version_no=? and status=?
                """)) {
            statement.setBigDecimal(1, countedQuantity);
            statement.setString(2, stocktakeId);
            statement.setInt(3, expectedVersion);
            statement.setString(4, MaterialStocktake.OPEN);
            if (statement.executeUpdate() != 1) {
                throw new BusinessException("盘点单已被其他人修改，请刷新后重试");
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法填写实盘", ex);
        }
    }

    @Override
    public void confirm(String stocktakeId, int expectedVersion, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                update inv_material_stocktake
                   set status=?, version_no=version_no+1
                 where stocktake_id=? and version_no=? and status=? and counted_quantity is not null
                """)) {
            statement.setString(1, MaterialStocktake.CONFIRMED);
            statement.setString(2, stocktakeId);
            statement.setInt(3, expectedVersion);
            statement.setString(4, MaterialStocktake.OPEN);
            if (statement.executeUpdate() != 1) {
                throw new BusinessException("盘点已经确认");
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法确认盘点", ex);
        }
    }

    private static MaterialStocktake map(ResultSet rows) throws SQLException {
        return new MaterialStocktake(
                rows.getString("stocktake_id"),
                rows.getString("counted_at"),
                rows.getString("item_id"),
                rows.getString("batch_code"),
                rows.getString("location_code"),
                rows.getBigDecimal("book_quantity"),
                rows.getBigDecimal("counted_quantity"),
                rows.getString("status"),
                rows.getInt("version_no")
        );
    }
}
