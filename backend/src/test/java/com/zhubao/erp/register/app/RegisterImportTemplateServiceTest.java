package com.zhubao.erp.register.app;

import com.zhubao.erp.register.domain.RegisterImportTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterImportTemplateServiceTest {

    private final RegisterImportTemplateService service = new RegisterImportTemplateService();

    @Test
    void exposesFourFieldRegisterTemplatesWithCommandMappings() {
        List<RegisterImportTemplate> templates = service.findTemplates();

        assertThat(templates).hasSize(4);
        assertThat(templates)
                .extracting(RegisterImportTemplate::code)
                .containsExactly("REG-INBOUND", "REG-OUTBOUND", "REG-POLISHING", "REG-ENGRAVING");
        assertThat(templates.get(0).fields())
                .anySatisfy(field -> assertThat(field.targetField()).isEqualTo("sourceRegisterSequence"))
                .anySatisfy(field -> assertThat(field.targetField()).isEqualTo("sourceSheetName"))
                .anySatisfy(field -> assertThat(field.targetField()).isEqualTo("sourceRegisterType"))
                .anySatisfy(field -> assertThat(field.targetField()).isEqualTo("sourceRegisterLineNo"))
                .anySatisfy(field -> assertThat(field.targetField()).isEqualTo("quantity + quantityUnit"))
                .anySatisfy(field -> assertThat(field.targetField()).isEqualTo("remark"));
        assertThat(templates.get(1).fields())
                .anySatisfy(field -> assertThat(field.targetField()).isEqualTo("paperExpressText + expressCompany + logisticsNo"));
        assertThat(templates.get(2).validationRules())
                .anySatisfy(rule -> assertThat(rule.description()).contains("姓名和日期必须展开"));
        assertThat(templates.get(3).validationRules())
                .anySatisfy(rule -> assertThat(rule.description()).contains("上下文继承"));
        assertThat(templates.get(3).fields())
                .anySatisfy(field -> assertThat(field.targetField()).isEqualTo("machineLineNo"));
    }

    @Test
    void summarizesTemplateReadinessWithPreviewParserReady() {
        RegisterImportTemplateService.RegisterImportSummary summary = service.summary();

        assertThat(summary.templates()).isEqualTo(4);
        assertThat(summary.fields()).isEqualTo(48);
        assertThat(summary.requiredFields()).isGreaterThan(20);
        assertThat(summary.commandReadyTemplates()).isEqualTo(4);
        assertThat(summary.parserStatus()).contains("preview parser ready");
    }
}
