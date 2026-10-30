package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.business.domain.CustomerReturnRepository;
import com.zhubao.erp.business.domain.CustomerReturnSource;
import com.zhubao.erp.business.domain.CustomerReturnStatus;
import com.zhubao.erp.business.domain.DeliveryConfirmedSource;
import com.zhubao.erp.business.domain.DeliveryDraft;
import com.zhubao.erp.business.domain.DeliveryDraftLine;
import com.zhubao.erp.business.domain.DeliveryDraftRepository;
import com.zhubao.erp.business.domain.DeliveryDraftStatus;
import com.zhubao.erp.business.domain.DeliveryStockPlace;
import com.zhubao.erp.business.domain.DeliveryStockPlaceLookup;
import com.zhubao.erp.business.domain.OutboundOrderRecord;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 按原交付收回明确份额，库存从已发转到待复核冻结。不自动合格，不删原账，不重开目标，也不写工资。
 * 返修是否收费、是否计酬尚未核定，依据里不填金额。
 */
@Service
public class CustomerReturnSourceService {

    private final DeliveryDraftRepository drafts;
    private final DeliveryStockPlaceLookup places;
    private final CustomerReturnRepository returns;
    private final BusinessDocumentRepository documents;
    private final StockLedgerService stock;
    private final BusinessWorkUnit workUnit;

    public CustomerReturnSourceService(DeliveryDraftRepository drafts, DeliveryStockPlaceLookup places,
                                       CustomerReturnRepository returns, BusinessDocumentRepository documents,
                                       StockLedgerService stock, BusinessWorkUnit workUnit) {
        this.drafts = drafts;
        this.places = places;
        this.returns = returns;
        this.documents = documents;
        this.stock = stock;
        this.workUnit = workUnit;
    }

    public CustomerReturnSource receive(String requestId, String actorId, String deliveryId, String outboundCode,
                                        BigDecimal quantity, BigDecimal weight) {
        String delivery = deliveryId == null ? "" : deliveryId.trim();
        String outbound = outboundCode == null ? "" : outboundCode.trim();
        String payload = delivery + "|" + outbound + "|" + plain(quantity) + "|" + plain(weight);
        String returnId = workUnit.execute(new CommandCall(
                requestId, actorId, "CUSTOMER_RETURN_SOURCE", "DELIVERY_DRAFT", delivery, payload
        ), session -> receiveIn(requestId, delivery, outbound, quantity, weight, session));
        return returns.find(returnId).orElseThrow(() -> new IllegalStateException("退货来源保存后无法读取"));
    }

    private String receiveIn(String requestId, String deliveryId, String outboundCode, BigDecimal quantity,
                             BigDecimal weight, WorkSession session) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessException("退货数量必须为正");
        }
        if (weight == null || weight.signum() <= 0) {
            throw new BusinessException("需要本份额的实际重量后才能转移或发货");
        }
        quantity = StockMeasurement.processingScale(quantity);
        weight = StockMeasurement.processingScale(weight);
        DeliveryDraft draft = drafts.lock(deliveryId, session).orElseThrow(() -> new BusinessException("交付草稿不存在"));
        if (draft.status() != DeliveryDraftStatus.CONFIRMED) {
            throw new BusinessException("退货必须关联已确认交付");
        }
        if (draft.businessKind() != BusinessKind.PROCESSING) {
            throw new BusinessException("打样和金工的退货不能改记成普通工单");
        }
        DeliveryConfirmedSource source = drafts.confirmedSources(draft.deliveryId()).stream()
                .filter(row -> row.outboundCode().equals(outboundCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException("退货必须关联已确认交付"));
        DeliveryDraftLine line = drafts.lines(draft.deliveryId()).stream()
                .filter(row -> row.lineNo() == source.lineNo())
                .findFirst()
                .orElseThrow(() -> new BusinessException("退货必须关联已确认交付"));
        StockMeasurement.rejectUndecidedGoldScale(line.businessKind());
        OutboundOrderRecord outbound = documents.findOutboundOrderByCode(outboundCode)
                .orElseThrow(() -> new BusinessException("退货必须关联已确认交付"));
        if (outbound.quantity().compareTo(source.quantity()) != 0) {
            throw new BusinessException("原出库与交付确认不一致");
        }
        BigDecimal returnedQty = BigDecimal.ZERO;
        BigDecimal returnedWeight = BigDecimal.ZERO;
        for (CustomerReturnSource prior : returns.listByOutbound(outboundCode)) {
            returnedQty = returnedQty.add(prior.quantity());
            returnedWeight = returnedWeight.add(prior.weight());
        }
        if (returnedQty.add(quantity).compareTo(source.quantity()) > 0) {
            throw new BusinessException("退货数量不得超过有效原交付");
        }
        if (returnedWeight.add(weight).compareTo(source.weight()) > 0) {
            throw new BusinessException("退货重量不得超过有效原交付");
        }
        DeliveryStockPlace place = places.place(BusinessTargetReference.fromCode(line.targetCode()))
                .orElseThrow(() -> new BusinessException("加工目标不存在"));
        String returnId = "CR" + UUID.randomUUID().toString().replace("-", "");
        stock.transfer(new StockTransferCommand(
                "customer_return_review", returnId,
                dimension(place, StockStatus.SHIPPED), dimension(place, StockStatus.FROZEN),
                quantity, weight, CustomerReturnSource.UNDECIDED_BASIS
        ), session);
        returns.save(new CustomerReturnSource(
                returnId, requestId, draft.deliveryId(), source.outboundCode(), source.bagId(),
                quantity, weight, CustomerReturnStatus.PENDING_REVIEW, CustomerReturnSource.UNDECIDED_BASIS, 1
        ), session);
        return returnId;
    }

    private StockDimension dimension(DeliveryStockPlace place, StockStatus status) {
        return new StockDimension(place.ownerType(), place.ownerCustomerId(), place.customerId(),
                place.motherOrderCode(), place.workOrderCode(), place.materialCode(), place.warehouseCode(), status);
    }

    private static String plain(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }
}
