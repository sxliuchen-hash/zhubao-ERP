package com.zhubao.erp.finance.app;

import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.business.domain.DeliveryConfirmedSource;
import com.zhubao.erp.business.domain.DeliveryDraft;
import com.zhubao.erp.business.domain.DeliveryDraftLine;
import com.zhubao.erp.business.domain.DeliveryDraftRepository;
import com.zhubao.erp.business.domain.DeliveryDraftStatus;
import com.zhubao.erp.business.domain.DeliveryStockPlace;
import com.zhubao.erp.business.domain.DeliveryStockPlaceLookup;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.ChargeUnit;
import com.zhubao.erp.finance.domain.CustomerBillAuthorization;
import com.zhubao.erp.finance.domain.CustomerChargeCalculator;
import com.zhubao.erp.finance.domain.CustomerChargeCoverage;
import com.zhubao.erp.finance.domain.CustomerChargeLine;
import com.zhubao.erp.finance.domain.CustomerChargeSnapshot;
import com.zhubao.erp.finance.domain.CustomerDeliveryBill;
import com.zhubao.erp.finance.domain.CustomerDeliveryBillKind;
import com.zhubao.erp.finance.domain.CustomerDeliveryBillLine;
import com.zhubao.erp.finance.domain.CustomerDeliveryBillRepository;
import com.zhubao.erp.finance.domain.FinanceRepository;
import com.zhubao.erp.finance.domain.ReceivableLine;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 确认本次已发交付的客户账单并写应收。未交齐也可以出本次账。
 * 不要求工资权限。同一发货单只有一张初始账单，调整同根追加差额，不改原应收。
 */
@Service
public class CustomerDeliveryBillingService {

    private static final DateTimeFormatter RECORDED_AT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final DeliveryDraftRepository drafts;
    private final DeliveryStockPlaceLookup places;
    private final CustomerDeliveryBillRepository bills;
    private final FinanceRepository finance;
    private final CustomerBillAuthorization authorization;
    private final BusinessWorkUnit workUnit;

    public CustomerDeliveryBillingService(DeliveryDraftRepository drafts, DeliveryStockPlaceLookup places,
                                          CustomerDeliveryBillRepository bills, FinanceRepository finance,
                                          CustomerBillAuthorization authorization, BusinessWorkUnit workUnit) {
        this.drafts = drafts;
        this.places = places;
        this.bills = bills;
        this.finance = finance;
        this.authorization = authorization;
        this.workUnit = workUnit;
    }

    public CustomerDeliveryBill confirm(OperatorContext actor, String requestId, String deliveryId,
                                        List<CustomerChargeLine> lines, BigDecimal lossQuantity,
                                        BigDecimal coveredAmount) {
        String delivery = required(deliveryId);
        String id = workUnit.execute(new CommandCall(
                requestId, actor.operator(), "CONFIRM_CUSTOMER_BILL", "DELIVERY_DRAFT", delivery,
                payload(delivery, lines, lossQuantity, coveredAmount)
        ), session -> confirmIn(actor, requestId, delivery, lines, lossQuantity, coveredAmount, session));
        return bills.find(id).orElseThrow(() -> new IllegalStateException("客户账单保存后无法读取"));
    }

    private String confirmIn(OperatorContext actor, String requestId, String deliveryId, List<CustomerChargeLine> lines,
                             BigDecimal lossQuantity, BigDecimal coveredAmount, WorkSession session) {
        DeliveryDraft draft = drafts.lock(deliveryId, session).orElseThrow(() -> new BusinessException("交付草稿不存在"));
        if (draft.status() != DeliveryDraftStatus.CONFIRMED) {
            throw new BusinessException("只有已确认交付才能出客户账单");
        }
        authorization.assertCanBill(actor, draft.customerId());
        if (bills.findInitial(draft.deliveryId()).isPresent()) {
            throw new BusinessException("同一已确认发货单只能有一张初始客户账单");
        }
        CustomerChargeSnapshot snapshot = snapshot(draft, lines, lossQuantity);
        if (snapshot.totalAmount().signum() < 0) {
            throw new BusinessException("净额为负，不能直接生成负加工费");
        }
        BigDecimal legacy = legacyAmount(lines);
        BigDecimal covered = coveredAmount == null ? BigDecimal.ZERO : coveredAmount;
        if (legacy.signum() == 0 && coveredAmount != null && covered.signum() != 0) {
            throw new BusinessException("没有可核清的旧凭据");
        }
        if (legacy.signum() != 0 && (coveredAmount == null || covered.compareTo(legacy) != 0)) {
            throw new BusinessException(coveredAmount == null ? "已有正式凭据，须先核清覆盖" : "覆盖金额与已有凭据不一致");
        }
        BigDecimal delta = snapshot.totalAmount().subtract(covered);
        String billId = "CB" + UUID.randomUUID().toString().replace("-", "");
        CustomerDeliveryBill bill = new CustomerDeliveryBill(
                billId, requestId, billId, draft.deliveryId(), draft.customerId(),
                CustomerDeliveryBillKind.INITIAL, snapshot.totalAmount(), 1);
        bills.save(bill, billLines(billId, snapshot), session);
        if (delta.signum() != 0) {
            finance.saveReceivable(receivable(actor, draft, lines, snapshot, billId, delta, ""), session);
        }
        return billId;
    }

    public CustomerDeliveryBill adjust(OperatorContext actor, String requestId, String deliveryId,
                                       List<CustomerChargeLine> lines, BigDecimal lossQuantity) {
        String delivery = required(deliveryId);
        String id = workUnit.execute(new CommandCall(
                requestId, actor.operator(), "ADJUST_CUSTOMER_BILL", "DELIVERY_DRAFT", delivery,
                payload(delivery, lines, lossQuantity, null)
        ), session -> adjustIn(actor, requestId, delivery, lines, lossQuantity, session));
        return bills.find(id).orElseThrow(() -> new IllegalStateException("客户账单保存后无法读取"));
    }

    private String adjustIn(OperatorContext actor, String requestId, String deliveryId, List<CustomerChargeLine> lines,
                            BigDecimal lossQuantity, WorkSession session) {
        DeliveryDraft draft = drafts.lock(deliveryId, session).orElseThrow(() -> new BusinessException("交付草稿不存在"));
        authorization.assertCanBill(actor, draft.customerId());
        CustomerDeliveryBill initial = bills.findInitial(draft.deliveryId())
                .orElseThrow(() -> new BusinessException("还没有初始客户账单"));
        CustomerChargeSnapshot snapshot = snapshot(draft, lines, lossQuantity);
        List<CustomerDeliveryBill> history = bills.listByRoot(initial.rootBillId());
        CustomerDeliveryBill latest = history.get(history.size() - 1);
        int version = latest.version() + 1;
        BigDecimal delta = snapshot.totalAmount().subtract(latest.totalAmount());
        String billId = "CB" + UUID.randomUUID().toString().replace("-", "");
        CustomerDeliveryBill bill = new CustomerDeliveryBill(
                billId, requestId, initial.rootBillId(), draft.deliveryId(), draft.customerId(),
                CustomerDeliveryBillKind.ADJUSTMENT, snapshot.totalAmount(), version);
        bills.save(bill, billLines(billId, snapshot), session);
        if (delta.signum() != 0) {
            String reverses = delta.signum() < 0 ? receivableCode(initial.billId()) : "";
            finance.saveReceivable(receivable(actor, draft, lines, snapshot, billId, delta, reverses), session);
        }
        return billId;
    }

    private CustomerChargeSnapshot snapshot(DeliveryDraft draft, List<CustomerChargeLine> lines, BigDecimal lossQuantity) {
        List<DeliveryDraftLine> draftLines = drafts.lines(draft.deliveryId());
        List<DeliveryConfirmedSource> sources = drafts.confirmedSources(draft.deliveryId());
        if (sources.isEmpty()) {
            throw new BusinessException("只有已确认交付才能出客户账单");
        }
        String unit = draftLines.get(0).quantityUnit();
        for (DeliveryDraftLine line : draftLines) {
            if (!unit.equals(line.quantityUnit())) {
                throw new BusinessException("不能把不同单位的数量加在一起");
            }
        }
        BigDecimal shipped = sources.stream().map(DeliveryConfirmedSource::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return CustomerChargeCalculator.calculate(
                new CustomerChargeCoverage(draft.deliveryId(), shipped, unit, lossQuantity), lines);
    }

    private BigDecimal legacyAmount(List<CustomerChargeLine> lines) {
        Set<String> mothers = new HashSet<>();
        for (CustomerChargeLine line : lines) {
            if (line.targetCode().isBlank()) {
                continue;
            }
            DeliveryStockPlace place = places.place(BusinessTargetReference.fromCode(line.targetCode())).orElse(null);
            mothers.add(place == null ? line.targetCode() : place.motherOrderCode());
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (ReceivableLine receivable : finance.listReceivables()) {
            if (mothers.contains(receivable.motherOrderCode()) && !"DELIVERY_BILL".equals(receivable.sourceType())) {
                sum = sum.add(receivable.amount());
            }
        }
        return sum;
    }

    private ReceivableLine receivable(OperatorContext actor, DeliveryDraft draft, List<CustomerChargeLine> lines,
                                      CustomerChargeSnapshot snapshot, String billId, BigDecimal amount, String reverses) {
        CustomerChargeLine first = lines.get(0);
        DeliveryStockPlace place = first.targetCode().isBlank()
                ? null : places.place(BusinessTargetReference.fromCode(first.targetCode())).orElse(null);
        String mother = place == null ? "" : place.motherOrderCode();
        String work = place == null ? "" : place.workOrderCode();
        return new ReceivableLine(
                receivableCode(billId), draft.customerId(), "", YearMonth.now().toString(), "DELIVERY_BILL",
                "delivery-bill:" + billId, mother, work, first.name(), new BigDecimal("1.000"),
                ChargeUnit.JOB, amount, amount, reverses,
                "交付 " + draft.deliveryId(), actor.operator(), RECORDED_AT.format(LocalDateTime.now()));
    }

    private static String receivableCode(String billId) {
        return "YS" + billId.substring(2);
    }

    private static List<CustomerDeliveryBillLine> billLines(String billId, CustomerChargeSnapshot snapshot) {
        List<CustomerDeliveryBillLine> stored = new ArrayList<>();
        int number = 1;
        for (CustomerChargeSnapshot.CustomerChargeResultLine line : snapshot.lines()) {
            stored.add(new CustomerDeliveryBillLine(
                    billId, number++, line.name(), line.method(), line.amount(), line.expression()));
        }
        return stored;
    }

    private static String payload(String deliveryId, List<CustomerChargeLine> lines, BigDecimal loss, BigDecimal covered) {
        StringBuilder text = new StringBuilder(deliveryId).append('|').append(loss).append('|').append(covered);
        for (CustomerChargeLine line : lines) {
            text.append('\n').append(line.name()).append('|').append(line.method()).append('|')
                    .append(line.quantity()).append('|').append(line.unitPrice()).append('|')
                    .append(line.directAmount()).append('|').append(line.basis());
        }
        return text.toString();
    }

    private static String required(String deliveryId) {
        String id = deliveryId == null ? "" : deliveryId.trim();
        if (id.isEmpty()) {
            throw new BusinessException("交付草稿不存在");
        }
        return id;
    }
}
