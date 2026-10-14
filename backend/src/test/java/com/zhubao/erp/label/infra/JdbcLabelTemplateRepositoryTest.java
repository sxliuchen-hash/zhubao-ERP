package com.zhubao.erp.label.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhubao.erp.label.domain.LabelPrinterSetting;
import com.zhubao.erp.label.domain.LabelLayout;
import com.zhubao.erp.label.domain.LabelTemplate;
import com.zhubao.erp.label.domain.LabelTemplateField;
import com.zhubao.erp.label.domain.LabelType;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 标签模板 JDBC 仓储测试：验证 JSON 字段列往返、默认模板查询与默认标记清除。
 */
class JdbcLabelTemplateRepositoryTest {

    private JdbcLabelTemplateRepository repository;
    private JdbcDataSource dataSource;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        createSchema(dataSource);
        repository = new JdbcLabelTemplateRepository(dataSource, new ObjectMapper());
    }

    @Test
    void 新增返回自增主键并可反序列化字段与打印设置() {
        LabelTemplate created = repository.create(template("MOTHER-DEFAULT", LabelType.MOTHER, true));

        assertThat(created.id()).isNotNull();
        assertThat(repository.findByTemplateCode("MOTHER-DEFAULT")).get()
                .satisfies(template -> {
                    assertThat(template.fields()).extracting(LabelTemplateField::label).containsExactly("客户", "数量");
                    assertThat(template.printerSetting().widthMm()).isEqualTo(80);
                    assertThat(template.printerSetting().copies()).isEqualTo(2);
                    assertThat(template.layout().mode()).isEqualTo("COMPACT");
                    assertThat(template.layout().qrWidthMm()).isEqualTo(17.5);
                });
    }

    @Test
    void 按类型查询默认模板() {
        repository.create(template("MOTHER-DEFAULT", LabelType.MOTHER, true));
        repository.create(template("MOTHER-ALT", LabelType.MOTHER, false));

        assertThat(repository.findDefaultByType(LabelType.MOTHER)).get()
                .satisfies(template -> assertThat(template.templateCode()).isEqualTo("MOTHER-DEFAULT"));
    }

    @Test
    void 清除同类型默认标记() {
        LabelTemplate first = repository.create(template("MOTHER-DEFAULT", LabelType.MOTHER, true));
        LabelTemplate second = repository.create(template("MOTHER-ALT", LabelType.MOTHER, true));

        repository.clearDefaultForType(LabelType.MOTHER, second.id());

        assertThat(repository.findById(first.id())).get()
                .satisfies(template -> assertThat(template.isDefault()).isFalse());
    }

    @Test
    void 旧模板空版式列读取默认值() throws SQLException {
        LabelTemplate created = repository.create(template("MOTHER-OLD", LabelType.MOTHER, false));
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("update sys_label_template set layout_json = null where id = " + created.id());
        }
        assertThat(repository.findById(created.id())).get()
                .satisfies(template -> {
                    assertThat(template.layout().mode()).isEqualTo("AUTO");
                    assertThat(template.layout().qrWidthMm()).isNull();
                });
    }

    private LabelTemplate template(String code, LabelType type, boolean isDefault) {
        return new LabelTemplate(
                null, code, code + " 模板", type, "系统",
                List.of(new LabelTemplateField("客户", "customer", 1), new LabelTemplateField("数量", "quantity", 2)),
                "express", "receiver", isDefault, true,
                new LabelPrinterSetting(80, 60, 300, "ZD888", 2), "测试", "李华", "2026-07-09T10:00:00",
                new LabelLayout("COMPACT", 17.5, 9.0, null, null, null, null));
    }

    private void createSchema(JdbcDataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table sys_label_template (
                        id bigint primary key auto_increment,
                        template_code varchar(80) not null,
                        template_name varchar(120) not null,
                        label_type varchar(40) not null,
                        brand_title varchar(120),
                        fields_json text,
                        footer_left_source varchar(80),
                        footer_right_source varchar(80),
                        is_default tinyint(1) not null default 0,
                        enabled tinyint(1) not null default 1,
                        printer_setting_json text,
                        layout_json text,
                        remark varchar(255),
                        created_by varchar(80),
                        created_at varchar(40),
                        unique key uk_label_template_code (template_code)
                    )
                    """);
        }
    }
}
