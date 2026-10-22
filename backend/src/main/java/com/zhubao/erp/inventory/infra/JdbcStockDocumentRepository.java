package com.zhubao.erp.inventory.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockDocument;
import com.zhubao.erp.inventory.domain.StockDocumentLine;
import com.zhubao.erp.inventory.domain.StockDocumentRepository;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.inventory.repository", havingValue = "jdbc")
public class JdbcStockDocumentRepository implements StockDocumentRepository {

    private final DataSource dataSource;

    public JdbcStockDocumentRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(StockDocument document, WorkSession session) {
        Connection connection = connection(session);
        try {
            upsert(connection, document);
        } catch (SQLException ex) {
            throw new IllegalStateException("库存单据写入失败", ex);
        }
    }

    @Override
    public Optional<StockDocument> find(String documentCode) {
        try (Connection connection = dataSource.getConnection()) {
            return load(connection, documentCode, false);
        } catch (SQLException ex) {
            throw new IllegalStateException("库存单据读取失败", ex);
        }
    }

    @Override
    public Optional<StockDocument> lock(String documentCode, WorkSession session) {
        try {
            return load(connection(session), documentCode, true);
        } catch (SQLException ex) {
            throw new IllegalStateException("库存单据读取失败", ex);
        }
    }

    @Override
    public List<StockDocument> list() {
        String sql = """
                select document_code, document_kind, status, owner_type, customer_id, customer_name, warehouse_code,
                       source_note, handover_person, purpose, recipient, target_order_code, source_document_code,
                       operator_name, request_id, biz_date, created_at, evidence_code, direction
                  from inv_stock_document
                 order by created_at, document_code
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<StockDocument> headers = new ArrayList<>();
            while (rs.next()) {
                headers.add(header(rs, List.of()));
            }
            if (headers.isEmpty()) {
                return List.of();
            }
            Map<String, List<StockDocumentLine>> lines = loadLines(connection, headers.stream().map(StockDocument::documentCode).toList());
            return headers.stream().map(header -> withLines(header, lines.getOrDefault(header.documentCode(), List.of()))).toList();
        } catch (SQLException ex) {
            throw new IllegalStateException("库存单据读取失败", ex);
        }
    }

    private void upsert(Connection connection, StockDocument document) throws SQLException {
        String update = """
                update inv_stock_document
                   set document_kind = ?, status = ?, owner_type = ?, customer_id = ?, customer_name = ?,
                       warehouse_code = ?, source_note = ?, handover_person = ?, purpose = ?, recipient = ?,
                       target_order_code = ?, source_document_code = ?, operator_name = ?, request_id = ?,
                       biz_date = ?, created_at = ?, evidence_code = ?, direction = ?
                 where document_code = ?
                """;
        int updated;
        try (PreparedStatement statement = connection.prepareStatement(update)) {
            bindHeader(statement, document);
            statement.setString(19, document.documentCode());
            updated = statement.executeUpdate();
        }
        if (updated == 0) {
            String insert = """
                    insert into inv_stock_document (
                        document_code, document_kind, status, owner_type, customer_id, customer_name, warehouse_code,
                        source_note, handover_person, purpose, recipient, target_order_code, source_document_code,
                        operator_name, request_id, biz_date, created_at, evidence_code, direction
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            try (PreparedStatement statement = connection.prepareStatement(insert)) {
                statement.setString(1, document.documentCode());
                bindHeader(statement, document, 2);
                statement.executeUpdate();
            }
        }
        try (PreparedStatement delete = connection.prepareStatement("delete from inv_stock_document_line where document_code = ?")) {
            delete.setString(1, document.documentCode());
            delete.executeUpdate();
        }
        String lineSql = """
                insert into inv_stock_document_line (
                    document_code, line_no, material_code, quantity_unit, quantity, weight, batch_code, external_ref,
                    source_line_no, counted, declared_quantity, declared_weight, ledger_code
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(lineSql)) {
            for (StockDocumentLine line : document.lines()) {
                statement.setString(1, document.documentCode());
                statement.setInt(2, line.lineNo());
                statement.setString(3, line.materialCode());
                statement.setString(4, line.quantityUnit());
                statement.setBigDecimal(5, line.quantity());
                setDecimal(statement, 6, line.weight());
                statement.setString(7, line.batchCode());
                statement.setString(8, line.externalRef());
                if (line.sourceLineNo() == null) {
                    statement.setNull(9, Types.INTEGER);
                } else {
                    statement.setInt(9, line.sourceLineNo());
                }
                statement.setBoolean(10, line.counted());
                statement.setBigDecimal(11, line.declaredQuantity());
                setDecimal(statement, 12, line.declaredWeight());
                statement.setString(13, line.ledgerCode());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    @Override
    public List<StockDocument> related(String sourceDocumentCode, WorkSession session) {
        Connection connection = connection(session);
        List<String> codes = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "select document_code from inv_stock_document where source_document_code = ? order by document_code for update")) {
            statement.setString(1, sourceDocumentCode);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) codes.add(rs.getString(1));
            }
            List<StockDocument> result = new ArrayList<>();
            for (String code : codes) load(connection, code, true).ifPresent(result::add);
            return result;
        } catch (SQLException ex) {
            throw new IllegalStateException("库存原单后续记录读取失败", ex);
        }
    }

    private Optional<StockDocument> load(Connection connection, String documentCode, boolean lock) throws SQLException {
        String sql = """
                select document_code, document_kind, status, owner_type, customer_id, customer_name, warehouse_code,
                       source_note, handover_person, purpose, recipient, target_order_code, source_document_code,
                       operator_name, request_id, biz_date, created_at, evidence_code, direction
                  from inv_stock_document
                 where document_code = ?
                """ + (lock ? " for update" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, documentCode);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                List<StockDocumentLine> lines = loadLines(connection, List.of(documentCode), lock).getOrDefault(documentCode, List.of());
                return Optional.of(header(rs, lines));
            }
        }
    }

    private Map<String, List<StockDocumentLine>> loadLines(Connection connection, List<String> codes) throws SQLException {
        return loadLines(connection, codes, false);
    }

    private Map<String, List<StockDocumentLine>> loadLines(Connection connection, List<String> codes, boolean lock) throws SQLException {
        if (codes.isEmpty()) {
            return Map.of();
        }
        StringBuilder sql = new StringBuilder("""
                select document_code, line_no, material_code, quantity_unit, quantity, weight, batch_code, external_ref,
                       source_line_no, counted, declared_quantity, declared_weight, ledger_code
                  from inv_stock_document_line
                 where document_code in (
                """);
        for (int index = 0; index < codes.size(); index++) {
            if (index > 0) {
                sql.append(',');
            }
            sql.append('?');
        }
        sql.append(") order by document_code, line_no");
        if (lock) sql.append(" for update");
        Map<String, List<StockDocumentLine>> grouped = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int index = 0; index < codes.size(); index++) {
                statement.setString(index + 1, codes.get(index));
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Integer sourceLine = rs.getInt("source_line_no");
                    if (rs.wasNull()) {
                        sourceLine = null;
                    }
                    StockDocumentLine line = new StockDocumentLine(
                            rs.getInt("line_no"),
                            rs.getString("material_code"),
                            rs.getString("quantity_unit"),
                            rs.getBigDecimal("quantity"),
                            decimal(rs, "weight"),
                            rs.getString("batch_code"),
                            rs.getString("external_ref"),
                            sourceLine,
                            rs.getBoolean("counted"),
                            rs.getBigDecimal("declared_quantity"),
                            decimal(rs, "declared_weight"),
                            rs.getString("ledger_code")
                    );
                    grouped.computeIfAbsent(rs.getString("document_code"), key -> new ArrayList<>()).add(line);
                }
            }
        }
        return grouped;
    }

    private void bindHeader(PreparedStatement statement, StockDocument document) throws SQLException {
        bindHeader(statement, document, 1);
    }

    private void bindHeader(PreparedStatement statement, StockDocument document, int start) throws SQLException {
        statement.setString(start, document.kind());
        statement.setString(start + 1, document.status());
        statement.setString(start + 2, document.ownerType());
        if (document.customerId() == null) {
            statement.setNull(start + 3, Types.BIGINT);
        } else {
            statement.setLong(start + 3, document.customerId());
        }
        statement.setString(start + 4, document.customerName());
        statement.setString(start + 5, document.warehouseCode());
        statement.setString(start + 6, document.sourceNote());
        statement.setString(start + 7, document.handoverPerson());
        statement.setString(start + 8, document.purpose());
        statement.setString(start + 9, document.recipient());
        statement.setString(start + 10, document.targetOrderCode());
        statement.setString(start + 11, document.sourceDocumentCode());
        statement.setString(start + 12, document.operatorName());
        statement.setString(start + 13, document.requestId());
        statement.setString(start + 14, document.bizDate());
        statement.setString(start + 15, document.createdAt());
        statement.setString(start + 16, document.evidenceCode());
        statement.setString(start + 17, document.direction());
    }

    private StockDocument header(ResultSet rs, List<StockDocumentLine> lines) throws SQLException {
        long customerId = rs.getLong("customer_id");
        Long customer = rs.wasNull() ? null : customerId;
        return new StockDocument(
                rs.getString("document_code"),
                rs.getString("document_kind"),
                rs.getString("status"),
                rs.getString("owner_type"),
                customer,
                rs.getString("customer_name"),
                rs.getString("warehouse_code"),
                rs.getString("source_note"),
                rs.getString("handover_person"),
                rs.getString("purpose"),
                rs.getString("recipient"),
                rs.getString("target_order_code"),
                rs.getString("source_document_code"),
                rs.getString("operator_name"),
                rs.getString("request_id"),
                rs.getString("biz_date"),
                rs.getString("created_at"),
                rs.getString("evidence_code"),
                rs.getString("direction"),
                lines
        );
    }

    private StockDocument withLines(StockDocument header, List<StockDocumentLine> lines) {
        return new StockDocument(header.documentCode(), header.kind(), header.status(), header.ownerType(),
                header.customerId(), header.customerName(), header.warehouseCode(), header.sourceNote(),
                header.handoverPerson(), header.purpose(), header.recipient(), header.targetOrderCode(),
                header.sourceDocumentCode(), header.operatorName(), header.requestId(), header.bizDate(),
                header.createdAt(), header.evidenceCode(), header.direction(), lines);
    }

    private Connection connection(WorkSession session) {
        if (session == null || session.connection().isEmpty()) {
            throw new BusinessException("库存单据必须使用工作单元连接");
        }
        return session.connection().get();
    }

    private static void setDecimal(PreparedStatement statement, int index, BigDecimal value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DECIMAL);
        } else {
            statement.setBigDecimal(index, value);
        }
    }

    private static BigDecimal decimal(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return rs.wasNull() ? null : value;
    }
}
