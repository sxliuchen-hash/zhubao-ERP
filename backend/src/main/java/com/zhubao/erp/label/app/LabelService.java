package com.zhubao.erp.label.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.label.domain.GenerateLabelCommand;
import com.zhubao.erp.label.domain.LabelField;
import com.zhubao.erp.label.domain.LabelPayload;
import com.zhubao.erp.label.domain.LabelType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 标签生成应用服务。
 *
 * <p>提供两种能力：<br>
 * 1) 通用生成：按前端传入的类型、主编码和固定字段组装可打印载荷；<br>
 * 2) 母单标签便捷生成：当结构化母单已存在时，直接按母单记录生成标签字段。<br>
 * 二维码内容统一为 {@code JG|<scanType>|<编码>}，扫码后可跳转对应详情。</p>
 */
@Service
public class LabelService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final BusinessDocumentRepository businessDocumentRepository;

    public LabelService() {
        this(null);
    }

    @Autowired
    public LabelService(BusinessDocumentRepository businessDocumentRepository) {
        this.businessDocumentRepository = businessDocumentRepository;
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
        return new LabelPayload(
                type,
                type.label(),
                code,
                qrContent(type, code),
                sanitizeFields(command.fields()),
                blankToEmpty(command.footerLeft()),
                blankToEmpty(command.footerRight()),
                TIME_FORMATTER.format(LocalDateTime.now())
        );
    }

    /** 母单标签便捷生成：从已落库的结构化母单记录组装标签（只放固定信息）。 */
    public LabelPayload generateMotherLabel(String motherOrderCode) {
        if (businessDocumentRepository == null) {
            throw new BusinessException("业务单据仓储不可用，无法按母单生成标签");
        }
        if (motherOrderCode == null || motherOrderCode.isBlank()) {
            throw new BusinessException("缺少母单号");
        }
        MotherOrderRecord record = businessDocumentRepository.findMotherOrderByCode(motherOrderCode.trim())
                .orElseThrow(() -> new BusinessException("未找到结构化母单：" + motherOrderCode));

        List<LabelField> fields = new ArrayList<>();
        addField(fields, "客户", customerText(record));
        addField(fields, "物料", firstNonBlank(record.goodsName(), record.materialCode()));
        addField(fields, "数量", quantityText(record));
        addField(fields, "重量", weightText(record));
        addField(fields, "收货日", record.inboundDate());

        return new LabelPayload(
                LabelType.MOTHER,
                LabelType.MOTHER.label(),
                record.code(),
                qrContent(LabelType.MOTHER, record.code()),
                fields,
                expressText(record),
                receiverText(record),
                TIME_FORMATTER.format(LocalDateTime.now())
        );
    }

    /** 二维码内容：系统统一扫码格式 JG|类型|编码。 */
    private String qrContent(LabelType type, String code) {
        return "JG|" + type.scanType() + "|" + code;
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
        if (record.customerId() != null) {
            return "客户#" + record.customerId();
        }
        return "";
    }

    private String quantityText(MotherOrderRecord record) {
        if (record.quantity() == null) {
            return "";
        }
        String unit = record.quantityUnit() == null || record.quantityUnit().isBlank() ? "件" : record.quantityUnit();
        return record.quantity().stripTrailingZeros().toPlainString() + " " + unit;
    }

    private String weightText(MotherOrderRecord record) {
        if (record.weight() == null) {
            return "";
        }
        return record.weight().stripTrailingZeros().toPlainString() + " 克";
    }

    private String expressText(MotherOrderRecord record) {
        String company = blankToEmpty(record.expressCompany());
        String courier = blankToEmpty(record.courierNo());
        if (company.isEmpty() && courier.isEmpty()) {
            return "";
        }
        return ("快递：" + (company + " " + courier)).trim();
    }

    private String receiverText(MotherOrderRecord record) {
        String receiver = firstNonBlank(record.receiverName(), record.unpackSigner());
        return receiver.isBlank() ? "" : "收货人：" + receiver;
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
}
