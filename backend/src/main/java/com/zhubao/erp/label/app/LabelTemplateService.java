package com.zhubao.erp.label.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.label.domain.LabelPrinterSetting;
import com.zhubao.erp.label.domain.LabelTemplate;
import com.zhubao.erp.label.domain.LabelTemplateField;
import com.zhubao.erp.label.domain.LabelTemplateRepository;
import com.zhubao.erp.label.domain.LabelType;
import com.zhubao.erp.label.domain.SaveLabelTemplateCommand;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 标签模板与打印机设置应用服务：支持用户自定义标签模板（字段、抬头、脚注）和打印机设置。
 */
@Service
public class LabelTemplateService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final LabelTemplateRepository repository;

    public LabelTemplateService(LabelTemplateRepository repository) {
        this.repository = repository;
    }

    public List<LabelTemplate> findAll() {
        return repository.findAll();
    }

    /**
     * 新增或更新标签模板。{@code id} 为空表示新增，非空表示更新；模板编码全局唯一。
     * 若标记为默认模板，会清除同类型其他模板的默认标记。
     */
    public LabelTemplate save(SaveLabelTemplateCommand command) {
        if (command == null) {
            throw new BusinessException("标签模板命令不能为空");
        }
        require(command.templateCode(), "templateCode");
        require(command.templateName(), "templateName");
        LabelType type = LabelType.fromString(command.labelType());
        if (type == null) {
            throw new BusinessException("不支持的标签类型：" + command.labelType());
        }
        String templateCode = command.templateCode().trim();

        // 校验模板编码唯一
        repository.findByTemplateCode(templateCode).ifPresent(existing -> {
            if (command.id() == null || !existing.id().equals(command.id())) {
                throw new BusinessException("标签模板编码已存在：" + templateCode);
            }
        });

        boolean enabled = command.enabled() == null || command.enabled();
        boolean isDefault = command.isDefault() != null && command.isDefault();
        String operator = blankToDefault(command.operator(), "标签模板维护员");
        LabelPrinterSetting printerSetting = new LabelPrinterSetting(
                nullToZero(command.widthMm()), nullToZero(command.heightMm()), nullToZero(command.dpi()),
                command.printerName(), nullToZero(command.copies()));
        List<LabelTemplateField> fields = normalizeFields(command.fields());

        LabelTemplate saved;
        if (command.id() == null) {
            LabelTemplate template = new LabelTemplate(
                    null, templateCode, command.templateName().trim(), type, blankToEmpty(command.brandTitle()),
                    fields, blankToEmpty(command.footerLeftSource()), blankToEmpty(command.footerRightSource()),
                    isDefault, enabled, printerSetting, blankToEmpty(command.remark()),
                    operator, TIME_FORMATTER.format(LocalDateTime.now()), command.layout());
            saved = repository.create(template);
        } else {
            LabelTemplate existing = repository.findById(command.id())
                    .orElseThrow(() -> new BusinessException("标签模板不存在：" + command.id()));
            LabelTemplate template = new LabelTemplate(
                    existing.id(), templateCode, command.templateName().trim(), type, blankToEmpty(command.brandTitle()),
                    fields, blankToEmpty(command.footerLeftSource()), blankToEmpty(command.footerRightSource()),
                    isDefault, enabled, printerSetting, blankToEmpty(command.remark()),
                    existing.createdBy(), existing.createdAt(),
                    command.layout() == null ? existing.layout() : command.layout());
            saved = repository.update(template);
        }

        // 默认模板类型内唯一：设置新默认前清除同类型其他模板的默认标记
        if (isDefault) {
            repository.clearDefaultForType(type, saved.id());
        }
        return saved;
    }

    private List<LabelTemplateField> normalizeFields(List<LabelTemplateField> fields) {
        if (fields == null) {
            return List.of();
        }
        AtomicInteger order = new AtomicInteger(0);
        return fields.stream()
                .filter(field -> field != null && field.label() != null && !field.label().isBlank())
                .map(field -> new LabelTemplateField(
                        field.label().trim(),
                        field.source(),
                        field.sortOrder() > 0 ? field.sortOrder() : order.incrementAndGet()))
                .toList();
    }

    private void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(field + " 不能为空");
        }
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
