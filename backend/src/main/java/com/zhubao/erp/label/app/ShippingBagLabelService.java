package com.zhubao.erp.label.app;

import com.zhubao.erp.business.domain.DeliveryConfirmedSource;
import com.zhubao.erp.business.domain.DeliveryDraft;
import com.zhubao.erp.business.domain.DeliveryDraftLine;
import com.zhubao.erp.business.domain.DeliveryDraftRepository;
import com.zhubao.erp.business.domain.DeliveryDraftStatus;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.label.domain.LabelField;
import com.zhubao.erp.label.domain.LabelPayload;
import com.zhubao.erp.label.domain.LabelPrinterSetting;
import com.zhubao.erp.label.domain.LabelType;
import com.zhubao.erp.label.domain.ShippingBagLabelPreview;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 从已确认交付快照生成 40×30 发货袋贴纸。
 * 不走厂内工单模板，补打不做出库。长字段只提示放不下，不缩小字号。
 */
@Service
public class ShippingBagLabelService {

    static final int FIT_LIMIT = 18;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final com.zhubao.erp.scan.app.PublicDeliveryAccessService publicAccess;
    private final DeliveryDraftRepository drafts;

    public ShippingBagLabelService(DeliveryDraftRepository drafts) {
        this(drafts, null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public ShippingBagLabelService(DeliveryDraftRepository drafts, com.zhubao.erp.scan.app.PublicDeliveryAccessService publicAccess) {
        this.publicAccess = publicAccess;
        this.drafts = drafts;
    }

    public ShippingBagLabelPreview preview(String deliveryId, int lineNo) {
        return compose(deliveryId, lineNo, null);
    }

    /** 补打沿用同一确认快照，不写出库或库存。 */
    public ShippingBagLabelPreview reprint(String deliveryId, int lineNo) {
        return compose(deliveryId, lineNo, null);
    }

    public ShippingBagLabelPreview preview(String deliveryId, int lineNo, String accessId) {
        return compose(deliveryId, lineNo, accessId);
    }
    private ShippingBagLabelPreview compose(String deliveryId, int lineNo, String accessId) {
        String id = deliveryId == null ? "" : deliveryId.trim();
        DeliveryDraft draft = drafts.find(id).orElseThrow(() -> new BusinessException("交付草稿不存在"));
        if (draft.status() != DeliveryDraftStatus.CONFIRMED) {
            throw new BusinessException("只有已确认交付才能打印发货袋贴纸");
        }
        DeliveryConfirmedSource source = drafts.confirmedSources(id).stream()
                .filter(row -> row.lineNo() == lineNo)
                .findFirst()
                .orElseThrow(() -> new BusinessException("发货袋不存在"));
        DeliveryDraftLine line = drafts.lines(id).stream()
                .filter(row -> row.lineNo() == lineNo)
                .findFirst()
                .orElseThrow(() -> new BusinessException("发货袋不存在"));
        if (publicAccess == null || accessId == null) throw new BusinessException("请先建立本次交付的公开扫码链接");
        publicAccess.readForDelivery(accessId, id);
        String bagCode = id + "-" + lineNo;
        List<LabelField> fields = new ArrayList<>();
        fields.add(new LabelField("数量", source.quantity().stripTrailingZeros().toPlainString() + line.quantityUnit()));
        fields.add(new LabelField("客户", line.customerName() == null || line.customerName().isBlank() ? "客户未记录" : line.customerName()));
        fields.add(new LabelField("材质", line.materialCode()));
        if (line.goodsSpecification() != null && !line.goodsSpecification().isBlank()) fields.add(new LabelField("规格", line.goodsSpecification()));
        if (line.processingSummary() != null && !line.processingSummary().isBlank()) fields.add(new LabelField("要求", line.processingSummary()));
        if (source.weight() != null) {
            fields.add(new LabelField("重量", source.weight().stripTrailingZeros().toPlainString() + "克"));
        }
        fields.add(new LabelField("来源", line.targetCode()));
        List<String> overflow = new ArrayList<>();
        for (LabelField field : fields) {
            if (field.value() != null && field.value().length() > FIT_LIMIT) {
                overflow.add(field.name() + "放不下，完整内容见A5和扫码页");
            }
        }
        LabelPayload payload = new LabelPayload(
                LabelType.SHIPPING_BAG,
                "发货袋",
                bagCode,
                "/d/" + accessId + "?bag=" + lineNo,
                fields,
                "",
                draft.remark(),
                TIME.format(LocalDateTime.now()),
                "珠宝来料加工系统",
                "SHIPPING_BAG_40X30",
                new LabelPrinterSetting(40, 30, 300, "", 1),
                null,
                List.of()
        );
        return new ShippingBagLabelPreview(payload, overflow);
    }
}
