package com.zhubao.erp.label.infra;

import com.zhubao.erp.label.domain.LabelPrinterSetting;
import com.zhubao.erp.label.domain.LabelTemplate;
import com.zhubao.erp.label.domain.LabelTemplateField;
import com.zhubao.erp.label.domain.LabelTemplateRepository;
import com.zhubao.erp.label.domain.LabelType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 标签模板内存仓储（本地默认）。启动时 seed 四类标签的默认模板，便于评审与离线使用。
 */
@Repository
@ConditionalOnProperty(name = "zhubao.label-template.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryLabelTemplateRepository implements LabelTemplateRepository {

    private final ConcurrentMap<Long, LabelTemplate> templates = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(7000);

    public InMemoryLabelTemplateRepository() {
        seedDefaults();
    }

    @Override
    public LabelTemplate create(LabelTemplate template) {
        long id = sequence.incrementAndGet();
        LabelTemplate saved = withId(template, id);
        templates.put(id, saved);
        return saved;
    }

    @Override
    public LabelTemplate update(LabelTemplate template) {
        templates.put(template.id(), template);
        return template;
    }

    @Override
    public Optional<LabelTemplate> findById(Long id) {
        return Optional.ofNullable(templates.get(id));
    }

    @Override
    public Optional<LabelTemplate> findByTemplateCode(String templateCode) {
        return templates.values().stream()
                .filter(template -> template.templateCode().equals(templateCode))
                .findFirst();
    }

    @Override
    public Optional<LabelTemplate> findDefaultByType(LabelType labelType) {
        return templates.values().stream()
                .filter(template -> template.labelType() == labelType && template.isDefault())
                .findFirst();
    }

    @Override
    public List<LabelTemplate> findAll() {
        return templates.values().stream()
                .sorted(Comparator.comparing(LabelTemplate::templateCode))
                .toList();
    }

    @Override
    public void clearDefaultForType(LabelType labelType, Long exceptId) {
        templates.values().stream()
                .filter(template -> template.labelType() == labelType && template.isDefault())
                .filter(template -> exceptId == null || !template.id().equals(exceptId))
                .forEach(template -> templates.put(template.id(), withDefault(template, false)));
    }

    private LabelTemplate withId(LabelTemplate template, Long id) {
        return new LabelTemplate(
                id, template.templateCode(), template.templateName(), template.labelType(),
                template.brandTitle(), template.fields(), template.footerLeftSource(),
                template.footerRightSource(), template.isDefault(), template.enabled(),
                template.printerSetting(), template.remark(), template.createdBy(), template.createdAt());
    }

    private LabelTemplate withDefault(LabelTemplate template, boolean isDefault) {
        return new LabelTemplate(
                template.id(), template.templateCode(), template.templateName(), template.labelType(),
                template.brandTitle(), template.fields(), template.footerLeftSource(),
                template.footerRightSource(), isDefault, template.enabled(),
                template.printerSetting(), template.remark(), template.createdBy(), template.createdAt());
    }

    private void seedDefaults() {
        LabelPrinterSetting mediumLabel = new LabelPrinterSetting(60, 40, 203, "", 1);
        create(new LabelTemplate(
                null, "MOTHER-DEFAULT", "母单默认模板", LabelType.MOTHER, "珠宝来料加工系统",
                List.of(
                        new LabelTemplateField("验证码", "verificationCode", 1),
                        new LabelTemplateField("客户", "customer", 2),
                        new LabelTemplateField("加工要求", "processingRequirement", 3),
                        new LabelTemplateField("期限", "dueDate", 4),
                        new LabelTemplateField("单号", "code", 5),
                        new LabelTemplateField("物料", "goodsName", 6),
                        new LabelTemplateField("数量", "quantity", 7),
                        new LabelTemplateField("重量", "weight", 8)),
                "express", "receiver", true, true, mediumLabel,
                "一期评审 seed", "system", "2026-07-09T10:00:00"));
        create(new LabelTemplate(
                null, "WORK_ORDER-DEFAULT", "子工单默认模板", LabelType.WORK_ORDER, "珠宝来料加工系统",
                List.of(
                        new LabelTemplateField("验证码", "verificationCode", 1),
                        new LabelTemplateField("客户", "customer", 2),
                        new LabelTemplateField("加工要求", "processingRequirement", 3),
                        new LabelTemplateField("期限", "dueDate", 4),
                        new LabelTemplateField("单号", "code", 5),
                        new LabelTemplateField("物料", "goodsName", 6),
                        new LabelTemplateField("数量", "quantity", 7),
                        new LabelTemplateField("重量", "weight", 8)),
                "", "", true, true, mediumLabel,
                "一期评审 seed", "system", "2026-07-09T10:00:00"));
        create(new LabelTemplate(
                null, "OUTBOUND-DEFAULT", "出货默认模板", LabelType.OUTBOUND, "珠宝来料加工系统",
                List.of(
                        new LabelTemplateField("客户", "customer", 1),
                        new LabelTemplateField("数量", "quantity", 2),
                        new LabelTemplateField("重量", "weight", 3)),
                "express", "receiver", true, true, mediumLabel,
                "一期评审 seed", "system", "2026-07-09T10:00:00"));
        create(new LabelTemplate(
                null, "REWORK-DEFAULT", "返工默认模板", LabelType.REWORK, "珠宝来料加工系统",
                List.of(
                        new LabelTemplateField("客户", "customer", 1),
                        new LabelTemplateField("物料", "goodsName", 2),
                        new LabelTemplateField("数量", "quantity", 3)),
                "", "", true, true, mediumLabel,
                "一期评审 seed", "system", "2026-07-09T10:00:00"));
    }
}
