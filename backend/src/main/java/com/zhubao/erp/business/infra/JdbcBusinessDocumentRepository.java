package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.ChildLineAllocation;
import com.zhubao.erp.business.domain.DuplicateCourierException;
import com.zhubao.erp.business.domain.LabelReprintLog;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.OrderAdjustment;
import com.zhubao.erp.business.domain.OrderRecycleMark;
import com.zhubao.erp.business.domain.MotherSplitState;
import com.zhubao.erp.business.domain.OutboundOrderRecord;
import com.zhubao.erp.business.domain.ProcessStationHandoff;
import com.zhubao.erp.business.domain.QualityCheckLine;
import com.zhubao.erp.business.domain.QualityRequirementCheck;
import com.zhubao.erp.business.domain.QualityReworkReport;
import com.zhubao.erp.business.domain.QualityRound;
import com.zhubao.erp.business.domain.ReceiptLine;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
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
    public boolean motherOrderExists(String code, WorkSession session) {
        return exists(requireConnection(session), "select count(*) from biz_mother_order where code = ?", code);
    }

    @Override
    public MotherOrderRecord saveMotherOrder(MotherOrderRecord record) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                writeMotherOrder(connection, record);
                connection.commit();
                return record;
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (DuplicateCourierException ex) {
            throw ex;
        } catch (SQLException ex) {
            throw motherWriteFailure(record.code(), ex);
        }
    }

    @Override
    public MotherOrderRecord saveMotherOrder(MotherOrderRecord record, WorkSession session) {
        try {
            writeMotherOrder(requireConnection(session), record);
            return record;
        } catch (SQLException ex) {
            throw motherWriteFailure(record.code(), ex);
        }
    }

    private void writeMotherOrder(Connection connection, MotherOrderRecord record) throws SQLException {
        String sql = """
                insert into biz_mother_order (
                    code, customer_id, owner_customer_id, material_code, inbound_date,
                    inbound_express_company, inbound_express_no, sender_name_snapshot,
                    receiver_name_snapshot, unpack_signer_name, goods_name_snapshot,
                    owner_type, is_customer_owned, warehouse_code, quantity, quantity_unit,
                    weight, stock_status, ledger_code, source_register_type,
                    source_line_no, source_sequence_no, operator, evidence_code,
                    inbound_remark, order_status, created_at, processing_requirement, due_date,
                    customer_name, courier_identity
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
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
            statement.setString(28, record.processingRequirement());
            statement.setString(29, record.dueDate());
            statement.setString(30, record.customerName());
            setCourierIdentity(statement, 31, record.courierNo());
            statement.executeUpdate();
            insertMotherOrderItem(connection, record);
        }
    }

    private RuntimeException motherWriteFailure(String code, SQLException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        if (message.contains("courier_identity") || message.contains("uk_mother_order_courier")) {
            return new DuplicateCourierException();
        }
        return new IllegalStateException("Failed to save mother order: " + code, ex);
    }

    @Override
    public Optional<MotherOrderRecord> findMotherOrderByCode(String code) {
        try (Connection connection = dataSource.getConnection()) {
            return findMotherOrderByCode(connection, code);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load mother order: " + code, ex);
        }
    }

    @Override
    public Optional<MotherOrderRecord> findMotherOrderByCode(String code, WorkSession session) {
        try {
            return findMotherOrderByCode(requireConnection(session), code);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load mother order: " + code, ex);
        }
    }

    private Optional<MotherOrderRecord> findMotherOrderByCode(Connection connection, String code) throws SQLException {
        String sql = visibleMotherOrderSelect() + " and code = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapMotherOrder(rs));
            }
        }
    }

    @Override
    public List<MotherOrderRecord> listMotherOrdersByCourierNo(String courierNo) {
        if (courierNo == null || courierNo.isBlank()) {
            return List.of();
        }
        String sql = visibleMotherOrderSelect() + " and inbound_express_no = ? order by created_at desc, code";
        return queryMotherOrders(sql, courierNo);
    }

    @Override
    public List<MotherOrderRecord> listMotherOrdersByCourierNo(String courierNo, WorkSession session) {
        if (courierNo == null || courierNo.isBlank()) {
            return List.of();
        }
        String sql = visibleMotherOrderSelect() + " and inbound_express_no = ? order by created_at desc, code";
        try (PreparedStatement statement = requireConnection(session).prepareStatement(sql)) {
            statement.setString(1, courierNo);
            try (ResultSet rs = statement.executeQuery()) {
                List<MotherOrderRecord> found = new ArrayList<>();
                while (rs.next()) found.add(mapMotherOrder(rs));
                return found;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to query mother orders", ex);
        }
    }

    @Override
    public List<MotherOrderRecord> listMotherOrders() {
        return queryMotherOrders(visibleMotherOrderSelect() + " order by created_at desc", null);
    }

    @Override
    public List<ReceiptLine> listStoredReceiptLines(String motherOrderCode, WorkSession session) {
        try (PreparedStatement statement = requireConnection(session).prepareStatement("""
                select line_no, material_code, material_name_snapshot, goods_name_snapshot,
                       quantity, quantity_unit, weight, ledger_code, storage_conditions, storage_instruction
                  from biz_receipt_line where mother_order_code = ? order by line_no
                """)) {
            statement.setString(1, motherOrderCode);
            try (ResultSet rs = statement.executeQuery()) {
                List<ReceiptLine> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new ReceiptLine(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getBigDecimal(5), rs.getString(6), rs.getBigDecimal(7), rs.getString(8), com.zhubao.erp.business.domain.StorageRequirements.read(rs.getString(9), rs.getString(10))));
                }
                return rows;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("收货明细读取失败", ex);
        }
    }

    @Override
    public void replaceReceiptLine(String motherOrderCode, ReceiptLine line, WorkSession session) {
        try (PreparedStatement statement = requireConnection(session).prepareStatement("""
                update biz_receipt_line
                   set quantity = ?, weight = ?, ledger_code = ?
                 where mother_order_code = ? and line_no = ?
                """)) {
            statement.setBigDecimal(1, line.quantity());
            statement.setBigDecimal(2, line.weight());
            statement.setString(3, line.ledgerCode());
            statement.setString(4, motherOrderCode);
            statement.setInt(5, line.lineNo());
            if (statement.executeUpdate() != 1) {
                throw new BusinessException("收货明细不存在");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (SQLException ex) {
            throw new IllegalStateException("收货明细更新失败", ex);
        }
    }

    @Override
    public boolean motherHasDownstream(String motherOrderCode, WorkSession session) {
        // 各表使用锁定读，避免等待母单行锁后仍从旧快照漏读刚提交的拆单或交付。
        String[] tables = {"biz_work_order", "biz_child_line_allocation", "biz_process_station_handoff",
                "biz_outbound_order", "biz_outbound_order_item"};
        try {
            for (String table : tables) {
                String column = "biz_process_station_handoff".equals(table) ? "order_code" : "mother_order_code";
                try (PreparedStatement statement = requireConnection(session).prepareStatement(
                        "select 1 from " + table + " where " + column + " = ? limit 1 for update")) {
                    statement.setString(1, motherOrderCode);
                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next()) return true;
                    }
                }
            }
            return false;
        } catch (SQLException ex) {
            throw new IllegalStateException("无法核对母单下游事实", ex);
        }
    }

    @Override
    public List<ReceiptLine> listReceiptLines(String motherOrderCode) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select line_no, material_code, material_name_snapshot, goods_name_snapshot,
                            quantity, quantity_unit, weight, ledger_code, storage_conditions, storage_instruction
                       from biz_receipt_line where mother_order_code = ? order by line_no
                     """)) {
            statement.setString(1, motherOrderCode);
            try (ResultSet rs = statement.executeQuery()) {
                List<ReceiptLine> rows = new ArrayList<>();
                while (rs.next()) rows.add(new ReceiptLine(rs.getInt(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getBigDecimal(5), rs.getString(6), rs.getBigDecimal(7), rs.getString(8), com.zhubao.erp.business.domain.StorageRequirements.read(rs.getString(9), rs.getString(10))));
                if (!rows.isEmpty()) return rows;
            }
        } catch (SQLException ex) { throw new IllegalStateException("收货明细读取失败", ex); }
        return findMotherOrderByCode(motherOrderCode).map(mother -> List.of(new ReceiptLine(1,
                mother.materialCode(), mother.materialCode(), mother.goodsName(), mother.quantity(),
                mother.quantityUnit(), mother.weight(), mother.ledgerCode()))).orElse(List.of());
    }

    @Override
    public void saveReceiptLine(String motherOrderCode, ReceiptLine line, WorkSession session) {
        try (PreparedStatement statement = requireConnection(session).prepareStatement("""
                insert into biz_receipt_line (mother_order_code, line_no, material_code, material_name_snapshot,
                goods_name_snapshot, quantity, quantity_unit, weight, ledger_code, storage_conditions, storage_instruction)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, motherOrderCode);
            statement.setInt(2, line.lineNo());
            statement.setString(3, line.materialCode());
            statement.setString(4, line.materialName());
            statement.setString(5, line.goodsName());
            statement.setBigDecimal(6, line.quantity());
            statement.setString(7, line.quantityUnit());
            statement.setBigDecimal(8, line.weight());
            statement.setString(9, line.ledgerCode());
            statement.setString(10, line.storageRequirements() == null ? null : line.storageRequirements().encoded());
            statement.setString(11, line.storageRequirements() == null ? null : line.storageRequirements().instruction());
            statement.executeUpdate();
        } catch (SQLException ex) { throw new IllegalStateException("收货明细保存失败", ex); }
    }

    @Override
    public void updateMotherOrderSummary(String motherOrderCode, java.math.BigDecimal quantity,
                                         java.math.BigDecimal weight, WorkSession session) {
        try (PreparedStatement statement = requireConnection(session).prepareStatement(
                "update biz_mother_order set quantity = ?, weight = ? where code = ?")) {
            statement.setBigDecimal(1, quantity);
            statement.setBigDecimal(2, weight);
            statement.setString(3, motherOrderCode);
            if (statement.executeUpdate() != 1) throw new BusinessException("母单不存在：" + motherOrderCode);
        } catch (SQLException ex) { throw new IllegalStateException("母单汇总更新失败", ex); }
    }

    @Override
    public List<ChildLineAllocation> listChildAllocations(String motherOrderCode, WorkSession session) {
        String sql = "select child_order_code, mother_order_code, source_line_no, material_code, quantity, weight, ledger_code, storage_conditions, storage_instruction "
                + "from biz_child_line_allocation where mother_order_code = ? order by child_order_code, source_line_no";
        try {
            if (session == null) {
                try (Connection connection = dataSource.getConnection()) {
                    return loadAllocations(connection, sql, motherOrderCode);
                }
            }
            return loadAllocations(requireConnection(session), sql, motherOrderCode);
        } catch (SQLException ex) { throw new IllegalStateException("子单来源分配读取失败", ex); }
    }

    private List<ChildLineAllocation> loadAllocations(Connection connection, String sql, String motherOrderCode) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, motherOrderCode);
            try (ResultSet rs = statement.executeQuery()) {
                List<ChildLineAllocation> rows = new ArrayList<>();
                while (rs.next()) rows.add(new ChildLineAllocation(rs.getString(1), rs.getString(2), rs.getInt(3),
                        rs.getString(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getString(7), com.zhubao.erp.business.domain.StorageRequirements.read(rs.getString(8), rs.getString(9))));
                return rows;
            }
        }
    }

    @Override
    public void saveChildAllocation(ChildLineAllocation allocation, WorkSession session) {
        try (PreparedStatement statement = requireConnection(session).prepareStatement("""
                insert into biz_child_line_allocation (child_order_code, mother_order_code, source_line_no,
                material_code, quantity, weight, ledger_code, storage_conditions, storage_instruction) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, allocation.childOrderCode());
            statement.setString(2, allocation.motherOrderCode());
            statement.setInt(3, allocation.sourceLineNo());
            statement.setString(4, allocation.materialCode());
            statement.setBigDecimal(5, allocation.quantity());
            statement.setBigDecimal(6, allocation.weight());
            statement.setString(7, allocation.ledgerCode());
            statement.setString(8, allocation.storageRequirements() == null ? null : allocation.storageRequirements().encoded());
            statement.setString(9, allocation.storageRequirements() == null ? null : allocation.storageRequirements().instruction());
            statement.executeUpdate();
        } catch (SQLException ex) { throw new IllegalStateException("子单来源分配保存失败", ex); }
    }

    private String visibleMotherOrderSelect() {
        return motherOrderSelect() + " where recycled_at is null";
    }

    private String motherOrderSelect() {
        return """
                select code, customer_id, owner_customer_id, material_code, inbound_date,
                       inbound_express_company, inbound_express_no, sender_name_snapshot,
                       receiver_name_snapshot, unpack_signer_name, goods_name_snapshot,
                       owner_type, is_customer_owned, warehouse_code, quantity, quantity_unit,
                       weight, stock_status, ledger_code, source_register_type,
                       source_line_no, source_sequence_no, operator, evidence_code,
                       inbound_remark, order_status, created_at, processing_requirement, due_date, customer_name
                  from biz_mother_order
                """;
    }

    private List<MotherOrderRecord> queryMotherOrders(String sql, String parameter) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (parameter != null) {
                statement.setString(1, parameter);
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<MotherOrderRecord> records = new ArrayList<>();
                while (rs.next()) {
                    records.add(mapMotherOrder(rs));
                }
                return records;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to query mother orders", ex);
        }
    }

    @Override
    public boolean outboundOrderExists(String code) {
        return exists("select count(*) from biz_outbound_order where code = ?", code);
    }

    @Override
    public boolean outboundOrderExists(String code, WorkSession session) {
        return exists(requireConnection(session), "select count(*) from biz_outbound_order where code = ?", code);
    }

    @Override
    public OutboundOrderRecord saveOutboundOrder(OutboundOrderRecord record) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                writeOutbound(connection, record);
                connection.commit();
                return record;
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException ex) {
            throw outboundWriteFailure(record, ex);
        }
    }

    @Override
    public OutboundOrderRecord saveOutboundOrder(OutboundOrderRecord record, WorkSession session) {
        try {
            writeOutbound(requireConnection(session), record);
            return record;
        } catch (SQLException ex) {
            throw outboundWriteFailure(record, ex);
        }
    }

    private void writeOutbound(Connection connection, OutboundOrderRecord record) throws SQLException {
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
        }
        insertOutboundOrderItem(connection, record);
    }

    private RuntimeException outboundWriteFailure(OutboundOrderRecord record, SQLException ex) {
        if (isDuplicate(ex)) {
            return new BusinessException("结构化出库单已存在，不能重复确认出库：" + record.code());
        }
        return new IllegalStateException("Failed to save outbound order: " + record.code(), ex);
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

    @Override
    public List<OutboundOrderRecord> listOutboundOrders() {
        String sql = """
                select code, mother_order_code, work_order_code, customer_id, owner_customer_id,
                       material_code, from_warehouse_code, to_warehouse_code, delivery_date,
                       delivery_category, delivery_quantity, delivery_quantity_unit, delivery_weight,
                       express_company, express_no, logistics_raw_text, handler_name_snapshot,
                       source_register_type, source_line_no, source_sequence_no, operator,
                       evidence_code, outbound_remark, order_status, ledger_code, created_at
                  from biz_outbound_order
                 order by code
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<OutboundOrderRecord> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(mapOutboundOrder(rs));
            }
            return List.copyOf(rows);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to list outbound orders", ex);
        }
    }

    @Override
    public void saveMotherSplitState(MotherSplitState state) {
        try (Connection connection = dataSource.getConnection()) {
            writeMotherSplitState(connection, state);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save mother split state: " + state.motherOrderCode(), ex);
        }
    }

    @Override
    public void saveMotherSplitState(MotherSplitState state, WorkSession session) {
        try {
            writeMotherSplitState(requireConnection(session), state);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save mother split state: " + state.motherOrderCode(), ex);
        }
    }

    @Override
    public Optional<MotherSplitState> findMotherSplitState(String motherOrderCode) {
        try (Connection connection = dataSource.getConnection()) {
            return findMotherSplitState(connection, motherOrderCode);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load mother split state: " + motherOrderCode, ex);
        }
    }

    @Override
    public Optional<MotherSplitState> findMotherSplitState(String motherOrderCode, WorkSession session) {
        try {
            return findMotherSplitState(requireConnection(session), motherOrderCode);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load mother split state: " + motherOrderCode, ex);
        }
    }

    @Override
    public void lockMotherOrder(String motherOrderCode, WorkSession session) {
        if (!lockMotherOrderIfPresent(motherOrderCode, session)) {
            throw new BusinessException("母单不存在：" + motherOrderCode);
        }
    }

    @Override
    public boolean lockMotherOrderIfPresent(String motherOrderCode, WorkSession session) {
        String sql = "select code from biz_mother_order where code = ? for update";
        try (PreparedStatement statement = requireConnection(session).prepareStatement(sql)) {
            statement.setString(1, motherOrderCode);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法锁定母单：" + motherOrderCode, ex);
        }
    }

    @Override
    public List<MotherSplitState> listAllMotherSplitStates() {
        String sql = """
                select mother_order_code, split_decision, verification_code, operator, created_at
                  from biz_mother_order_split
                 order by mother_order_code
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<MotherSplitState> states = new ArrayList<>();
            while (rs.next()) {
                states.add(new MotherSplitState(
                        rs.getString("mother_order_code"),
                        rs.getString("split_decision"),
                        rs.getString("verification_code"),
                        rs.getString("operator"),
                        rs.getString("created_at")
                ));
            }
            return states;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to list mother split states", ex);
        }
    }

    @Override
    public boolean childWorkOrderExists(String code) {
        return exists("select count(*) from biz_work_order where code = ?", code);
    }

    @Override
    public boolean childWorkOrderExists(String code, WorkSession session) {
        return exists(requireConnection(session), "select count(*) from biz_work_order where code = ?", code);
    }

    @Override
    public ChildWorkOrderRecord saveChildWorkOrder(ChildWorkOrderRecord record) {
        try (Connection connection = dataSource.getConnection()) {
            writeChildWorkOrder(connection, record);
            return record;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save work order: " + record.code(), ex);
        }
    }

    @Override
    public ChildWorkOrderRecord saveChildWorkOrder(ChildWorkOrderRecord record, WorkSession session) {
        try {
            writeChildWorkOrder(requireConnection(session), record);
            return record;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save work order: " + record.code(), ex);
        }
    }

    @Override
    public Optional<ChildWorkOrderRecord> findChildWorkOrderByCode(String code) {
        return queryChildWorkOrders(visibleChildWorkOrderSelect() + " and code = ?", code).stream().findFirst();
    }

    @Override
    public List<ChildWorkOrderRecord> listChildWorkOrders(String motherOrderCode) {
        return queryChildWorkOrders(
                visibleChildWorkOrderSelect() + " and mother_order_code = ? order by code",
                motherOrderCode
        );
    }

    @Override
    public List<ChildWorkOrderRecord> listChildWorkOrders(String motherOrderCode, WorkSession session) {
        try {
            return queryChildWorkOrders(
                    requireConnection(session),
                    visibleChildWorkOrderSelect() + " and mother_order_code = ? order by code",
                    motherOrderCode == null ? List.of() : List.of(motherOrderCode)
            );
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to query work orders", ex);
        }
    }

    @Override
    public List<ChildWorkOrderRecord> listAllChildWorkOrders() {
        return queryChildWorkOrders(visibleChildWorkOrderSelect() + " order by code", List.of());
    }

    @Override
    public boolean verificationCodeExists(String verificationCode) {
        if (verificationCode == null || verificationCode.isBlank()) {
            return false;
        }
        return exists("select count(*) from biz_mother_order_split where verification_code = ?", verificationCode)
                || exists("select count(*) from biz_work_order where verification_code = ?", verificationCode);
    }

    @Override
    public boolean verificationCodeExists(String verificationCode, WorkSession session) {
        if (verificationCode == null || verificationCode.isBlank()) {
            return false;
        }
        Connection connection = requireConnection(session);
        return exists(connection, "select count(*) from biz_mother_order_split where verification_code = ?", verificationCode)
                || exists(connection, "select count(*) from biz_work_order where verification_code = ?", verificationCode);
    }

    @Override
    public LabelReprintLog saveLabelReprint(LabelReprintLog log) {
        String sql = """
                insert into biz_label_reprint_log (
                    label_type, business_code, verification_code, operator, reason, reprinted_at
                ) values (?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, log.labelType());
            statement.setString(2, log.businessCode());
            statement.setString(3, log.verificationCode());
            statement.setString(4, log.operator());
            statement.setString(5, log.reason());
            statement.setString(6, log.reprintedAt());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                Long id = keys.next() ? keys.getLong(1) : null;
                return new LabelReprintLog(
                        id,
                        log.labelType(),
                        log.businessCode(),
                        log.verificationCode(),
                        log.operator(),
                        log.reason(),
                        log.reprintedAt()
                );
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save label reprint: " + log.businessCode(), ex);
        }
    }

    @Override
    public List<LabelReprintLog> listLabelReprints(String labelType, String businessCode) {
        String sql = """
                select id, label_type, business_code, verification_code, operator, reason, reprinted_at
                  from biz_label_reprint_log
                 where label_type = ? and business_code = ?
                 order by id
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, labelType);
            statement.setString(2, businessCode);
            try (ResultSet rs = statement.executeQuery()) {
                List<LabelReprintLog> logs = new ArrayList<>();
                while (rs.next()) {
                    logs.add(new LabelReprintLog(
                            rs.getLong("id"),
                            rs.getString("label_type"),
                            rs.getString("business_code"),
                            rs.getString("verification_code"),
                            rs.getString("operator"),
                            rs.getString("reason"),
                            rs.getString("reprinted_at")
                    ));
                }
                return logs;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to list label reprints: " + businessCode, ex);
        }
    }

    @Override
    public void saveProcessStationHandoff(ProcessStationHandoff handoff) {
        try (Connection connection = dataSource.getConnection()) {
            writeHandoff(connection, handoff);
        } catch (SQLException ex) {
            throw handoffWriteFailure(handoff, ex);
        }
    }

    @Override
    public void saveProcessStationHandoff(ProcessStationHandoff handoff, WorkSession session) {
        try {
            writeHandoff(requireConnection(session), handoff);
        } catch (SQLException ex) {
            throw handoffWriteFailure(handoff, ex);
        }
    }

    @Override
    public Optional<ProcessStationHandoff> findProcessStationHandoff(String orderCode, String processName) {
        try (Connection connection = dataSource.getConnection()) {
            return findProcessStationHandoff(connection, orderCode, processName);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load process station handoff: " + orderCode, ex);
        }
    }

    @Override
    public Optional<ProcessStationHandoff> findProcessStationHandoff(String orderCode, String processName, WorkSession session) {
        try {
            return findProcessStationHandoff(requireConnection(session), orderCode, processName, true);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load process station handoff: " + orderCode, ex);
        }
    }

    @Override
    public List<ProcessStationHandoff> listProcessStationHandoffs(String orderCode) {
        String sql = """
                select order_code, process_name, operator, ledger_code, created_at
                  from biz_process_station_handoff
                 where order_code = ?
                 order by id
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, orderCode);
            try (ResultSet rs = statement.executeQuery()) {
                List<ProcessStationHandoff> handoffs = new ArrayList<>();
                while (rs.next()) {
                    handoffs.add(mapProcessStationHandoff(rs));
                }
                return handoffs;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to list process station handoffs: " + orderCode, ex);
        }
    }

    @Override
    public List<ProcessStationHandoff> listAllProcessStationHandoffs() {
        String sql = """
                select order_code, process_name, operator, ledger_code, created_at
                  from biz_process_station_handoff
                 order by id
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<ProcessStationHandoff> handoffs = new ArrayList<>();
            while (rs.next()) {
                handoffs.add(mapProcessStationHandoff(rs));
            }
            return handoffs;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to list process station handoffs", ex);
        }
    }

    @Override
    public void saveQualityRequirementCheck(QualityRequirementCheck check) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                writeQualityCheck(connection, check);
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw qualityCheckFailure(check, ex);
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (SQLException ex) {
            throw qualityCheckFailure(check, ex);
        }
    }

    @Override
    public void saveQualityRequirementCheck(QualityRequirementCheck check, WorkSession session) {
        try {
            writeQualityCheck(requireConnection(session), check);
        } catch (SQLException ex) {
            throw qualityCheckFailure(check, ex);
        }
    }

    @Override
    public Optional<QualityRequirementCheck> findQualityRequirementCheck(String orderCode) {
        try (Connection connection = dataSource.getConnection()) {
            return findQualityRequirementCheck(connection, orderCode);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load quality requirement check: " + orderCode, ex);
        }
    }

    @Override
    public Optional<QualityRequirementCheck> findQualityRequirementCheck(String orderCode, WorkSession session) {
        try {
            return findQualityRequirementCheck(requireConnection(session), orderCode);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load quality requirement check: " + orderCode, ex);
        }
    }

    @Override
    public List<QualityRequirementCheck> listAllQualityRequirementChecks() {
        String sql = """
                select quality_case_code, order_code, mother_order_code, inspector, outcome,
                       frozen, ledger_code, rework_order_code, created_at
                  from biz_quality_requirement_check
                 order by order_code
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<QualityRequirementCheck> checks = new ArrayList<>();
            while (rs.next()) {
                checks.add(new QualityRequirementCheck(
                        rs.getString("quality_case_code"),
                        rs.getString("order_code"),
                        rs.getString("mother_order_code"),
                        rs.getString("inspector"),
                        rs.getString("outcome"),
                        rs.getInt("frozen") == 1,
                        rs.getString("ledger_code"),
                        rs.getString("rework_order_code"),
                        rs.getString("created_at"),
                        List.of()
                ));
            }
            return checks;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to list quality requirement checks", ex);
        }
    }

    @Override
    public void attachQualityRework(String orderCode, String reworkOrderCode) {
        try (Connection connection = dataSource.getConnection()) {
            writeQualityRework(connection, orderCode, reworkOrderCode);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to attach quality rework: " + orderCode, ex);
        }
    }

    @Override
    public void attachQualityRework(String orderCode, String reworkOrderCode, WorkSession session) {
        try {
            writeQualityRework(requireConnection(session), orderCode, reworkOrderCode);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to attach quality rework: " + orderCode, ex);
        }
    }

    @Override
    public void saveQualityRound(QualityRound round) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                writeQualityRound(connection, round);
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw qualityRoundFailure(round, ex);
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (SQLException ex) {
            throw qualityRoundFailure(round, ex);
        }
    }

    @Override
    public void saveQualityRound(QualityRound round, WorkSession session) {
        try {
            writeQualityRound(requireConnection(session), round);
        } catch (SQLException ex) {
            throw qualityRoundFailure(round, ex);
        }
    }

    @Override
    public List<QualityRound> listQualityRounds(String orderCode) {
        try (Connection connection = dataSource.getConnection()) {
            return listQualityRounds(connection, orderCode);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to list quality rounds: " + orderCode, ex);
        }
    }

    @Override
    public List<QualityRound> listQualityRounds(String orderCode, WorkSession session) {
        try {
            return listQualityRounds(requireConnection(session), orderCode);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to list quality rounds: " + orderCode, ex);
        }
    }

    @Override
    public void saveQualityReworkReport(QualityReworkReport report) {
        try (Connection connection = dataSource.getConnection()) {
            writeQualityReworkReport(connection, report);
        } catch (SQLException ex) {
            throw qualityReportFailure(report, ex);
        }
    }

    @Override
    public void saveQualityReworkReport(QualityReworkReport report, WorkSession session) {
        try {
            writeQualityReworkReport(requireConnection(session), report);
        } catch (SQLException ex) {
            throw qualityReportFailure(report, ex);
        }
    }

    @Override
    public Optional<QualityReworkReport> findQualityReworkReport(String reworkOrderCode) {
        try (Connection connection = dataSource.getConnection()) {
            return findQualityReworkReport(connection, reworkOrderCode);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load quality rework report: " + reworkOrderCode, ex);
        }
    }

    @Override
    public Optional<QualityReworkReport> findQualityReworkReport(String reworkOrderCode, WorkSession session) {
        try {
            return findQualityReworkReport(requireConnection(session), reworkOrderCode);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load quality rework report: " + reworkOrderCode, ex);
        }
    }

    private void writeQualityCheck(Connection connection, QualityRequirementCheck check) throws SQLException {
        String headerSql = """
                insert into biz_quality_requirement_check (
                    quality_case_code, order_code, mother_order_code, inspector, outcome,
                    frozen, ledger_code, rework_order_code, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String lineSql = """
                insert into biz_quality_requirement_line (
                    quality_case_code, line_no, clause_text, verdict, reason
                ) values (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement header = connection.prepareStatement(headerSql)) {
            header.setString(1, check.qualityCaseCode());
            header.setString(2, check.orderCode());
            header.setString(3, check.motherOrderCode());
            header.setString(4, check.inspector());
            header.setString(5, check.outcome());
            header.setInt(6, check.frozen() ? 1 : 0);
            header.setString(7, check.ledgerCode());
            header.setString(8, check.reworkOrderCode());
            header.setString(9, check.createdAt());
            header.executeUpdate();
        }
        try (PreparedStatement line = connection.prepareStatement(lineSql)) {
            for (QualityCheckLine item : check.lines()) {
                line.setString(1, check.qualityCaseCode());
                line.setInt(2, item.lineNo());
                line.setString(3, item.clause());
                line.setString(4, item.verdict());
                line.setString(5, item.reason());
                line.addBatch();
            }
            if (!check.lines().isEmpty()) {
                line.executeBatch();
            }
        }
    }

    private Optional<QualityRequirementCheck> findQualityRequirementCheck(Connection connection, String orderCode) throws SQLException {
        String sql = """
                select quality_case_code, order_code, mother_order_code, inspector, outcome,
                       frozen, ledger_code, rework_order_code, created_at
                  from biz_quality_requirement_check
                 where order_code = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, orderCode);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                String qualityCaseCode = rs.getString("quality_case_code");
                return Optional.of(new QualityRequirementCheck(
                        qualityCaseCode,
                        rs.getString("order_code"),
                        rs.getString("mother_order_code"),
                        rs.getString("inspector"),
                        rs.getString("outcome"),
                        rs.getInt("frozen") == 1,
                        rs.getString("ledger_code"),
                        rs.getString("rework_order_code"),
                        rs.getString("created_at"),
                        loadQualityLines(connection, qualityCaseCode)
                ));
            }
        }
    }

    private void writeQualityRework(Connection connection, String orderCode, String reworkOrderCode) throws SQLException {
        String sql = """
                update biz_quality_requirement_check
                   set rework_order_code = ?
                 where order_code = ?
                   and rework_order_code = ''
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, reworkOrderCode);
            statement.setString(2, orderCode);
            int updated = statement.executeUpdate();
            if (updated == 0) {
                if (findQualityRequirementCheck(connection, orderCode).isEmpty()) {
                    throw new BusinessException("请先提交质检对照");
                }
                throw new BusinessException("已经开过返工");
            }
        }
    }

    private void writeQualityRound(Connection connection, QualityRound round) throws SQLException {
        String headerSql = """
                insert into biz_quality_round (
                    round_code, quality_case_code, order_code, mother_order_code, round_no, round_type,
                    rework_order_code, rework_report_ledger, outcome, frozen, ledger_code, inspector, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String lineSql = """
                insert into biz_quality_round_line (
                    round_code, line_no, clause_text, verdict, reason
                ) values (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement header = connection.prepareStatement(headerSql)) {
            header.setString(1, round.roundCode());
            header.setString(2, round.qualityCaseCode());
            header.setString(3, round.orderCode());
            header.setString(4, round.motherOrderCode());
            header.setInt(5, round.roundNo());
            header.setString(6, round.roundType());
            header.setString(7, round.reworkOrderCode());
            header.setString(8, round.reworkReportLedger());
            header.setString(9, round.outcome());
            header.setInt(10, round.frozen() ? 1 : 0);
            header.setString(11, round.ledgerCode());
            header.setString(12, round.inspector());
            header.setString(13, round.createdAt());
            header.executeUpdate();
        }
        try (PreparedStatement line = connection.prepareStatement(lineSql)) {
            for (QualityCheckLine item : round.lines()) {
                line.setString(1, round.roundCode());
                line.setInt(2, item.lineNo());
                line.setString(3, item.clause());
                line.setString(4, item.verdict());
                line.setString(5, item.reason());
                line.addBatch();
            }
            if (!round.lines().isEmpty()) {
                line.executeBatch();
            }
        }
    }

    private List<QualityRound> listQualityRounds(Connection connection, String orderCode) throws SQLException {
        String sql = """
                select round_code, quality_case_code, order_code, mother_order_code, round_no, round_type,
                       rework_order_code, rework_report_ledger, outcome, frozen, ledger_code, inspector, created_at
                  from biz_quality_round
                 where order_code = ?
                 order by round_no
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, orderCode);
            try (ResultSet rs = statement.executeQuery()) {
                List<QualityRound> rounds = new ArrayList<>();
                while (rs.next()) {
                    String roundCode = rs.getString("round_code");
                    rounds.add(new QualityRound(
                            roundCode,
                            rs.getString("quality_case_code"),
                            rs.getString("order_code"),
                            rs.getString("mother_order_code"),
                            rs.getInt("round_no"),
                            rs.getString("round_type"),
                            rs.getString("rework_order_code"),
                            rs.getString("rework_report_ledger"),
                            rs.getString("outcome"),
                            rs.getInt("frozen") == 1,
                            rs.getString("ledger_code"),
                            rs.getString("inspector"),
                            rs.getString("created_at"),
                            loadRoundLines(connection, roundCode)
                    ));
                }
                return rounds;
            }
        }
    }

    private void writeQualityReworkReport(Connection connection, QualityReworkReport report) throws SQLException {
        String sql = """
                insert into biz_quality_rework_report (
                    rework_order_code, quality_case_code, order_code, mother_order_code,
                    ledger_code, operator_name, created_at
                ) values (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, report.reworkOrderCode());
            statement.setString(2, report.qualityCaseCode());
            statement.setString(3, report.orderCode());
            statement.setString(4, report.motherOrderCode());
            statement.setString(5, report.ledgerCode());
            statement.setString(6, report.operator());
            statement.setString(7, report.createdAt());
            statement.executeUpdate();
        }
    }

    private Optional<QualityReworkReport> findQualityReworkReport(Connection connection, String reworkOrderCode) throws SQLException {
        String sql = """
                select rework_order_code, quality_case_code, order_code, mother_order_code,
                       ledger_code, operator_name, created_at
                  from biz_quality_rework_report
                 where rework_order_code = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, reworkOrderCode);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new QualityReworkReport(
                        rs.getString("rework_order_code"),
                        rs.getString("quality_case_code"),
                        rs.getString("order_code"),
                        rs.getString("mother_order_code"),
                        rs.getString("ledger_code"),
                        rs.getString("operator_name"),
                        rs.getString("created_at")
                ));
            }
        }
    }

    private List<QualityCheckLine> loadQualityLines(Connection connection, String qualityCaseCode) throws SQLException {
        return loadLines(connection, """
                select line_no, clause_text, verdict, reason
                  from biz_quality_requirement_line
                 where quality_case_code = ?
                 order by line_no
                """, qualityCaseCode);
    }

    private List<QualityCheckLine> loadRoundLines(Connection connection, String roundCode) throws SQLException {
        return loadLines(connection, """
                select line_no, clause_text, verdict, reason
                  from biz_quality_round_line
                 where round_code = ?
                 order by line_no
                """, roundCode);
    }

    private List<QualityCheckLine> loadLines(Connection connection, String sql, String code) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            try (ResultSet rs = statement.executeQuery()) {
                List<QualityCheckLine> lines = new ArrayList<>();
                while (rs.next()) {
                    lines.add(new QualityCheckLine(
                            rs.getInt("line_no"),
                            rs.getString("clause_text"),
                            rs.getString("verdict"),
                            rs.getString("reason")
                    ));
                }
                return lines;
            }
        }
    }

    private RuntimeException qualityCheckFailure(QualityRequirementCheck check, SQLException ex) {
        if (isDuplicate(ex)) {
            return new BusinessException("这张单已经做过质检对照");
        }
        return new IllegalStateException("Failed to save quality requirement check: " + check.orderCode(), ex);
    }

    private RuntimeException qualityRoundFailure(QualityRound round, SQLException ex) {
        if (isDuplicate(ex)) {
            return new BusinessException("这一轮质检已经提交");
        }
        return new IllegalStateException("Failed to save quality round: " + round.roundCode(), ex);
    }

    private RuntimeException qualityReportFailure(QualityReworkReport report, SQLException ex) {
        if (isDuplicate(ex)) {
            return new BusinessException("返工已经报过工");
        }
        return new IllegalStateException("Failed to save quality rework report: " + report.reworkOrderCode(), ex);
    }

    private void writeHandoff(Connection connection, ProcessStationHandoff handoff) throws SQLException {
        String sql = """
                insert into biz_process_station_handoff (
                    order_code, process_name, operator, ledger_code, created_at
                ) values (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, handoff.orderCode());
            statement.setString(2, handoff.processName());
            statement.setString(3, handoff.operator());
            statement.setString(4, handoff.ledgerCode());
            statement.setString(5, handoff.createdAt());
            statement.executeUpdate();
        }
    }

    private Optional<ProcessStationHandoff> findProcessStationHandoff(Connection connection, String orderCode, String processName)
            throws SQLException {
        return findProcessStationHandoff(connection, orderCode, processName, false);
    }

    private Optional<ProcessStationHandoff> findProcessStationHandoff(Connection connection, String orderCode, String processName, boolean lock) throws SQLException {
        String sql = "select order_code, process_name, operator, ledger_code, created_at from biz_process_station_handoff where order_code = ? and process_name = ?" + (lock ? " for update" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, orderCode);
            statement.setString(2, processName);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapProcessStationHandoff(rs));
            }
        }
    }

    private RuntimeException handoffWriteFailure(ProcessStationHandoff handoff, SQLException ex) {
        if (isDuplicate(ex)) {
            return new BusinessException("这道工序已经交过");
        }
        return new IllegalStateException("Failed to save process station handoff: " + handoff.orderCode(), ex);
    }

    private ProcessStationHandoff mapProcessStationHandoff(ResultSet rs) throws SQLException {
        return new ProcessStationHandoff(
                rs.getString("order_code"),
                rs.getString("process_name"),
                rs.getString("operator"),
                rs.getString("ledger_code"),
                rs.getString("created_at")
        );
    }

    private boolean isDuplicate(SQLException ex) {
        if (ex.getErrorCode() == 1062) {
            return true;
        }
        String state = ex.getSQLState();
        return state != null && state.startsWith("23");
    }

    private String visibleChildWorkOrderSelect() {
        return childWorkOrderSelect() + """
                 where recycled_at is null
                   and mother_order_code not in (
                       select code from biz_mother_order where recycled_at is not null
                   )
                """;
    }

    private String childWorkOrderSelect() {
        return """
                select code, mother_order_code, customer_id, customer_name, processing_requirement, due_date,
                       material_code, goods_name, quantity, quantity_unit, weight, warehouse_code,
                       process_names, verification_code, ledger_code, operator, created_at
                  from biz_work_order
                """;
    }

    private void writeMotherSplitState(Connection connection, MotherSplitState state) throws SQLException {
        String sql = """
                insert into biz_mother_order_split (
                    mother_order_code, split_decision, verification_code, operator, created_at
                ) values (?, ?, ?, ?, ?)
                on duplicate key update
                    split_decision = values(split_decision),
                    verification_code = values(verification_code),
                    operator = values(operator),
                    created_at = values(created_at)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, state.motherOrderCode());
            statement.setString(2, state.decision());
            statement.setString(3, state.verificationCode());
            statement.setString(4, state.operator());
            statement.setString(5, state.createdAt());
            statement.executeUpdate();
        }
    }

    private Optional<MotherSplitState> findMotherSplitState(Connection connection, String motherOrderCode) throws SQLException {
        String sql = """
                select mother_order_code, split_decision, verification_code, operator, created_at
                  from biz_mother_order_split
                 where mother_order_code = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, motherOrderCode);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new MotherSplitState(
                        rs.getString("mother_order_code"),
                        rs.getString("split_decision"),
                        rs.getString("verification_code"),
                        rs.getString("operator"),
                        rs.getString("created_at")
                ));
            }
        }
    }

    private void writeChildWorkOrder(Connection connection, ChildWorkOrderRecord record) throws SQLException {
        String sql = """
                insert into biz_work_order (
                    code, mother_order_code, customer_id, customer_name, processing_requirement, due_date,
                    material_code, goods_name, quantity, quantity_unit, weight, warehouse_code,
                    process_names, verification_code, ledger_code, operator, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.code());
            statement.setString(2, record.motherOrderCode());
            setLong(statement, 3, record.customerId());
            statement.setString(4, record.customerName());
            statement.setString(5, record.processingRequirement());
            statement.setString(6, record.dueDate());
            statement.setString(7, record.materialCode());
            statement.setString(8, record.goodsName());
            statement.setBigDecimal(9, record.quantity());
            statement.setString(10, record.quantityUnit());
            statement.setBigDecimal(11, record.weight());
            statement.setString(12, record.warehouseCode());
            statement.setString(13, record.processes());
            statement.setString(14, record.verificationCode());
            statement.setString(15, record.ledgerCode());
            statement.setString(16, record.operator());
            statement.setString(17, record.createdAt());
            statement.executeUpdate();
        }
    }

    private List<ChildWorkOrderRecord> queryChildWorkOrders(String sql, String parameter) {
        return queryChildWorkOrders(sql, parameter == null ? List.of() : List.of(parameter));
    }

    private List<ChildWorkOrderRecord> queryChildWorkOrders(String sql, List<String> parameters) {
        try (Connection connection = dataSource.getConnection()) {
            return queryChildWorkOrders(connection, sql, parameters);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to query work orders", ex);
        }
    }

    private List<ChildWorkOrderRecord> queryChildWorkOrders(
            Connection connection,
            String sql,
            List<String> parameters
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.size(); index++) {
                statement.setString(index + 1, parameters.get(index));
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<ChildWorkOrderRecord> records = new ArrayList<>();
                while (rs.next()) {
                    records.add(mapChildWorkOrder(rs));
                }
                return records;
            }
        }
    }

    private ChildWorkOrderRecord mapChildWorkOrder(ResultSet rs) throws SQLException {
        return new ChildWorkOrderRecord(
                rs.getString("code"),
                rs.getString("mother_order_code"),
                nullableLong(rs, "customer_id"),
                rs.getString("customer_name"),
                rs.getString("processing_requirement"),
                rs.getString("due_date"),
                rs.getString("material_code"),
                rs.getString("goods_name"),
                rs.getBigDecimal("quantity"),
                rs.getString("quantity_unit"),
                rs.getBigDecimal("weight"),
                rs.getString("warehouse_code"),
                rs.getString("process_names"),
                rs.getString("verification_code"),
                rs.getString("ledger_code"),
                rs.getString("operator"),
                rs.getString("created_at")
        );
    }

    private boolean exists(String sql, String code) {
        try (Connection connection = dataSource.getConnection()) {
            return exists(connection, sql, code);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to check business document code: " + code, ex);
        }
    }

    private boolean exists(Connection connection, String sql, String code) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to check business document code: " + code, ex);
        }
    }

    private Connection requireConnection(WorkSession session) {
        if (session == null || session.connection().isEmpty()) {
            throw new IllegalStateException("单据写入必须使用工作单元连接");
        }
        return session.connection().get();
    }

    private void setCourierIdentity(PreparedStatement statement, int index, String courierNo) throws SQLException {
        if (courierNo == null || courierNo.isBlank()) {
            statement.setNull(index, Types.VARCHAR);
            return;
        }
        statement.setString(index, courierNo.trim());
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
                rs.getString("created_at"),
                rs.getString("processing_requirement"),
                rs.getString("due_date"),
                rs.getString("customer_name")
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

    @Override
    public Optional<MotherOrderRecord> findMotherOrderAny(String code) {
        return queryMotherOrders(motherOrderSelect() + " where code = ?", code).stream().findFirst();
    }

    @Override
    public Optional<MotherOrderRecord> findMotherOrderAny(String code, WorkSession session) {
        try (PreparedStatement statement = requireConnection(session).prepareStatement(
                motherOrderSelect() + " where code = ? for update")) {
            statement.setString(1, code);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapMotherOrder(result)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("读取母单失败", ex);
        }
    }

    @Override
    public Optional<ChildWorkOrderRecord> findChildWorkOrderAny(String code) {
        return queryChildWorkOrders(childWorkOrderSelect() + " where code = ?", code).stream().findFirst();
    }

    @Override
    public Optional<ChildWorkOrderRecord> findChildWorkOrderAny(String code, WorkSession session) {
        try {
            return queryChildWorkOrders(requireConnection(session), childWorkOrderSelect() + " where code = ? for update",
                    List.of(code)).stream().findFirst();
        } catch (SQLException ex) {
            throw new IllegalStateException("读取子单失败", ex);
        }
    }

    @Override
    public void lockChildWorkOrder(String code, WorkSession session) {
        try (PreparedStatement statement = requireConnection(session).prepareStatement(
                "select code from biz_work_order where code = ? for update")) {
            statement.setString(1, code);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new BusinessException("子单不存在：" + code);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法锁定子单", ex);
        }
    }

    @Override
    public List<ChildWorkOrderRecord> listChildWorkOrdersAny(String motherOrderCode) {
        return queryChildWorkOrders(childWorkOrderSelect() + " where mother_order_code = ? order by code", motherOrderCode);
    }

    @Override
    public Optional<String> childMotherCode(String childCode) {
        String sql = "select mother_order_code from biz_work_order where code = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, childCode);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.ofNullable(result.getString(1)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("读取子单所属母单失败", ex);
        }
    }

    @Override
    public List<ChildWorkOrderRecord> listChildWorkOrdersAny(String motherOrderCode, WorkSession session) {
        try {
            return queryChildWorkOrders(requireConnection(session),
                    childWorkOrderSelect() + " where mother_order_code = ? order by code for update",
                    List.of(motherOrderCode));
        } catch (SQLException ex) {
            throw new IllegalStateException("读取子单失败", ex);
        }
    }

    @Override
    public void replaceMotherOrder(MotherOrderRecord record) {
        try (Connection connection = dataSource.getConnection()) {
            updateMotherOrder(connection, record);
        } catch (SQLException ex) {
            throw new IllegalStateException("修改母单失败", ex);
        }
    }

    private void updateMotherOrder(Connection connection, MotherOrderRecord record) throws SQLException {
        String sql = """
                update biz_mother_order
                   set customer_id = ?, customer_name = ?, material_code = ?, goods_name_snapshot = ?,
                       quantity = ?, weight = ?, processing_requirement = ?, due_date = ?, inbound_remark = ?
                 where code = ?
                """;
        updateOne(connection, sql, statement -> {
            setLong(statement, 1, record.customerId());
            statement.setString(2, record.customerName());
            statement.setString(3, record.materialCode());
            statement.setString(4, record.goodsName());
            statement.setBigDecimal(5, record.quantity());
            statement.setBigDecimal(6, record.weight());
            statement.setString(7, record.processingRequirement());
            statement.setString(8, record.dueDate());
            statement.setString(9, record.remark());
            statement.setString(10, record.code());
        }, "母单不存在");
    }

    @Override
    public void replaceChildWorkOrder(ChildWorkOrderRecord record) {
        try (Connection connection = dataSource.getConnection()) {
            updateChildWorkOrder(connection, record);
        } catch (SQLException ex) {
            throw new IllegalStateException("修改子单失败", ex);
        }
    }

    private void updateChildWorkOrder(Connection connection, ChildWorkOrderRecord record) throws SQLException {
        String sql = """
                update biz_work_order
                   set customer_id = ?, customer_name = ?, material_code = ?, goods_name = ?,
                       quantity = ?, weight = ?, processing_requirement = ?, due_date = ?, process_names = ?
                 where code = ?
                """;
        updateOne(connection, sql, statement -> {
            setLong(statement, 1, record.customerId());
            statement.setString(2, record.customerName());
            statement.setString(3, record.materialCode());
            statement.setString(4, record.goodsName());
            statement.setBigDecimal(5, record.quantity());
            statement.setBigDecimal(6, record.weight());
            statement.setString(7, record.processingRequirement());
            statement.setString(8, record.dueDate());
            statement.setString(9, record.processes());
            statement.setString(10, record.code());
        }, "子单不存在");
    }

    @Override
    public void correctMotherOrder(MotherOrderRecord record, OrderAdjustment adjustment) {
        correctOrder(connection -> updateMotherOrder(connection, record), adjustment);
    }

    @Override
    public void correctMotherOrder(MotherOrderRecord record, OrderAdjustment adjustment, WorkSession session) {
        try {
            Connection connection = requireConnection(session);
            updateMotherOrder(connection, record);
            insertOrderAdjustment(connection, adjustment);
        } catch (SQLException ex) {
            throw new IllegalStateException("改单与调整记录保存失败", ex);
        }
    }

    @Override
    public void correctMotherBasicInfo(MotherOrderRecord record, OrderAdjustment adjustment, WorkSession session) {
        Connection connection = requireConnection(session);
        try {
            updateOne(connection, "update biz_mother_order set owner_customer_id = ?, ledger_code = ? where code = ?", statement -> {
                setLong(statement, 1, record.ownerCustomerId());
                statement.setString(2, record.ledgerCode());
                statement.setString(3, record.code());
            }, "母单不存在");
            correctMotherOrder(record, adjustment, session);
        } catch (SQLException ex) {
            throw new IllegalStateException("母单基础信息更正失败", ex);
        }
    }

    @Override
    public void correctChildWorkOrder(ChildWorkOrderRecord record, OrderAdjustment adjustment) {
        correctOrder(connection -> updateChildWorkOrder(connection, record), adjustment);
    }

    @Override
    public void correctChildWorkOrder(ChildWorkOrderRecord record, OrderAdjustment adjustment, WorkSession session) {
        try {
            Connection connection = requireConnection(session);
            updateChildWorkOrder(connection, record);
            insertOrderAdjustment(connection, adjustment);
        } catch (SQLException ex) {
            throw new IllegalStateException("改单与调整记录保存失败", ex);
        }
    }

    private void correctOrder(SqlCorrection correction, OrderAdjustment adjustment) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                correction.apply(connection);
                insertOrderAdjustment(connection, adjustment);
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("改单与调整记录保存失败", ex);
        }
    }

    @Override
    public void recycleMother(String code, String operator, String at, String batch, String reason) {
        recycle("biz_mother_order", code, operator, at, batch, reason, "母单不存在或已在回收站");
    }

    @Override
    public void recycleMother(String code, String operator, String at, String batch, String reason, WorkSession session) {
        recycle("biz_mother_order", code, operator, at, batch, reason, "母单不存在或已在回收站", session);
    }

    @Override
    public void recycleChild(String code, String operator, String at, String batch, String reason) {
        recycle("biz_work_order", code, operator, at, batch, reason, "子单不存在或已在回收站");
    }

    @Override
    public void recycleChild(String code, String operator, String at, String batch, String reason, WorkSession session) {
        recycle("biz_work_order", code, operator, at, batch, reason, "子单不存在或已在回收站", session);
    }

    @Override
    public Optional<OrderRecycleMark> motherRecycle(String code) {
        return recycleMark("MOTHER", "biz_mother_order", code);
    }

    @Override
    public Optional<OrderRecycleMark> motherRecycle(String code, WorkSession session) {
        return recycleMark("MOTHER", "biz_mother_order", code, session);
    }

    @Override
    public Optional<OrderRecycleMark> childRecycle(String code) {
        return recycleMark("WORK_ORDER", "biz_work_order", code);
    }

    @Override
    public Optional<OrderRecycleMark> childRecycle(String code, WorkSession session) {
        return recycleMark("WORK_ORDER", "biz_work_order", code, session);
    }

    @Override
    public List<OrderRecycleMark> listMotherRecycles() {
        return listRecycles("MOTHER", "biz_mother_order");
    }

    @Override
    public List<OrderRecycleMark> listChildRecycles() {
        return listRecycles("WORK_ORDER", "biz_work_order");
    }

    @Override
    public void clearChildRecycle(String code) {
        updateOne("""
                update biz_work_order
                   set recycled_at = null, recycled_by = null, recycle_batch = null, recycle_reason = null
                 where code = ? and recycled_at is not null
                """, statement -> statement.setString(1, code), "子单不在回收站");
    }

    @Override
    public void clearChildRecycle(String code, WorkSession session) {
        try {
            updateOne(session.connection().orElseThrow(), "update biz_work_order set recycled_at = null, recycled_by = null, "
                    + "recycle_batch = null, recycle_reason = null where code = ? and recycled_at is not null",
                    statement -> statement.setString(1, code), "子单不在回收站");
        } catch (SQLException ex) {
            throw new IllegalStateException("恢复子单失败", ex);
        }
    }

    @Override
    public void clearRecycleBatch(String batch) {
        try (Connection connection = dataSource.getConnection()) {
            clearBatch(connection, "biz_mother_order", batch);
            clearBatch(connection, "biz_work_order", batch);
        } catch (SQLException ex) {
            throw new IllegalStateException("恢复回收站失败", ex);
        }
    }

    @Override
    public void clearRecycleBatch(String batch, WorkSession session) {
        try {
            Connection connection = session.connection().orElseThrow();
            clearBatch(connection, "biz_mother_order", batch);
            clearBatch(connection, "biz_work_order", batch);
        } catch (SQLException ex) {
            throw new IllegalStateException("恢复回收站失败", ex);
        }
    }

    @Override
    public void saveOrderAdjustment(OrderAdjustment adjustment) {
        try (Connection connection = dataSource.getConnection()) {
            insertOrderAdjustment(connection, adjustment);
        } catch (SQLException ex) {
            throw new IllegalStateException("保存单据调整记录失败", ex);
        }
    }

    @Override
    public void saveOrderAdjustment(OrderAdjustment adjustment, WorkSession session) {
        try {
            insertOrderAdjustment(session.connection().orElseThrow(), adjustment);
        } catch (SQLException ex) {
            throw new IllegalStateException("保存单据调整记录失败", ex);
        }
    }

    private void insertOrderAdjustment(Connection connection, OrderAdjustment adjustment) throws SQLException {
        String sql = """
                insert into biz_order_adjustment (
                    target_type, target_code, before_text, after_text, reason, operator_name, created_at
                ) values (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, adjustment.targetType());
            statement.setString(2, adjustment.code());
            statement.setString(3, adjustment.beforeText());
            statement.setString(4, adjustment.afterText());
            statement.setString(5, adjustment.reason());
            statement.setString(6, adjustment.operatorName());
            statement.setString(7, adjustment.createdAt());
            statement.executeUpdate();
        }
    }

    @Override
    public List<OrderAdjustment> listOrderAdjustments(String targetType, String code) {
        String sql = """
                select before_text, after_text, reason, operator_name, created_at
                  from biz_order_adjustment
                 where target_type = ? and target_code = ?
                 order by id desc
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, targetType);
            statement.setString(2, code);
            try (ResultSet rs = statement.executeQuery()) {
                List<OrderAdjustment> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new OrderAdjustment(targetType, code, rs.getString("before_text"), rs.getString("after_text"),
                            rs.getString("reason"), rs.getString("operator_name"), rs.getString("created_at")));
                }
                return rows;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("读取单据调整记录失败", ex);
        }
    }

    private void recycle(String table, String code, String operator, String at, String batch, String reason, String missing) {
        updateOne("update " + table + " set recycled_at = ?, recycled_by = ?, recycle_batch = ?, recycle_reason = ? "
                + "where code = ? and recycled_at is null", statement -> {
            statement.setString(1, at);
            statement.setString(2, operator);
            statement.setString(3, batch);
            statement.setString(4, reason);
            statement.setString(5, code);
        }, missing);
    }

    private void recycle(String table, String code, String operator, String at, String batch, String reason,
                         String missing, WorkSession session) {
        try {
            updateOne(session.connection().orElseThrow(), "update " + table
                    + " set recycled_at = ?, recycled_by = ?, recycle_batch = ?, recycle_reason = ? "
                    + "where code = ? and recycled_at is null", statement -> {
                statement.setString(1, at);
                statement.setString(2, operator);
                statement.setString(3, batch);
                statement.setString(4, reason);
                statement.setString(5, code);
            }, missing);
        } catch (SQLException ex) {
            throw new IllegalStateException("回收单据失败", ex);
        }
    }

    private Optional<OrderRecycleMark> recycleMark(String type, String table, String code, WorkSession session) {
        String sql = "select recycled_at, recycled_by, recycle_batch, recycle_reason from " + table
                + " where code = ? for update";
        try (PreparedStatement statement = requireConnection(session).prepareStatement(sql)) {
            statement.setString(1, code);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next() || rs.getString("recycled_at") == null) {
                    return Optional.empty();
                }
                return Optional.of(new OrderRecycleMark(type, code, rs.getString("recycled_at"), rs.getString("recycled_by"),
                        rs.getString("recycle_batch"), rs.getString("recycle_reason")));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("读取回收站失败", ex);
        }
    }

    private Optional<OrderRecycleMark> recycleMark(String type, String table, String code) {
        String sql = "select recycled_at, recycled_by, recycle_batch, recycle_reason from " + table
                + " where code = ? and recycled_at is not null";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new OrderRecycleMark(type, code, rs.getString("recycled_at"), rs.getString("recycled_by"),
                        rs.getString("recycle_batch"), rs.getString("recycle_reason")));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("读取回收站失败", ex);
        }
    }

    private List<OrderRecycleMark> listRecycles(String type, String table) {
        String sql = "select code, recycled_at, recycled_by, recycle_batch, recycle_reason from " + table
                + " where recycled_at is not null order by recycled_at desc";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<OrderRecycleMark> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(new OrderRecycleMark(type, rs.getString("code"), rs.getString("recycled_at"),
                        rs.getString("recycled_by"), rs.getString("recycle_batch"), rs.getString("recycle_reason")));
            }
            return rows;
        } catch (SQLException ex) {
            throw new IllegalStateException("列出回收站失败", ex);
        }
    }

    private void clearBatch(Connection connection, String table, String batch) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("update " + table
                + " set recycled_at = null, recycled_by = null, recycle_batch = null, recycle_reason = null "
                + "where recycle_batch = ?")) {
            statement.setString(1, batch);
            statement.executeUpdate();
        }
    }

    private void updateOne(String sql, SqlBinder binder, String missing) {
        try (Connection connection = dataSource.getConnection()) {
            updateOne(connection, sql, binder, missing);
        } catch (BusinessException ex) {
            throw ex;
        } catch (SQLException ex) {
            throw new IllegalStateException(missing, ex);
        }
    }

    private void updateOne(Connection connection, String sql, SqlBinder binder, String missing) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            if (statement.executeUpdate() != 1) {
                throw new BusinessException(missing);
            }
        }
    }

    @FunctionalInterface
    private interface SqlCorrection {
        void apply(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    private volatile Boolean processBatchesSupported;

    @Override public boolean supportsProcessBatches() {
        if (processBatchesSupported != null) return processBatchesSupported;
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("select batch_code from biz_process_batch_handoff where 1 = 0")) {
            statement.executeQuery().close(); processBatchesSupported = true; return true;
        } catch (SQLException ex) {
            if ("42S02".equals(ex.getSQLState()) || "42102".equals(ex.getSQLState())) { processBatchesSupported = false; return false; }
            throw new IllegalStateException("无法核查分批交工结构", ex);
        }
    }

    @Override public void lockProcessBatchOrder(String orderCode, WorkSession session) {
        Connection connection = requireConnection(session);
        try (PreparedStatement insert = connection.prepareStatement("insert into biz_process_batch_lock (order_code) values (?) on duplicate key update order_code = values(order_code)")) {
            insert.setString(1, orderCode); insert.executeUpdate();
            try (PreparedStatement lock = connection.prepareStatement("select order_code from biz_process_batch_lock where order_code = ? for update")) {
                lock.setString(1, orderCode); lock.executeQuery().close();
            }
        } catch (SQLException ex) { throw new IllegalStateException("无法锁定分批交工工单", ex); }
    }

    @Override public List<com.zhubao.erp.business.domain.ProcessBatchHandoff> listProcessBatches(String orderCode, WorkSession session) {
        if (!supportsProcessBatches()) return List.of();
        if (session != null) return readProcessBatches(requireConnection(session), orderCode, true);
        try (Connection connection = dataSource.getConnection()) { return readProcessBatches(connection, orderCode, false); }
        catch (SQLException ex) { throw new IllegalStateException("无法读取分批交工", ex); }
    }

    @Override public List<com.zhubao.erp.business.domain.ProcessBatchHandoff> listAllProcessBatches() {
        if (!supportsProcessBatches()) return List.of();
        try (Connection connection = dataSource.getConnection()) { return readProcessBatches(connection, null, false); }
        catch (SQLException ex) { throw new IllegalStateException("无法读取分批交工", ex); }
    }

    private List<com.zhubao.erp.business.domain.ProcessBatchHandoff> readProcessBatches(Connection connection, String orderCode, boolean lock) {
        String sql = "select batch_code, order_code, process_name, quantity, final_weight, operator, created_at from biz_process_batch_handoff"
                + (orderCode == null ? "" : " where order_code = ?") + " order by created_at, batch_code" + (lock ? " for update" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (orderCode != null) statement.setString(1, orderCode);
            try (ResultSet rs = statement.executeQuery()) {
                List<com.zhubao.erp.business.domain.ProcessBatchHandoff> rows = new ArrayList<>();
                while (rs.next()) rows.add(new com.zhubao.erp.business.domain.ProcessBatchHandoff(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getString(6), rs.getString(7)));
                return List.copyOf(rows);
            }
        } catch (SQLException ex) { throw new IllegalStateException("无法读取分批交工明细", ex); }
    }

    @Override public void saveProcessBatch(com.zhubao.erp.business.domain.ProcessBatchHandoff batch, WorkSession session) {
        try (PreparedStatement statement = requireConnection(session).prepareStatement("insert into biz_process_batch_handoff (batch_code, order_code, process_name, quantity, final_weight, operator, created_at) values (?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, batch.batchCode()); statement.setString(2, batch.orderCode()); statement.setString(3, batch.processName());
            statement.setBigDecimal(4, batch.quantity()); statement.setBigDecimal(5, batch.finalWeight()); statement.setString(6, batch.operator()); statement.setString(7, batch.createdAt());
            statement.executeUpdate();
        } catch (SQLException ex) { throw new IllegalStateException("无法保存分批交工", ex); }
    }

}
