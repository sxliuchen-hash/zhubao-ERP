package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetRole;
import com.zhubao.erp.business.domain.DeliveryDraft;
import com.zhubao.erp.business.domain.DeliveryDraftLine;
import com.zhubao.erp.business.domain.DeliveryDraftRepository;
import com.zhubao.erp.business.domain.DeliveryDraftStatus;
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
@ConditionalOnProperty(name = "zhubao.delivery-draft.repository", havingValue = "jdbc")
public class JdbcDeliveryDraftRepository implements DeliveryDraftRepository {

    private final DataSource dataSource;

    public JdbcDeliveryDraftRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(DeliveryDraft draft, List<DeliveryDraftLine> lines, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into biz_delivery_draft(
                    delivery_id, request_id, customer_id, business_kind, logistics_note, remark,
                    status_code, version_no)
                values (?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, draft.deliveryId());
            statement.setString(2, draft.requestId());
            statement.setLong(3, draft.customerId());
            statement.setString(4, draft.businessKind().name());
            statement.setString(5, draft.logisticsNote());
            statement.setString(6, draft.remark());
            statement.setString(7, draft.status().name());
            statement.setInt(8, draft.version());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw writeFailure(ex);
        }
        for (DeliveryDraftLine line : lines) {
            insertLine(connection, line);
        }
    }

    @Override
    public Optional<DeliveryDraft> find(String deliveryId) {
        return listHeaders("delivery_id=?", deliveryId).stream().findFirst();
    }

    @Override
    public Optional<DeliveryDraft> findByRequest(String requestId) {
        return listHeaders("request_id=?", requestId).stream().findFirst();
    }

    @Override
    public List<DeliveryDraftLine> lines(String deliveryId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select * from biz_delivery_draft_line
                      where delivery_id=?
                      order by line_no
                     """)) {
            statement.setString(1, deliveryId);
            try (ResultSet rows = statement.executeQuery()) {
                List<DeliveryDraftLine> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(mapLine(rows));
                }
                return result;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取交付草稿明细", ex);
        }
    }

    @Override
    public List<String> listIds(WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement(
                "select delivery_id from biz_delivery_draft order by delivery_id")) {
            try (ResultSet rows = statement.executeQuery()) {
                List<String> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(rows.getString(1));
                }
                return result;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取交付单号", ex);
        }
    }

    private void insertLine(Connection connection, DeliveryDraftLine line) {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into biz_delivery_draft_line(
                    delivery_id, line_no, bag_id, bag_version, business_kind, target_role, target_code,
                    quantity, quantity_unit, weight_state, weight_value, material_code, source_key,
                    requirement_version)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, line.deliveryId());
            statement.setInt(2, line.lineNo());
            statement.setString(3, line.bagId());
            statement.setInt(4, line.bagVersion());
            statement.setString(5, line.businessKind().name());
            statement.setString(6, line.targetRole().name());
            statement.setString(7, line.targetCode());
            statement.setBigDecimal(8, line.quantity());
            statement.setString(9, line.quantityUnit());
            statement.setString(10, line.weight().state().name());
            if (line.weight().state() == StockMeasurement.WeightState.KNOWN) {
                statement.setBigDecimal(11, line.weight().value());
            } else {
                statement.setBigDecimal(11, null);
            }
            statement.setString(12, line.materialCode());
            statement.setString(13, line.sourceKey());
            statement.setString(14, line.requirementVersion());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw writeFailure(ex);
        }
    }

    private List<DeliveryDraft> listHeaders(String where, String value) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select * from biz_delivery_draft where " + where)) {
            statement.setString(1, value);
            try (ResultSet rows = statement.executeQuery()) {
                List<DeliveryDraft> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(mapHeader(rows));
                }
                return result;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取交付草稿", ex);
        }
    }

    private DeliveryDraft mapHeader(ResultSet rows) throws SQLException {
        return new DeliveryDraft(
                rows.getString("delivery_id"),
                rows.getString("request_id"),
                rows.getLong("customer_id"),
                BusinessKind.valueOf(rows.getString("business_kind")),
                rows.getString("logistics_note"),
                rows.getString("remark"),
                DeliveryDraftStatus.valueOf(rows.getString("status_code")),
                rows.getInt("version_no")
        );
    }

    private DeliveryDraftLine mapLine(ResultSet rows) throws SQLException {
        String weightState = rows.getString("weight_state");
        BigDecimal weightValue = rows.getBigDecimal("weight_value");
        if (rows.wasNull()) {
            weightValue = null;
        }
        StockMeasurement.Weight weight = "KNOWN".equals(weightState) && weightValue != null
                ? StockMeasurement.known(weightValue)
                : StockMeasurement.unknown();
        return new DeliveryDraftLine(
                rows.getString("delivery_id"),
                rows.getInt("line_no"),
                rows.getString("bag_id"),
                rows.getInt("bag_version"),
                BusinessKind.valueOf(rows.getString("business_kind")),
                BusinessTargetRole.valueOf(rows.getString("target_role")),
                rows.getString("target_code"),
                rows.getBigDecimal("quantity"),
                rows.getString("quantity_unit"),
                weight,
                rows.getString("material_code"),
                rows.getString("source_key"),
                rows.getString("requirement_version")
        );
    }

    private RuntimeException writeFailure(SQLException ex) {
        if (duplicate(ex)) {
            throw new BusinessException("交付草稿未能保存，请重试");
        }
        throw new IllegalStateException("无法保存交付草稿", ex);
    }

    private static boolean duplicate(SQLException ex) {
        String state = ex.getSQLState();
        return ex.getErrorCode() == 1062 || ex.getErrorCode() == 23505
                || "23000".equals(state) || "23505".equals(state);
    }
}
