package com.zhubao.erp.label.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.label.domain.LabelTemplate;
import com.zhubao.erp.label.domain.LabelLayout;
import com.zhubao.erp.label.domain.LabelTemplateField;
import com.zhubao.erp.label.domain.LabelType;
import com.zhubao.erp.label.domain.SaveLabelTemplateCommand;
import com.zhubao.erp.label.infra.InMemoryLabelTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 标签模板与打印机设置应用服务测试。
 */
class LabelTemplateServiceTest {

    private InMemoryLabelTemplateRepository repository;
    private LabelTemplateService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryLabelTemplateRepository();
        service = new LabelTemplateService(repository);
    }

    @Test
    @DisplayName("seed：内存仓储启动即含四类默认模板")
    void 内存仓储应含四类默认模板() {
        assertThat(service.findAll()).hasSize(4);
        assertThat(repository.findDefaultByType(LabelType.MOTHER)).get().satisfies(template -> {
            assertThat(template.fields()).extracting(LabelTemplateField::source)
                    .containsExactly("verificationCode", "customer", "processingRequirement", "dueDate");
            assertThat(template.footerLeftSource()).isEmpty();
            assertThat(template.footerRightSource()).isEmpty();
        });
        assertThat(repository.findDefaultByType(LabelType.WORK_ORDER)).get().satisfies(template ->
                assertThat(template.fields()).extracting(LabelTemplateField::source)
                        .containsExactly("verificationCode", "customer", "processingRequirement", "dueDate"));
        assertThat(repository.findDefaultByType(LabelType.OUTBOUND)).get().satisfies(template ->
                assertThat(template.fields()).extracting(LabelTemplateField::source)
                        .containsExactly("customer", "quantity"));
        assertThat(repository.findDefaultByType(LabelType.REWORK)).get().satisfies(template ->
                assertThat(template.fields()).extracting(LabelTemplateField::source)
                        .containsExactly("customer", "quantity"));
        assertThat(service.findAll()).allSatisfy(template -> {
            assertThat(template.printerSetting().widthMm()).isEqualTo(40);
            assertThat(template.printerSetting().heightMm()).isEqualTo(30);
            assertThat(template.printerSetting().dpi()).isEqualTo(300);
        });
    }

    @Test
    @DisplayName("新增：自定义模板与打印机设置")
    void 新增自定义模板与打印机设置() {
        SaveLabelTemplateCommand command = new SaveLabelTemplateCommand(
                null, "MOTHER-BOX", "母单周转盒模板", "MOTHER", "恒信珠宝定制",
                List.of(new LabelTemplateField("客户", "customer", 1), new LabelTemplateField("负责人", "receiver", 2)),
                "express", "receiver", false, true,
                80, 60, 300, "ZDesigner ZD888", 2, "周转盒专用", "库管员");

        LabelTemplate saved = service.save(command);

        assertThat(saved.id()).isNotNull();
        assertThat(saved.printerSetting().widthMm()).isEqualTo(80);
        assertThat(saved.printerSetting().dpi()).isEqualTo(300);
        assertThat(saved.printerSetting().copies()).isEqualTo(2);
        assertThat(saved.fields()).hasSize(2);
    }

    @Test
    @DisplayName("默认模板类型内唯一：设新默认会清除旧默认")
    void 设置新默认应清除同类型旧默认() {
        LabelTemplate oldDefault = repository.findDefaultByType(LabelType.MOTHER).orElseThrow();

        service.save(new SaveLabelTemplateCommand(
                null, "MOTHER-NEW", "母单新默认", "MOTHER", "系统",
                List.of(new LabelTemplateField("客户", "customer", 1)),
                "", "", true, true, 60, 40, 203, "", 1, "", "库管员"));

        assertThat(repository.findById(oldDefault.id())).get()
                .satisfies(template -> assertThat(template.isDefault()).isFalse());
        assertThat(repository.findDefaultByType(LabelType.MOTHER)).get()
                .satisfies(template -> assertThat(template.templateCode()).isEqualTo("MOTHER-NEW"));
    }

    @Test
    @DisplayName("校验：模板编码重复应拒绝")
    void 模板编码重复应拒绝() {
        assertThatThrownBy(() -> service.save(new SaveLabelTemplateCommand(
                null, "MOTHER-DEFAULT", "重复编码", "MOTHER", "系统",
                List.of(), "", "", false, true, 60, 40, 203, "", 1, "", "库管员")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("标签模板编码已存在");
    }

    @Test
    @DisplayName("校验：不支持的标签类型应拒绝")
    void 不支持的标签类型应拒绝() {
        assertThatThrownBy(() -> service.save(new SaveLabelTemplateCommand(
                null, "X-1", "非法类型", "UNKNOWN", "系统",
                List.of(), "", "", false, true, 60, 40, 203, "", 1, "", "库管员")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的标签类型");
    }

    @Test
    @DisplayName("更新：按 id 更新并保留创建人")
    void 按id更新并保留创建人() {
        LabelTemplate created = service.save(new SaveLabelTemplateCommand(
                null, "REWORK-Q", "返工质检模板", "REWORK", "系统",
                List.of(new LabelTemplateField("客户", "customer", 1)),
                "", "", false, true, 60, 40, 203, "", 1, "", "质检员"));

        LabelTemplate updated = service.save(new SaveLabelTemplateCommand(
                created.id(), "REWORK-Q", "返工质检模板改名", "REWORK", "系统",
                List.of(new LabelTemplateField("客户", "customer", 1)),
                "", "", false, false, 60, 40, 203, "", 1, "", "车间主管"));

        assertThat(updated.templateName()).isEqualTo("返工质检模板改名");
        assertThat(updated.enabled()).isFalse();
        assertThat(updated.createdBy()).isEqualTo("质检员");
    }

    @Test
    void 版式配置在内存仓储往返且拒绝越界值() {
        LabelLayout layout = new LabelLayout("COMPACT", 17.5, 10.0, 7.0, 9.0, 8.0, 5.0);
        LabelTemplate saved = service.save(new SaveLabelTemplateCommand(
                null, "MOTHER-LAYOUT", "排版模板", "MOTHER", "系统", List.of(),
                "", "", false, true, 40, 30, 300, "", 1, "", "库管员", layout));
        assertThat(repository.findById(saved.id())).get()
                .satisfies(template -> assertThat(template.layout()).isEqualTo(layout));
        assertThatThrownBy(() -> new LabelLayout("CUSTOM", null, null, null, null, null, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new LabelLayout("AUTO", 101.0, null, null, null, null, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new LabelLayout("AUTO", null, 3.0, null, null, null, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 版式配置拒绝任意样式属性() {
        assertThatThrownBy(() -> new ObjectMapper().readValue(
                "{\"mode\":\"AUTO\",\"css\":\"display:none\"}", LabelLayout.class))
                .hasRootCauseInstanceOf(BusinessException.class);
    }
}
