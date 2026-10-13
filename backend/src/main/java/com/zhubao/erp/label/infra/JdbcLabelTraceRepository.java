package com.zhubao.erp.label.infra;

import com.zhubao.erp.label.domain.LabelTrace;
import com.zhubao.erp.label.domain.LabelTraceRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "zhubao.label-template.repository", havingValue = "jdbc")
public class JdbcLabelTraceRepository implements LabelTraceRepository {

    private final DataSource dataSource;

    public JdbcLabelTraceRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(LabelTrace trace) {
        String sql = """
                insert into biz_label_trace (
                    id, mother_order_code, business_code, event_type, version_no, instance_no,
                    print_job_id, reason_text, operator_name, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, trace.id());
            statement.setString(2, trace.motherOrderCode());
            statement.setString(3, trace.businessCode());
            statement.setString(4, trace.eventType());
            statement.setString(5, trace.versionNo());
            statement.setString(6, trace.instanceNo());
            statement.setString(7, trace.printJobId());
            statement.setString(8, trace.reason());
            statement.setString(9, trace.operatorName());
            statement.setString(10, trace.createdAt());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存标签追溯", ex);
        }
    }

    @Override
    public List<LabelTrace> listByMother(String motherOrderCode) {
        String sql = "select * from biz_label_trace where mother_order_code = ? order by created_at";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, motherOrderCode);
            try (ResultSet rs = statement.executeQuery()) {
                List<LabelTrace> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new LabelTrace(
                            rs.getString("id"),
                            rs.getString("mother_order_code"),
                            rs.getString("business_code"),
                            rs.getString("event_type"),
                            rs.getString("version_no"),
                            rs.getString("instance_no"),
                            rs.getString("print_job_id"),
                            rs.getString("reason_text"),
                            rs.getString("operator_name"),
                            rs.getString("created_at")
                    ));
                }
                return rows;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取标签追溯", ex);
        }
    }
}