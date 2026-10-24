package com.zhubao.erp.label.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhubao.erp.label.domain.*;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.business-document.repository", havingValue = "jdbc")
public class JdbcLabelStorageEditionRepository implements LabelStorageEditionRepository {
    private final DataSource dataSource;
    private final ObjectMapper mapper = new ObjectMapper();
    public JdbcLabelStorageEditionRepository(DataSource dataSource) { this.dataSource = dataSource; }
    public Optional<LabelStorageEdition> latest(String code, WorkSession session) {
        if (session != null) return load(session.connection().orElseThrow(() -> new IllegalStateException("需要 JDBC 工作会话")), code);
        try (var connection = dataSource.getConnection()) { return load(connection, code); }
        catch (Exception ex) { throw new IllegalStateException("标签保存要求版本读取失败", ex); }
    }
    private Optional<LabelStorageEdition> load(Connection connection, String code) {
        try (var statement = connection.prepareStatement("select content_snapshot from label_storage_edition where business_code=? order by edition desc limit 1")) {
            statement.setString(1, code);
            try (var result = statement.executeQuery()) { return result.next() ? Optional.of(mapper.readValue(result.getString(1), LabelStorageEdition.class)) : Optional.empty(); }
        } catch (Exception ex) { throw new IllegalStateException("标签保存要求版本读取失败", ex); }
    }
    public java.util.List<LabelStorageEdition> history(String code) {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement("select content_snapshot from label_storage_edition where business_code=? order by edition")) {
            statement.setString(1, code); var editions = new java.util.ArrayList<LabelStorageEdition>();
            try (var result = statement.executeQuery()) { while (result.next()) editions.add(mapper.readValue(result.getString(1), LabelStorageEdition.class)); }
            return java.util.List.copyOf(editions);
        } catch (Exception ex) { throw new IllegalStateException("换贴历史读取失败", ex); }
    }
    public void save(LabelStorageEdition edition, WorkSession session) {
        try (var statement = session.connection().orElseThrow(() -> new IllegalStateException("需要 JDBC 工作会话")).prepareStatement("insert into label_storage_edition (business_code, edition, request_id, content_snapshot) values (?,?,?,?)")) {
            statement.setString(1, edition.businessCode()); statement.setInt(2, edition.edition()); statement.setString(3, edition.requestId()); statement.setString(4, mapper.writeValueAsString(edition)); statement.executeUpdate();
        } catch (Exception ex) { throw new IllegalStateException("标签保存要求版本保存失败", ex); }
    }
}
