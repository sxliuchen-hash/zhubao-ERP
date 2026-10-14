package com.zhubao.erp.label.infra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhubao.erp.label.domain.LabelPrinterSetting;
import com.zhubao.erp.label.domain.LabelLayout;
import com.zhubao.erp.label.domain.LabelTemplate;
import com.zhubao.erp.label.domain.LabelTemplateField;
import com.zhubao.erp.label.domain.LabelTemplateRepository;
import com.zhubao.erp.label.domain.LabelType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 标签模板 JDBC 仓储（MySQL profile 默认）。字段列表和打印机设置以 JSON 文本列持久化。
 */
@Repository
@ConditionalOnProperty(name = "zhubao.label-template.repository", havingValue = "jdbc")
public class JdbcLabelTemplateRepository implements LabelTemplateRepository {

    private static final TypeReference<List<LabelTemplateField>> FIELD_LIST_TYPE = new TypeReference<>() {
    };

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public JdbcLabelTemplateRepository(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    @Override
    public LabelTemplate create(LabelTemplate template) {
        String sql = """
                insert into sys_label_template (
                    template_code, template_name, label_type, brand_title, fields_json,
                    footer_left_source, footer_right_source, is_default, enabled, printer_setting_json,
                    remark, created_by, created_at, layout_json
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindColumns(statement, template);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                Long id = keys.next() ? keys.getLong(1) : null;
                return withId(template, id);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to create label template: " + template.templateCode(), ex);
        }
    }

    @Override
    public LabelTemplate update(LabelTemplate template) {
        String sql = """
                update sys_label_template
                   set template_code = ?, template_name = ?, label_type = ?, brand_title = ?, fields_json = ?,
                       footer_left_source = ?, footer_right_source = ?, is_default = ?, enabled = ?,
                       printer_setting_json = ?, remark = ?, layout_json = ?
                 where id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, template.templateCode());
            statement.setString(2, template.templateName());
            statement.setString(3, template.labelType().name());
            statement.setString(4, template.brandTitle());
            statement.setString(5, toJson(template.fields()));
            statement.setString(6, template.footerLeftSource());
            statement.setString(7, template.footerRightSource());
            statement.setBoolean(8, template.isDefault());
            statement.setBoolean(9, template.enabled());
            statement.setString(10, toJson(template.printerSetting()));
            statement.setString(11, template.remark());
            statement.setString(12, toJson(template.layout()));
            statement.setLong(13, template.id());
            statement.executeUpdate();
            return template;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to update label template: " + template.id(), ex);
        }
    }

    @Override
    public Optional<LabelTemplate> findById(Long id) {
        String sql = selectSql() + " where id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load label template: " + id, ex);
        }
    }

    @Override
    public Optional<LabelTemplate> findByTemplateCode(String templateCode) {
        String sql = selectSql() + " where template_code = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, templateCode);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load label template by code: " + templateCode, ex);
        }
    }

    @Override
    public Optional<LabelTemplate> findDefaultByType(LabelType labelType) {
        String sql = selectSql() + " where label_type = ? and is_default = 1";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, labelType.name());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load default label template: " + labelType, ex);
        }
    }

    @Override
    public List<LabelTemplate> findAll() {
        String sql = selectSql() + " order by template_code";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<LabelTemplate> templates = new ArrayList<>();
            while (rs.next()) {
                templates.add(mapRow(rs));
            }
            return List.copyOf(templates);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load label templates", ex);
        }
    }

    @Override
    public void clearDefaultForType(LabelType labelType, Long exceptId) {
        String sql = "update sys_label_template set is_default = 0 where label_type = ? and is_default = 1 and id <> ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, labelType.name());
            statement.setLong(2, exceptId == null ? -1L : exceptId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to clear default label template: " + labelType, ex);
        }
    }

    private void bindColumns(PreparedStatement statement, LabelTemplate template) throws SQLException {
        statement.setString(1, template.templateCode());
        statement.setString(2, template.templateName());
        statement.setString(3, template.labelType().name());
        statement.setString(4, template.brandTitle());
        statement.setString(5, toJson(template.fields()));
        statement.setString(6, template.footerLeftSource());
        statement.setString(7, template.footerRightSource());
        statement.setBoolean(8, template.isDefault());
        statement.setBoolean(9, template.enabled());
        statement.setString(10, toJson(template.printerSetting()));
        statement.setString(11, template.remark());
        statement.setString(12, template.createdBy());
        statement.setString(13, template.createdAt());
        statement.setString(14, toJson(template.layout()));
    }

    private String selectSql() {
        return "select id, template_code, template_name, label_type, brand_title, fields_json, "
                + "footer_left_source, footer_right_source, is_default, enabled, printer_setting_json, "
                + "remark, created_by, created_at, layout_json from sys_label_template";
    }

    private LabelTemplate mapRow(ResultSet rs) throws SQLException {
        return new LabelTemplate(
                rs.getLong("id"),
                rs.getString("template_code"),
                rs.getString("template_name"),
                LabelType.valueOf(rs.getString("label_type")),
                rs.getString("brand_title"),
                readFields(rs.getString("fields_json")),
                rs.getString("footer_left_source"),
                rs.getString("footer_right_source"),
                rs.getBoolean("is_default"),
                rs.getBoolean("enabled"),
                readPrinterSetting(rs.getString("printer_setting_json")),
                rs.getString("remark"),
                rs.getString("created_by"),
                rs.getString("created_at"),
                readLayout(rs.getString("layout_json"))
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize label template payload", ex);
        }
    }

    private List<LabelTemplateField> readFields(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, FIELD_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize label template fields", ex);
        }
    }

    private LabelPrinterSetting readPrinterSetting(String json) {
        if (json == null || json.isBlank()) {
            return LabelPrinterSetting.defaultSetting();
        }
        try {
            return objectMapper.readValue(json, LabelPrinterSetting.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize label printer setting", ex);
        }
    }

    private LabelLayout readLayout(String json) {
        if (json == null || json.isBlank()) {
            return LabelLayout.defaultLayout();
        }
        try {
            return objectMapper.readValue(json, LabelLayout.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize label layout", ex);
        }
    }

    private LabelTemplate withId(LabelTemplate template, Long id) {
        return new LabelTemplate(
                id, template.templateCode(), template.templateName(), template.labelType(),
                template.brandTitle(), template.fields(), template.footerLeftSource(),
                template.footerRightSource(), template.isDefault(), template.enabled(),
                template.printerSetting(), template.remark(), template.createdBy(), template.createdAt(), template.layout());
    }
}
