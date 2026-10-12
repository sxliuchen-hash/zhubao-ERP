package com.zhubao.erp.document.infra;

import com.zhubao.erp.document.domain.BusinessEvidenceLink;
import com.zhubao.erp.document.domain.BusinessEvidenceLinkRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.documents.repository", havingValue = "jdbc")
public class JdbcBusinessEvidenceLinkRepository implements BusinessEvidenceLinkRepository {

    private final DataSource dataSource;

    public JdbcBusinessEvidenceLinkRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<BusinessEvidenceLink> findAll() {
        String sql = """
                select link_code, attachment_code, target_type, target_code, stage, scene,
                       operation_type, operation_code, process_name, clause_no, operator_name,
                       linked_at, link_key_hash
                  from doc_business_evidence_link
                 order by id
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<BusinessEvidenceLink> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(map(rs));
            }
            return List.copyOf(rows);
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取业务证据引用", ex);
        }
    }

    @Override
    public Optional<BusinessEvidenceLink> findByKeyHash(String linkKeyHash) {
        String sql = """
                select link_code, attachment_code, target_type, target_code, stage, scene,
                       operation_type, operation_code, process_name, clause_no, operator_name,
                       linked_at, link_key_hash
                  from doc_business_evidence_link
                 where link_key_hash = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, linkKeyHash);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法按引用键读取业务证据", ex);
        }
    }

    @Override
    public void insert(BusinessEvidenceLink link) {
        String sql = """
                insert into doc_business_evidence_link (
                    link_code, attachment_code, target_type, target_code, stage, scene,
                    operation_type, operation_code, process_name, clause_no, operator_name,
                    linked_at, link_key_hash
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, link.linkCode());
            statement.setString(2, link.attachmentCode());
            statement.setString(3, link.targetType());
            statement.setString(4, link.targetCode());
            statement.setString(5, link.stage());
            statement.setString(6, link.scene());
            statement.setString(7, link.operationType());
            statement.setString(8, link.operationCode());
            statement.setString(9, link.processName());
            statement.setString(10, link.clauseNo());
            statement.setString(11, link.operatorName());
            statement.setString(12, link.linkedAt());
            statement.setString(13, link.linkKeyHash());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存业务证据引用：" + link.linkCode(), ex);
        }
    }

    private BusinessEvidenceLink map(ResultSet rs) throws SQLException {
        return new BusinessEvidenceLink(
                rs.getString("link_code"),
                rs.getString("attachment_code"),
                rs.getString("target_type"),
                rs.getString("target_code"),
                rs.getString("stage"),
                rs.getString("scene"),
                rs.getString("operation_type"),
                rs.getString("operation_code"),
                rs.getString("process_name"),
                rs.getString("clause_no"),
                rs.getString("operator_name"),
                rs.getString("linked_at"),
                rs.getString("link_key_hash")
        );
    }
}
