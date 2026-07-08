package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.OutboundOrderRecord;
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
@ConditionalOnProperty(name = "zhubao.business-document.repository", havingValue = "jdbc")
public class JdbcBusinessDocumentRepository implements BusinessDocumentRepository {

    private final DataSource dataSource;

    public JdbcBusinessDocumentRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean motherOrderExists(String code) {
        return exists("select count(*) from biz_mother_order where code = ?", code);
    }

    @Override
    public MotherOrderRecord saveMotherOrder(MotherOrderRecord record) {
        String sql = """
                insert into biz_mother_order (
                    code, customer_id, owner_customer_id, material_code, inbound_date,
                    inbound_express_company, inbound_express_no, sender_name_snapshot,
                    receiver_name_snapshot, unpack_signer_name, goods_name_snapshot,
                    owner_type, is_customer_owned, warehouse_code, quantity, quantity_unit,
                    weight, stock_status, ledger_code, source_register_type,
                    source_line_no, source_sequence_no, operator, evidence_code,
                    inbound_remark, order_status, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, record.code());
                setLong(statement, 2, record.customerId());
                setLong(statement, 3, record.ownerCustomerId());
                statement.setString(4, record.materialCode());
                statement.setString(5, record.inboundDate());
                statement.setString(6, record.expressCompany());
                statement.setString(7, record.courierNo());
                statement.setString(8, record.senderName());
                statement.setString(9, record.receiverName());
                statement.setString(10, record.unpackSigner());
                statement.setString(11, record.goodsName());
                statement.setString(12, record.ownerType());
                statement.setBoolean(13, record.customerOwned());
                statement.setString(14, record.warehouseCode());
                statement.setBigDecimal(15, record.quantity());
                statement.setString(16, record.quantityUnit());
                statement.setBigDecimal(17, record.weight());
                statement.setString(18, record.stockStatus());
                statement.setString(19, record.ledgerCode());
                statement.setString(20, record.sourceRegisterType());
                setInteger(statement, 21, record.sourceRegisterLineNo());
                statement.setString(22, record.sourceRegisterSequence());
                statement.setString(23, record.operator());
                statement.setString(24, record.evidenceCode());
                statement.setString(25, record.remark());
                statement.setString(26, record.status());
                statement.setString(27, record.createdAt());
                statement.executeUpdate();
                insertMotherOrderItem(connection, record);
                connection.commit();
                return record;
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save mother order: " + record.code(), ex);
        }
    }

    @Override
    public Optional<MotherOrderRecord> findMotherOrderByCode(String code) {
        String sql = """
                select code, customer_id, owner_customer_id, material_code, inbound_date,
                       inbound_express_company, inbound_express_no, sender_name_snapshot,
                       receiver_name_snapshot, unpack_signer_name, goods_name_snapshot,
                       owner_type, is_customer_owned, warehouse_code, quantity, quantity_unit,
                       weight, stock_status, ledger_code, source_register_type,
                       source_line_no, source_sequence_no, operator, evidence_code,
                       inbound_remark, order_status, created_at
                  from biz_mother_order
                 where code = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapMotherOrder(rs));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load mother order: " + code, ex);
        }
    }

    @Override
    public boolean outboundOrderExists(String code) {
        return exists("select count(*) from biz_outbound_order where code = ?", code);
    }

    @Override
    public OutboundOrderRecord saveOutboundOrder(OutboundOrderRecord record) {
        String sql = """
                insert into biz_outbound_order (
                    code, mother_order_code, work_order_code, customer_id, owner_customer_id,
                    material_code, from_warehouse_code, to_warehouse_code, delivery_date,
                    delivery_category, delivery_quantity, delivery_quantity_unit, delivery_weight,
                    express_company, express_no, logistics_raw_text, handler_name_snapshot,
                    source_register_type, source_line_no, source_sequence_no, operator,
                    evidence_code, outbound_remark, order_status, ledger_code, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, record.code());
                statement.setString(2, record.motherOrderCode());
                statement.setString(3, record.workOrderCode());
                setLong(statement, 4, record.customerId());
                setLong(statement, 5, record.ownerCustomerId());
                statement.setString(6, record.materialCode());
                statement.setString(7, record.fromWarehouseCode());
                statement.setString(8, record.toWarehouseCode());
                statement.setString(9, record.deliveryDate());
                statement.setString(10, record.deliveryCategory());
                statement.setBigDecimal(11, record.quantity());
                statement.setString(12, record.quantityUnit());
                statement.setBigDecimal(13, record.weight());
                statement.setString(14, record.expressCompany());
                statement.setString(15, record.logisticsNo());
                statement.setString(16, record.paperExpressText());
                statement.setString(17, record.handler());
                statement.setString(18, record.sourceRegisterType());
                setInteger(statement, 19, record.sourceRegisterLineNo());
                statement.setString(20, record.sourceRegisterSequence());
                statement.setString(21, record.operator());
                statement.setString(22, record.evidenceCode());
                statement.setString(23, record.remark());
                statement.setString(24, record.status());
                statement.setString(25, record.ledgerCode());
                statement.setString(26, record.createdAt());
                statement.executeUpdate();
                insertOutboundOrderItem(connection, record);
                connection.commit();
                return record;
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save outbound order: " + record.code(), ex);
        }
    }

    @Override
    public Optional<OutboundOrderRecord> findOutboundOrderByCode(String code) {
        String sql = """
                select code, mother_order_code, work_order_code, customer_id, owner_customer_id,
                       material_code, from_warehouse_code, to_warehouse_code, delivery_date,
                       delivery_category, delivery_quantity, delivery_quantity_unit, delivery_weight,
                       express_company, express_no, logistics_raw_text, handler_name_snapshot,
                       source_register_type, source_line_no, source_sequence_no, operator,
                       evidence_code, outbound_remark, order_status, ledger_code, created_at
                  from biz_outbound_order
                 where code = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapOutboundOrder(rs));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load outbound order: " + code, ex);
        }
    }

    private boolean exists(String sql, String code) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to check business document code: " + code, ex);
        }
    }

    private void insertMotherOrderItem(Connection connection, MotherOrderRecord record) throws SQLException {
        String sql = """
                insert into biz_mother_order_item (
                    mother_order_code, material_code, quantity, quantity_unit,
                    weight, weight_unit, ledger_code
                ) values (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.code());
            statement.setString(2, record.materialCode());
            statement.setBigDecimal(3, record.quantity());
            statement.setString(4, record.quantityUnit());
            statement.setBigDecimal(5, record.weight());
            statement.setString(6, "g");
            statement.setString(7, record.ledgerCode());
            statement.executeUpdate();
        }
    }

    private void insertOutboundOrderItem(Connection connection, OutboundOrderRecord record) throws SQLException {
        String sql = """
                insert into biz_outbound_order_item (
                    outbound_order_code, mother_order_code, work_order_code, material_code,
                    quantity, quantity_unit, weight, weight_unit, ledger_code
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.code());
            statement.setString(2, record.motherOrderCode());
            statement.setString(3, record.workOrderCode());
            statement.setString(4, record.materialCode());
            statement.setBigDecimal(5, record.quantity());
            statement.setString(6, record.quantityUnit());
            statement.setBigDecimal(7, record.weight());
            statement.setString(8, "g");
            statement.setString(9, record.ledgerCode());
            statement.executeUpdate();
        }
    }

    private MotherOrderRecord mapMotherOrder(ResultSet rs) throws SQLException {
        return new MotherOrderRecord(
                rs.getString("code"),
                nullableLong(rs, "customer_id"),
                nullableLong(rs, "owner_customer_id"),
                rs.getString("material_code"),
                rs.getString("inbound_date"),
                rs.getString("inbound_express_company"),
                rs.getString("inbound_express_no"),
                rs.getString("sender_name_snapshot"),
                rs.getString("receiver_name_snapshot"),
                rs.getString("unpack_signer_name"),
                rs.getString("goods_name_snapshot"),
                rs.getString("owner_type"),
                rs.getBoolean("is_customer_owned"),
                rs.getString("warehouse_code"),
                rs.getBigDecimal("quantity"),
                rs.getString("quantity_unit"),
                rs.getBigDecimal("weight"),
                rs.getString("stock_status"),
                rs.getString("ledger_code"),
                rs.getString("source_register_type"),
                nullableInteger(rs, "source_line_no"),
                rs.getString("source_sequence_no"),
                rs.getString("operator"),
                rs.getString("evidence_code"),
                rs.getString("inbound_remark"),
                rs.getString("order_status"),
                rs.getString("created_at")
        );
    }

    private OutboundOrderRecord mapOutboundOrder(ResultSet rs) throws SQLException {
        return new OutboundOrderRecord(
                rs.getString("code"),
                rs.getString("mother_order_code"),
                rs.getString("work_order_code"),
                nullableLong(rs, "customer_id"),
                nullableLong(rs, "owner_customer_id"),
                rs.getString("material_code"),
                rs.getString("from_warehouse_code"),
                rs.getString("to_warehouse_code"),
                rs.getString("delivery_date"),
                rs.getString("delivery_category"),
                rs.getBigDecimal("delivery_quantity"),
                rs.getString("delivery_quantity_unit"),
                rs.getBigDecimal("delivery_weight"),
                rs.getString("express_company"),
                rs.getString("express_no"),
                rs.getString("logistics_raw_text"),
                rs.getString("handler_name_snapshot"),
                rs.getString("source_register_type"),
                nullableInteger(rs, "source_line_no"),
                rs.getString("source_sequence_no"),
                rs.getString("operator"),
                rs.getString("evidence_code"),
                rs.getString("outbound_remark"),
                rs.getString("order_status"),
                rs.getString("ledger_code"),
                rs.getString("created_at")
        );
    }

    private void setLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private void setInteger(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
