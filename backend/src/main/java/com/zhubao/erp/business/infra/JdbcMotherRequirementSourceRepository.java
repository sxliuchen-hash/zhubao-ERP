package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.MotherRequirementSource;
import com.zhubao.erp.business.domain.MotherRequirementSourceRepository;
import com.zhubao.erp.common.error.BusinessException;
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
@ConditionalOnProperty(name = "zhubao.business-document.repository", havingValue = "jdbc")
public class JdbcMotherRequirementSourceRepository implements MotherRequirementSourceRepository {

    private final DataSource dataSource;

    public JdbcMotherRequirementSourceRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(MotherRequirementSource source, WorkSession session) {
        if (session == null || session.connection().isEmpty()) {
            throw new BusinessException("加工要求来源必须使用工作单元连接");
        }
        String sql = """
                insert into biz_mother_requirement_source
                    (mother_order_code, template_code, template_version, saved_requirement)
                values (?, ?, ?, ?)
                """;
        try (PreparedStatement statement = session.connection().get().prepareStatement(sql)) {
            statement.setString(1, source.motherOrderCode());
            statement.setString(2, source.templateCode());
            statement.setInt(3, source.templateVersion());
            statement.setString(4, source.savedRequirement());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("加工要求来源写入失败", ex);
        }
    }

    @Override
    public Optional<MotherRequirementSource> find(String motherOrderCode) {
        String sql = """
                select mother_order_code, template_code, template_version, saved_requirement
                  from biz_mother_requirement_source
                 where mother_order_code = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, motherOrderCode);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new MotherRequirementSource(
                        rs.getString("mother_order_code"),
                        rs.getString("template_code"),
                        rs.getInt("template_version"),
                        rs.getString("saved_requirement")
                ));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("加工要求来源读取失败", ex);
        }
    }
}
