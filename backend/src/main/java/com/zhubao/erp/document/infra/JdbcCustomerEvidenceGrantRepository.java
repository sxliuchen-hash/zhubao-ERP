package com.zhubao.erp.document.infra;

import com.zhubao.erp.document.domain.CustomerEvidenceGrant;
import com.zhubao.erp.document.domain.CustomerEvidenceGrantRepository;
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
public class JdbcCustomerEvidenceGrantRepository implements CustomerEvidenceGrantRepository {

    private final DataSource dataSource;

    public JdbcCustomerEvidenceGrantRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void revoke(String grantId) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "delete from doc_customer_evidence_grant where grant_id=?")) {
            statement.setString(1, grantId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("撤销客户证据授权失败", ex);
        }
    }

    @Override
    public void save(CustomerEvidenceGrant grant) {
        String sql = """
                insert into doc_customer_evidence_grant (grant_id, customer_id, attachment_code, kind)
                values (?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, grant.grantId());
            statement.setLong(2, grant.customerId());
            statement.setString(3, grant.attachmentCode());
            statement.setString(4, grant.kind());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("保存客户证据获准失败", ex);
        }
    }

    @Override
    public Optional<CustomerEvidenceGrant> findById(String grantId) {
        return one("select grant_id, customer_id, attachment_code, kind from doc_customer_evidence_grant where grant_id = ?",
                statement -> statement.setString(1, grantId == null ? "" : grantId.trim()));
    }

    @Override
    public Optional<CustomerEvidenceGrant> find(long customerId, String attachmentCode) {
        return one("""
                select grant_id, customer_id, attachment_code, kind
                  from doc_customer_evidence_grant
                 where customer_id = ? and attachment_code = ?
                """, statement -> {
            statement.setLong(1, customerId);
            statement.setString(2, attachmentCode == null ? "" : attachmentCode.trim());
        });
    }

    @Override
    public List<CustomerEvidenceGrant> listByCustomer(long customerId) {
        String sql = """
                select grant_id, customer_id, attachment_code, kind
                  from doc_customer_evidence_grant
                 where customer_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, customerId);
            try (ResultSet result = statement.executeQuery()) {
                List<CustomerEvidenceGrant> rows = new ArrayList<>();
                while (result.next()) {
                    rows.add(read(result));
                }
                return List.copyOf(rows);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("读取客户证据获准失败", ex);
        }
    }

    private Optional<CustomerEvidenceGrant> one(String sql, Binder binder) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("读取客户证据获准失败", ex);
        }
    }

    private static CustomerEvidenceGrant read(ResultSet result) throws SQLException {
        return new CustomerEvidenceGrant(result.getString("grant_id"), result.getLong("customer_id"),
                result.getString("attachment_code"), result.getString("kind"));
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
