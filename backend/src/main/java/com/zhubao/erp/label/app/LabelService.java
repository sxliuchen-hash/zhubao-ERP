package com.zhubao.erp.label.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.LabelReprintLog;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.MotherSplitState;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.label.domain.GenerateLabelCommand;
import com.zhubao.erp.label.domain.LabelField;
import com.zhubao.erp.label.domain.LabelPayload;
import com.zhubao.erp.label.domain.LabelPrinterSetting;
import com.zhubao.erp.label.domain.LabelTemplate;
import com.zhubao.erp.label.domain.LabelTemplateField;
import com.zhubao.erp.label.domain.LabelTemplateRepository;
import com.zhubao.erp.label.domain.LabelType;
import com.zhubao.erp.business.domain.OrderChangeRepository;
import com.zhubao.erp.label.domain.ReprintLabelCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 标签生成应用服务。
 *
 * <p>提供三种能力：<br>
 * 1) 通用生成：按前端传入的类型、主编码和固定字段组装可打印载荷；<br>
 * 2) 母单或子单贴纸：从已落库单据带出验证码、客户、加工要求、期限和单号；<br>
 * 3) 补打：沿用原验证码，并把原因写入补打日志。<br>
 * 二维码内容统一为 {@code JG|<scanType>|<编码>}。</p>
 *
 * <p>若命令带模板编码或该类型存在默认模板，则采用模板的品牌抬头、字段来源和打印机设置；
 * 模板缺省时回退到内置现场贴纸布局。物料、数量、重量可以留在模板里，但不能成为唯一内容。</p>
 */
@Service
public class LabelService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String DEFAULT_BRAND = "珠宝来料加工系统";

    private final BusinessDocumentRepository businessDocumentRepository;
    private final LabelTemplateRepository labelTemplateRepository;
    private OrderChangeRepository orderChanges;

    public LabelService() {
        this(null, null);
    }

    public LabelService(BusinessDocumentRepository businessDocumentRepository) {
        this(businessDocumentRepository, null);
    }

    @Autowired
    public LabelService(
            BusinessDocumentRepository businessDocumentRepository,
            LabelTemplateRepository labelTemplateRepository
    ) {
        this.businessDocumentRepository = businessDocumentRepository;
        this.labelTemplateRepository = labelTemplateRepository;
    }

    @Autowired(required = false)
    public void setOrderChanges(OrderChangeRepository orderChanges) {
        this.orderChanges = orderChanges;
    }

    /** 通用标签生成：按命令组装载荷。 */
    public LabelPayload generate(GenerateLabelCommand command) {
        if (command == null) {
            throw new BusinessException("标签生成命令不能为空");
        }
        LabelType type = LabelType.fromString(command.type());
        if (type == null) {
            throw new BusinessException("不支持的标签类型：" + command.type());
        }
        if (command.primaryCode() == null || command.primaryCode().isBlank()) {
            throw new BusinessException("缺少标签主编码");
        }
        String code = command.primaryCode().trim();
        LabelTemplate template = resolveTemplate(command.templateCode(), type);
        String brand = template != null && !template.brandTitle().isBlank() ? template.brandTitle() : DEFAULT_BRAND;
        LabelPrinterSetting printerSetting = template != null ? template.printerSetting() : LabelPrinterSetting.defaultSetting();
        return new LabelPayload(
                type,
                type.label(),
                code,
                qrContent(type, code),
                sanitizeFields(command.fields()),
                blankToEmpty(command.footerLeft()),
                blankToEmpty(command.footerRight()),
                TIME_FORMATTER.format(LocalDateTime.now()),
                brand,
                template != null ? template.templateCode() : "",
                printerSetting,
                template != null ? template.layout() : null
        );
    }

    /** 母单标签便捷生成：从已落库的结构化母单记录组装标签。 */
    public LabelPayload generateMotherLabel(String motherOrderCode) {
        return generateMotherLabel(motherOrderCode, null);
    }

    /** 母单标签便捷生成，可指定模板编码。 */
    public LabelPayload generateMotherLabel(String motherOrderCode, String templateCode) {
        MotherOrderRecord record = requireMother(motherOrderCode);
        businessDocumentRepository.findMotherSplitState(record.code())
                .filter(state -> MotherSplitState.SPLIT.equals(state.decision()))
                .ifPresent(state -> {
                    throw new BusinessException("这张母单已拆成子单，请打印子单贴纸");
                });
        StickerFacts facts = motherFacts(record);
        return payloadOf(LabelType.MOTHER, record.code(), facts, templateCode, true);
    }

    /** 子单标签：从已拆出的子单带出现场贴纸。 */
    public LabelPayload generateWorkOrderLabel(String workOrderCode) {
        return generateWorkOrderLabel(workOrderCode, null);
    }

    /** 子单标签，可指定模板编码。 */
    public LabelPayload generateWorkOrderLabel(String workOrderCode, String templateCode) {
        if (businessDocumentRepository == null) {
            throw new BusinessException("业务单据仓储不可用，无法按子单生成标签");
        }
        if (workOrderCode == null || workOrderCode.isBlank()) {
            throw new BusinessException("缺少子单号");
        }
        ChildWorkOrderRecord record = businessDocumentRepository.findChildWorkOrderByCode(workOrderCode.trim())
                .orElseThrow(() -> new BusinessException("未找到子单：" + workOrderCode));
        return payloadOf(LabelType.WORK_ORDER, record.code(), childFacts(record), templateCode, false);
    }

    /** 补打母单或子单贴纸，验证码不变，并写入补打日志。 */
    public LabelPayload reprint(ReprintLabelCommand command) {
        if (command == null) {
            throw new BusinessException("补打命令不能为空");
        }
        if (command.reason() == null || command.reason().isBlank()) {
            throw new BusinessException("补打必须填写原因");
        }
        String reason = command.reason().trim();
        if (reason.length() > 200) {
            throw new BusinessException("补打原因过长");
        }
        LabelType type = LabelType.fromString(command.type());
        if (type != LabelType.MOTHER && type != LabelType.WORK_ORDER) {
            throw new BusinessException("只支持母单和子单贴纸补打");
        }
        if (businessDocumentRepository == null) {
            throw new BusinessException("业务单据仓储不可用，无法记录补打日志");
        }
        LabelPayload payload = type == LabelType.MOTHER
                ? generateMotherLabel(command.primaryCode())
                : generateWorkOrderLabel(command.primaryCode());
        String verificationCode = type == LabelType.MOTHER
                ? motherFacts(requireMother(command.primaryCode())).verificationCode()
                : businessDocumentRepository.findChildWorkOrderByCode(command.primaryCode().trim())
                        .map(ChildWorkOrderRecord::verificationCode).orElse("");
        if (verificationCode.isBlank()) {
            throw new BusinessException("这张贴纸还没有验证码，不能补打");
        }
        String operator = command.operator() == null || command.operator().isBlank()
                ? "现场"
                : command.operator().trim();
        businessDocumentRepository.saveLabelReprint(new LabelReprintLog(
                null,
                type.name(),
                payload.primaryCode(),
                verificationCode,
                operator,
                reason,
                TIME_FORMATTER.format(LocalDateTime.now())
        ));
        return payload;
    }

    private LabelPayload payloadOf(
            LabelType type,
            String code,
            StickerFacts facts,
            String templateCode,
            boolean motherFallback
    ) {
        LabelTemplate template = resolveTemplate(templateCode, type);
        String brand = template != null && !template.brandTitle().isBlank() ? template.brandTitle() : DEFAULT_BRAND;
        LabelPrinterSetting printerSetting = template != null ? template.printerSetting() : LabelPrinterSetting.defaultSetting();
        String footerLeft = "";
        String footerRight = "";
        if (motherFallback) {
            footerLeft = template == null ? facts.express() : footerValue(template.footerLeftSource(), facts);
            footerRight = template == null ? receiverFooter(facts.receiver()) : footerValue(template.footerRightSource(), facts);
        }
        return new LabelPayload(
                type,
                type.label(),
                code,
                qrContent(type, code),
                composeFields(template, facts, motherFallback),
                footerLeft,
                footerRight,
                TIME_FORMATTER.format(LocalDateTime.now()),
                brand,
                template != null ? template.templateCode() : "",
                printerSetting,
                template != null ? template.layout() : null
        );
    }

    private MotherOrderRecord requireMother(String motherOrderCode) {
        if (businessDocumentRepository == null) {
            throw new BusinessException("业务单据仓储不可用，无法按母单生成标签");
        }
        if (motherOrderCode == null || motherOrderCode.isBlank()) {
            throw new BusinessException("缺少母单号");
        }
        return businessDocumentRepository.findMotherOrderByCode(motherOrderCode.trim())
                .orElseThrow(() -> new BusinessException("未找到结构化母单：" + motherOrderCode));
    }

    private StickerFacts motherFacts(MotherOrderRecord record) {
        String verificationCode = businessDocumentRepository.findMotherSplitState(record.code())
                .map(MotherSplitState::verificationCode)
                .orElse("");
        return new StickerFacts(
                blankToEmpty(verificationCode),
                customerText(record),
                blankToEmpty(record.processingRequirement()),
                blankToEmpty(record.dueDate()),
                blankToEmpty(record.code()),
                blankToEmpty(record.goodsName()),
                blankToEmpty(record.materialCode()),
                quantityText(record.quantity(), record.quantityUnit()),
                weightText(record.weight()),
                blankToEmpty(record.inboundDate()),
                blankToEmpty(record.warehouseCode()),
                firstNonBlank(record.receiverName(), record.unpackSigner()),
                defaultExpressText(record)
        );
    }

    private StickerFacts childFacts(ChildWorkOrderRecord record) {
        return new StickerFacts(
                blankToEmpty(record.verificationCode()),
                blankToEmpty(record.customerName()),
                blankToEmpty(record.processingRequirement()),
                blankToEmpty(record.dueDate()),
                blankToEmpty(record.code()),
                blankToEmpty(record.goodsName()),
                blankToEmpty(record.materialCode()),
                quantityText(record.quantity(), record.quantityUnit()),
                weightText(record.weight()),
                "",
                blankToEmpty(record.warehouseCode()),
                "",
                ""
        );
    }

    private List<LabelField> composeFields(LabelTemplate template, StickerFacts facts, boolean motherFallback) {
        List<LabelField> fields = new ArrayList<>();
        if (template != null) {
            template.fields().stream()
                    .sorted(Comparator.comparingInt(LabelTemplateField::sortOrder))
                    .forEach(field -> addField(fields, field.label(), valueOf(field.source(), facts)));
            return fields;
        }
        addField(fields, "验证码", facts.verificationCode());
        addField(fields, "客户", facts.customer());
        addField(fields, "加工要求", facts.processingRequirement());
        addField(fields, "期限", facts.dueDate());
        addField(fields, "单号", facts.code());
        addField(fields, "物料", firstNonBlank(facts.goodsName(), facts.materialCode()));
        addField(fields, "数量", facts.quantity());
        addField(fields, "重量", facts.weight());
        if (motherFallback) {
            addField(fields, "收货日", facts.inboundDate());
        }
        return fields;
    }

    /** 解析要使用的模板：优先按模板编码，其次按类型默认模板；仓储不可用或无匹配时返回 null。 */
    private LabelTemplate resolveTemplate(String templateCode, LabelType type) {
        if (labelTemplateRepository == null) {
            return null;
        }
        if (templateCode != null && !templateCode.isBlank()) {
            LabelTemplate template = labelTemplateRepository.findByTemplateCode(templateCode.trim())
                    .orElseThrow(() -> new BusinessException("未找到标签模板：" + templateCode));
            if (template.labelType() != type) {
                throw new BusinessException("标签模板类型不匹配：" + templateCode);
            }
            if (!template.enabled()) {
                throw new BusinessException("标签模板已停用：" + templateCode);
            }
            return template;
        }
        Optional<LabelTemplate> defaultTemplate = labelTemplateRepository.findDefaultByType(type);
        return defaultTemplate.filter(LabelTemplate::enabled).orElse(null);
    }

    /** 按字段来源键取值。 */
    private String valueOf(String source, StickerFacts facts) {
        if (source == null) {
            return "";
        }
        return switch (source.trim()) {
            case "customer" -> facts.customer();
            case "goodsName" -> facts.goodsName();
            case "materialCode" -> facts.materialCode();
            case "quantity" -> facts.quantity();
            case "weight" -> facts.weight();
            case "inboundDate" -> facts.inboundDate();
            case "warehouseCode" -> facts.warehouseCode();
            case "receiver" -> facts.receiver();
            case "express" -> facts.express();
            case "code" -> facts.code();
            case "verificationCode" -> facts.verificationCode();
            case "processingRequirement" -> facts.processingRequirement();
            case "dueDate" -> facts.dueDate();
            default -> "";
        };
    }

    private String footerValue(String source, StickerFacts facts) {
        if (source == null || source.isBlank()) {
            return "";
        }
        return valueOf(source, facts);
    }

    /** 新标签二维码包含版本和标签实例。没有生效改单时版本为 0。 */
    private String qrContent(LabelType type, String code) {
        int version = orderChanges == null ? 0 : orderChanges.latestAppliedVersion(code);
        String instance = "LI" + Integer.toHexString(Math.abs((type.scanType() + "|" + code + "|" + version).hashCode()));
        return "JG|" + type.scanType() + "|" + code + "|" + version + "|" + instance;
    }

    private List<LabelField> sanitizeFields(List<LabelField> fields) {
        List<LabelField> result = new ArrayList<>();
        for (LabelField field : fields) {
            if (field != null && field.name() != null && !field.name().isBlank()) {
                result.add(new LabelField(field.name().trim(), blankToEmpty(field.value())));
            }
        }
        return result;
    }

    private void addField(List<LabelField> fields, String name, String value) {
        if (value != null && !value.isBlank()) {
            fields.add(new LabelField(name, value.trim()));
        }
    }

    private String customerText(MotherOrderRecord record) {
        if (record.customerName() != null && !record.customerName().isBlank()) {
            return record.customerName().trim();
        }
        if (record.customerId() != null) {
            return "客户#" + record.customerId();
        }
        return "";
    }

    private String quantityText(BigDecimal quantity, String unit) {
        if (quantity == null) {
            return "";
        }
        String unitText = unit == null || unit.isBlank() ? "件" : unit;
        return quantity.stripTrailingZeros().toPlainString() + " " + unitText;
    }

    private String weightText(BigDecimal weight) {
        if (weight == null) {
            return "";
        }
        return weight.stripTrailingZeros().toPlainString() + " 克";
    }

    private String defaultExpressText(MotherOrderRecord record) {
        String company = blankToEmpty(record.expressCompany());
        String courier = blankToEmpty(record.courierNo());
        if (company.isEmpty() && courier.isEmpty()) {
            return "";
        }
        return ("快递：" + (company + " " + courier)).trim();
    }

    private String receiverFooter(String receiver) {
        return receiver == null || receiver.isBlank() ? "" : "收货人：" + receiver.trim();
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        return b == null ? "" : b.trim();
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private record StickerFacts(
            String verificationCode,
            String customer,
            String processingRequirement,
            String dueDate,
            String code,
            String goodsName,
            String materialCode,
            String quantity,
            String weight,
            String inboundDate,
            String warehouseCode,
            String receiver,
            String express
    ) {
    }
}
