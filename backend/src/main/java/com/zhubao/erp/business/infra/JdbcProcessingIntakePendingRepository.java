package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.*;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import javax.sql.DataSource;
import java.sql.*;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.business-document.repository", havingValue = "jdbc")
public class JdbcProcessingIntakePendingRepository implements ProcessingIntakePendingRepository {
    private final DataSource dataSource;
    public JdbcProcessingIntakePendingRepository(DataSource dataSource) { this.dataSource = dataSource; }
    public Optional<ProcessingIntakePending> find(String code, WorkSession session) {
        if (session != null) return load(session.connection().orElseThrow(), code, true);
        try (var connection = dataSource.getConnection()) { return load(connection, code, false); }
        catch (SQLException ex) { throw new IllegalStateException("加工待核读取失败", ex); }
    }
    private Optional<ProcessingIntakePending> load(Connection connection, String code, boolean lock) {
        try (var statement = connection.prepareStatement("select * from biz_processing_intake_pending where code=?" + (lock ? " for update" : ""))) {
            statement.setString(1, code); try (var result = statement.executeQuery()) { return result.next() ? Optional.of(map(result)) : Optional.empty(); }
        } catch (SQLException ex) { throw new IllegalStateException("加工待核读取失败", ex); }
    }
    private ProcessingIntakePending map(ResultSet result) throws SQLException { return new ProcessingIntakePending(result.getString("code"), result.getInt("version"), result.getString("account_name"), result.getString("status"), result.getString("mother_order_code"), result.getString("draft_json"), result.getString("created_at"), result.getString("updated_at")); }
    public List<ProcessingIntakePending> list(String account) {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement("select * from biz_processing_intake_pending where account_name=? and status='PENDING' order by updated_at desc")) {
            statement.setString(1, account); var rows = new java.util.ArrayList<ProcessingIntakePending>();
            try (var result = statement.executeQuery()) { while (result.next()) rows.add(map(result)); } return List.copyOf(rows);
        } catch (SQLException ex) { throw new IllegalStateException("加工待核列表读取失败", ex); }
    }
    public void save(ProcessingIntakePending row, WorkSession session) {
        var connection = session.connection().orElseThrow();
        try {
            boolean exists = find(row.code(), session).isPresent();
            String sql = exists ? "update biz_processing_intake_pending set version=?, account_name=?, status=?, mother_order_code=?, draft_json=?, created_at=?, updated_at=? where code=?"
                    : "insert into biz_processing_intake_pending (version,account_name,status,mother_order_code,draft_json,created_at,updated_at,code) values (?,?,?,?,?,?,?,?)";
            try (var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, row.version()); statement.setString(2, row.account()); statement.setString(3, row.status()); statement.setString(4, row.motherOrderCode()); statement.setString(5, row.draftJson()); statement.setString(6, row.createdAt()); statement.setString(7, row.updatedAt()); statement.setString(8, row.code()); statement.executeUpdate();
            }
        } catch (SQLException ex) { throw new IllegalStateException("加工待核保存失败", ex); }
    }
}
