package com.zhubao.erp.machine.infra;

import com.zhubao.erp.machine.domain.MachineOutputRecord;
import com.zhubao.erp.machine.domain.MachineOutputRepository;
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
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.machine-output.repository", havingValue = "jdbc")
public class JdbcMachineOutputRepository implements MachineOutputRepository {

    private final DataSource dataSource;

    public JdbcMachineOutputRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public MachineOutputRecord save(MachineOutputRecord record) {
        String sql = """
                insert into biz_machine_output_record (
                    code, work_date, customer_id, customer_name_snapshot, product_category,
                    machine_id, machine_no_snapshot, work_order_code, work_process_id, work_report_code,
                    output_quantity, quantity_unit, operator_employee_id, operator_signature_name,
                    signature_media_id, source_register_type, source_line_no, machine_line_no,
                    source_sequence_no, import_batch_no, ledger_code, output_status, remark,
                    created_by, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setRecord(statement, record);
            statement.executeUpdate();
            return record;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save machine output record: " + record.code(), ex);
        }
    }

    @Override
    public Optional<MachineOutputRecord> findByCode(String code) {
        String sql = selectSql() + " where code = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRecord(rs));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load machine output record: " + code, ex);
        }
    }

    @Override
    public List<MachineOutputRecord> findRecent(int limit) {
        String sql = selectSql() + " order by id desc limit ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            return list(statement);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load recent machine output records", ex);
        }
    }

    @Override
    public List<MachineOutputRecord> findByImportBatchNo(String importBatchNo) {
        String sql = selectSql() + " where import_batch_no = ? order by source_line_no, id";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, importBatchNo);
            return list(statement);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load machine output records for import batch: " + importBatchNo, ex);
        }
    }

    private List<MachineOutputRecord> list(PreparedStatement statement) throws SQLException {
        try (ResultSet rs = statement.executeQuery()) {
            List<MachineOutputRecord> records = new ArrayList<>();
            while (rs.next()) {
                records.add(mapRecord(rs));
            }
            return List.copyOf(records);
        }
    }

    private String selectSql() {
        return """
                select code, work_date, customer_id, customer_name_snapshot, product_category,
                       machine_id, machine_no_snapshot, work_order_code, work_process_id, work_report_code,
                       output_quantity, quantity_unit, operator_employee_id, operator_signature_name,
                       signature_media_id, source_register_type, source_line_no, machine_line_no,
                       source_sequence_no, import_batch_no, ledger_code, output_status, remark,
                       created_by, created_at
                  from biz_machine_output_record
                """;
    }

    private void setRecord(PreparedStatement statement, MachineOutputRecord record) throws SQLException {
        statement.setString(1, record.code());
        statement.setString(2, record.workDate());
        setLong(statement, 3, record.customerId());
        statement.setString(4, record.customerNameSnapshot());
        statement.setString(5, record.productCategory());
        setLong(statement, 6, record.machineId());
        statement.setString(7, record.machineNoSnapshot());
        statement.setString(8, record.workOrderCode());
        setLong(statement, 9, record.workProcessId());
        statement.setString(10, record.workReportCode());
        statement.setBigDecimal(11, record.outputQuantity());
        statement.setString(12, record.quantityUnit());
        setLong(statement, 13, record.operatorEmployeeId());
        statement.setString(14, record.operatorSignatureName());
        setLong(statement, 15, record.signatureMediaId());
        statement.setString(16, record.sourceRegisterType());
        setInteger(statement, 17, record.sourceLineNo());
        statement.setString(18, record.machineLineNo());
        statement.setString(19, record.sourceSequenceNo());
        statement.setString(20, record.importBatchNo());
        statement.setString(21, record.ledgerCode());
        statement.setString(22, record.status());
        statement.setString(23, record.remark());
        statement.setString(24, record.createdBy());
        statement.setString(25, record.createdAt());
    }

    private MachineOutputRecord mapRecord(ResultSet rs) throws SQLException {
        return new MachineOutputRecord(
                rs.getString("code"),
                rs.getString("work_date"),
                nullableLong(rs, "customer_id"),
                rs.getString("customer_name_snapshot"),
                rs.getString("product_category"),
                nullableLong(rs, "machine_id"),
                rs.getString("machine_no_snapshot"),
                rs.getString("work_order_code"),
                nullableLong(rs, "work_process_id"),
                rs.getString("work_report_code"),
                rs.getBigDecimal("output_quantity"),
                rs.getString("quantity_unit"),
                nullableLong(rs, "operator_employee_id"),
                rs.getString("operator_signature_name"),
                nullableLong(rs, "signature_media_id"),
                rs.getString("source_register_type"),
                nullableInteger(rs, "source_line_no"),
                rs.getString("machine_line_no"),
                rs.getString("source_sequence_no"),
                rs.getString("import_batch_no"),
                rs.getString("ledger_code"),
                rs.getString("output_status"),
                rs.getString("remark"),
                rs.getString("created_by"),
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
