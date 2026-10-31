package com.zhubao.erp.finance.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.FixedAssetDisposition;
import com.zhubao.erp.finance.domain.FixedAssetDispositionRepository;
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
import java.util.ArrayList;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "zhubao.fixed-asset-disposition.repository", havingValue = "jdbc")
public class JdbcFixedAssetDispositionRepository implements FixedAssetDispositionRepository {

    private final DataSource dataSource;

    public JdbcFixedAssetDispositionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void insert(FixedAssetDisposition disposition, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into fin_fixed_asset_disposition(
                    disposition_id, asset_code, disposition_kind, occurred_on, basis_text, receipt_code)
                values (?,?,?,?,?,?)
                """)) {
            statement.setString(1, disposition.dispositionId());
            statement.setString(2, disposition.assetCode());
            statement.setString(3, disposition.kind());
            statement.setString(4, disposition.occurredOn());
            statement.setString(5, disposition.basis());
            if (disposition.receiptCode() == null || disposition.receiptCode().isEmpty()) {
                statement.setNull(6, Types.VARCHAR);
            } else {
                statement.setString(6, disposition.receiptCode());
            }
            statement.executeUpdate();
        } catch (SQLException ex) {
            if (duplicate(ex)) {
                throw new BusinessException("这笔回款已经对应过处置");
            }
            throw new IllegalStateException("无法保存资产处置", ex);
        }
    }

    @Override
    public boolean coversReceipt(String receiptCode, WorkSession session) {
        if (receiptCode == null || receiptCode.isBlank()) {
            return false;
        }
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                select disposition_id from fin_fixed_asset_disposition where receipt_code=? for update
                """)) {
            statement.setString(1, receiptCode);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法核对处置回款", ex);
        }
    }

    @Override
    public List<FixedAssetDisposition> list(String assetCode) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select disposition_id, asset_code, disposition_kind, occurred_on, basis_text, receipt_code
                       from fin_fixed_asset_disposition
                      where asset_code=?
                      order by disposition_id
                     """)) {
            statement.setString(1, assetCode);
            try (ResultSet rows = statement.executeQuery()) {
                List<FixedAssetDisposition> found = new ArrayList<>();
                while (rows.next()) {
                    String receipt = rows.getString("receipt_code");
                    found.add(new FixedAssetDisposition(
                            rows.getString("disposition_id"),
                            rows.getString("asset_code"),
                            rows.getString("disposition_kind"),
                            rows.getString("occurred_on") == null ? "" : rows.getString("occurred_on").trim(),
                            rows.getString("basis_text"),
                            receipt == null ? "" : receipt
                    ));
                }
                return found;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取资产处置", ex);
        }
    }

    private static boolean duplicate(SQLException ex) {
        String state = ex.getSQLState();
        return ex.getErrorCode() == 1062 || ex.getErrorCode() == 23505
                || "23000".equals(state) || "23505".equals(state)
                || (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("unique"));
    }
}
