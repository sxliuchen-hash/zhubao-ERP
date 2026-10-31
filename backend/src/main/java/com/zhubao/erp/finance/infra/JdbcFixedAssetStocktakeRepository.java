package com.zhubao.erp.finance.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.FixedAssetStocktake;
import com.zhubao.erp.finance.domain.FixedAssetStocktakeRepository;
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
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.fixed-asset-stocktake.repository", havingValue = "jdbc")
public class JdbcFixedAssetStocktakeRepository implements FixedAssetStocktakeRepository {

    private final DataSource dataSource;

    public JdbcFixedAssetStocktakeRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void insert(FixedAssetStocktake stocktake, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into fin_fixed_asset_stocktake(
                    stocktake_id, asset_code, counted_at, book_location, book_custodian, book_status, book_version,
                    seen_state, count_location, count_custodian, exception_text, status, version_no)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, stocktake.stocktakeId());
            statement.setString(2, stocktake.assetCode());
            statement.setString(3, stocktake.countedAt());
            setText(statement, 4, stocktake.bookLocation());
            setText(statement, 5, stocktake.bookCustodian());
            setText(statement, 6, stocktake.bookStatus());
            statement.setLong(7, stocktake.bookVersion());
            setText(statement, 8, stocktake.seen());
            setText(statement, 9, stocktake.countLocation());
            setText(statement, 10, stocktake.countCustodian());
            statement.setString(11, stocktake.exceptionText());
            statement.setString(12, stocktake.status());
            statement.setLong(13, stocktake.version());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存资产盘点", ex);
        }
    }

    @Override
    public Optional<FixedAssetStocktake> find(String stocktakeId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectSql() + " where stocktake_id=?")) {
            statement.setString(1, stocktakeId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取资产盘点", ex);
        }
    }

    @Override
    public void count(String stocktakeId, String seen, String location, String custodian, String exceptionText,
                      long expectedVersion, WorkSession session) {
        update("""
                update fin_fixed_asset_stocktake
                   set seen_state=?, count_location=?, count_custodian=?, exception_text=?, version_no=version_no+1
                 where stocktake_id=? and version_no=? and status=?
                """, session, statement -> {
            setText(statement, 1, seen);
            setText(statement, 2, location);
            setText(statement, 3, custodian);
            statement.setString(4, exceptionText);
            statement.setString(5, stocktakeId);
            statement.setLong(6, expectedVersion);
            statement.setString(7, FixedAssetStocktake.OPEN);
        }, "请先登记实盘或刷新盘点单");
    }

    @Override
    public void confirm(String stocktakeId, long expectedVersion, WorkSession session) {
        update("""
                update fin_fixed_asset_stocktake
                   set status=?, version_no=version_no+1
                 where stocktake_id=? and version_no=? and status=? and seen_state is not null
                """, session, statement -> {
            statement.setString(1, FixedAssetStocktake.CONFIRMED);
            statement.setString(2, stocktakeId);
            statement.setLong(3, expectedVersion);
            statement.setString(4, FixedAssetStocktake.OPEN);
        }, "盘点已经确认");
    }

    private void update(String sql, WorkSession session, Binder binder, String failure) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            if (statement.executeUpdate() != 1) {
                throw new BusinessException(failure);
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (SQLException ex) {
            throw new IllegalStateException("无法更新资产盘点", ex);
        }
    }

    private static FixedAssetStocktake map(ResultSet rows) throws SQLException {
        return new FixedAssetStocktake(
                rows.getString("stocktake_id"),
                rows.getString("asset_code"),
                rows.getString("counted_at"),
                rows.getString("book_location"),
                rows.getString("book_custodian"),
                rows.getString("book_status"),
                rows.getLong("book_version"),
                rows.getString("seen_state"),
                rows.getString("count_location"),
                rows.getString("count_custodian"),
                rows.getString("exception_text") == null ? "" : rows.getString("exception_text"),
                rows.getString("status"),
                rows.getLong("version_no")
        );
    }

    private static String selectSql() {
        return """
                select stocktake_id, asset_code, counted_at, book_location, book_custodian, book_status, book_version,
                       seen_state, count_location, count_custodian, exception_text, status, version_no
                  from fin_fixed_asset_stocktake
                """;
    }

    private static void setText(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null || value.isEmpty()) {
            statement.setNull(index, Types.VARCHAR);
            return;
        }
        statement.setString(index, value);
    }

    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
