package com.zhubao.erp.sampling.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.sampling.domain.SamplingProductionLink;
import com.zhubao.erp.sampling.domain.SamplingProductionLinkRepository;
import com.zhubao.erp.sampling.domain.SamplingVersionKind;
import com.zhubao.erp.work.app.WorkSessionBindings;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.sampling-production-link.repository", havingValue = "jdbc")
public class JdbcSamplingProductionLinkRepository implements SamplingProductionLinkRepository {

    private final DataSource dataSource;

    public JdbcSamplingProductionLinkRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void insert(SamplingProductionLink link, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into smp_sampling_production_link(
                    mother_code, sampling_code, version_kind, version_no, snapshot_text, linked_on)
                values (?,?,?,?,?,?)
                """)) {
            statement.setString(1, link.motherCode());
            statement.setString(2, link.samplingCode());
            statement.setString(3, link.versionKind().name());
            statement.setInt(4, link.versionNo());
            statement.setString(5, link.snapshotText());
            statement.setString(6, link.linkedOn());
            statement.executeUpdate();
        } catch (SQLException ex) {
            if (duplicate(ex)) {
                throw new BusinessException("这张母单已经引用样品版本");
            }
            throw new IllegalStateException("无法保存样品引用", ex);
        }
    }

    @Override
    public void delete(String motherCode, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement(
                "delete from smp_sampling_production_link where mother_code=?")) {
            statement.setString(1, motherCode);
            if (statement.executeUpdate() == 0) {
                throw new BusinessException("这张母单没有样品引用");
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法取消样品引用", ex);
        }
    }

    @Override
    public Optional<SamplingProductionLink> find(String motherCode) {
        if (motherCode == null || motherCode.isBlank()) {
            return Optional.empty();
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectSql() + " where mother_code=?")) {
            statement.setString(1, motherCode.trim());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取样品引用", ex);
        }
    }

    @Override
    public Optional<SamplingProductionLink> lock(String motherCode, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement(selectSql() + " where mother_code=? for update")) {
            statement.setString(1, motherCode);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法锁定样品引用", ex);
        }
    }

    private static SamplingProductionLink map(ResultSet rows) throws SQLException {
        return new SamplingProductionLink(
                rows.getString("mother_code"),
                rows.getString("sampling_code"),
                SamplingVersionKind.valueOf(rows.getString("version_kind")),
                rows.getInt("version_no"),
                rows.getString("snapshot_text"),
                rows.getString("linked_on")
        );
    }

    private static String selectSql() {
        return """
                select mother_code, sampling_code, version_kind, version_no, snapshot_text, linked_on
                  from smp_sampling_production_link
                """;
    }

    private static boolean duplicate(SQLException ex) {
        String state = ex.getSQLState();
        return ex.getErrorCode() == 1062 || ex.getErrorCode() == 23505
                || "23000".equals(state) || "23505".equals(state)
                || (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("unique"));
    }
}
