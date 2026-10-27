package com.zhubao.erp.finance.app;

import com.zhubao.erp.business.app.OrderDeliveryCompletionQuery;
import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.OrderDeliveryAssessment;
import com.zhubao.erp.business.domain.OrderDeliveryAssessment.LaborFact;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.ChargeUnit;
import com.zhubao.erp.finance.domain.FinanceRepository;
import com.zhubao.erp.finance.domain.OrderSettlement;
import com.zhubao.erp.finance.domain.OrderSettlementItem;
import com.zhubao.erp.finance.domain.OrderSettlementPosting;
import com.zhubao.erp.finance.domain.OrderSettlementRepository;
import com.zhubao.erp.finance.domain.OrderSettlementSource;
import com.zhubao.erp.finance.domain.Payable;
import com.zhubao.erp.finance.domain.PayrollLine;
import com.zhubao.erp.finance.domain.ReceivableLine;
import com.zhubao.erp.finance.domain.SettlementCommand;
import com.zhubao.erp.finance.domain.SettlementItemKind;
import com.zhubao.erp.finance.domain.SettlementStatus;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.AuthUserAccountRepository;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 交货后的客户加工费和员工工费。草稿不入账；确认时应收、工资和员工应付同一工作单元提交。
 */
@Service
public class OrderSettlementService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private final AtomicLong sequence = new AtomicLong();
    private final OrderDeliveryCompletionQuery deliveries;
    private final OrderSettlementRepository settlements;
    private final FinanceRepository finance;
    private final BusinessDocumentRepository documents;
    private final BusinessWorkUnit workUnit;
    private final AuthUserAccountRepository accounts;
    private final FinanceReadAuthorizer authorizer;

    public OrderSettlementService(
            OrderDeliveryCompletionQuery deliveries,
            OrderSettlementRepository settlements,
            FinanceRepository finance,
            BusinessDocumentRepository documents,
            BusinessWorkUnit workUnit,
            AuthUserAccountRepository accounts,
            FinanceReadAuthorizer authorizer
    ) {
        this.deliveries = deliveries;
        this.settlements = settlements;
        this.finance = finance;
        this.documents = documents;
        this.workUnit = workUnit;
        this.accounts = accounts;
        this.authorizer = authorizer == null ? new FinanceReadAuthorizer() : authorizer;
    }

    public OrderSettlement saveDraft(SettlementCommand command, OperatorContext actor, Set<String> permissions) {
        requireUnit();
        if (!settlements.policyEnabled()) {
            throw new BusinessException("交货后结单尚未启用");
        }
        Prepared prepared = prepare(command, actor, permissions, false);
        String result = workUnit.execute(new CommandCall(
                command.requestId(), actorId(actor), "ORDER_SETTLEMENT_DRAFT", "ORDER_SETTLEMENT", command.targetCode(), payload(command)
        ), session -> write(store(prepared, session).code()));
        return settlements.find(codeOf(result), null).orElseThrow(() -> new IllegalStateException("结单草稿保存后无法读取"));
    }

    public OrderSettlement confirm(SettlementCommand command, OperatorContext actor, Set<String> permissions) {
        requireUnit();
        if (!settlements.policyEnabled()) {
            throw new BusinessException("交货后结单尚未启用");
        }
        Prepared prepared = prepare(command, actor, permissions, true);
        String result = workUnit.execute(new CommandCall(
                command.requestId(), actorId(actor), "ORDER_SETTLEMENT_CONFIRM", "ORDER_SETTLEMENT", command.targetCode(), payload(command)
        ), session -> write(confirmInSession(prepared, actor, session)));
        return settlements.find(codeOf(result), null).orElseThrow(() -> new IllegalStateException("结单确认后无法读取"));
    }

    private String confirmInSession(Prepared prepared, OperatorContext actor, WorkSession session) {
        OrderSettlement draft = store(prepared, session);
        String month = YearMonth.now().toString();
        String now = FinanceTexts.now();
        List<OrderSettlementPosting> postings = new ArrayList<>();
        Map<String, PayableAccumulator> payables = new LinkedHashMap<>();
        for (Priced priced : prepared.priced()) {
            if (priced.item().kind() == SettlementItemKind.CUSTOMER) {
                if (priced.net().signum() > 0) {
                    String code = FinanceTexts.code("FIN-AR", sequence);
                    finance.saveReceivable(new ReceivableLine(
                            code, prepared.assessment().customerId(), prepared.assessment().customerName(), month,
                            "ORDER_SETTLEMENT", sourceKey(draft.code(), priced.item().lineNo()),
                            prepared.assessment().motherOrderCode(), prepared.assessment().targetCode(), priced.process(),
                            priced.quantity(), priced.unit(), priced.unitPrice(), priced.net(), "", priced.item().remark(),
                            actorId(actor), now
                    ), session);
                    postings.add(new OrderSettlementPosting(priced.item().lineNo(), OrderSettlementPosting.RECEIVABLE, code, priced.net()));
                }
                continue;
            }
            if (priced.net().signum() <= 0) {
                continue;
            }
            String payrollCode = FinanceTexts.code("FIN-WG", sequence);
            finance.savePayrollLine(new PayrollLine(
                    payrollCode, month, priced.payeeName(), prepared.assessment().targetCode(), priced.process(),
                    prepared.assessment().motherOrderCode(), prepared.assessment().customerId(), priced.quantity(),
                    priced.unit(), priced.unitPrice(), priced.gross(), priced.deduction(), priced.net(), true,
                    sourceKey(draft.code(), priced.item().lineNo()), PayrollLine.POST_DELIVERY, priced.item().payeeAccount()
            ), session);
            postings.add(new OrderSettlementPosting(priced.item().lineNo(), OrderSettlementPosting.PAYROLL, payrollCode, priced.net()));
            payables.computeIfAbsent(priced.item().payeeAccount(), key -> new PayableAccumulator(priced.payeeName()))
                    .add(priced.net());
        }
        int payableLine = 9000;
        for (Map.Entry<String, PayableAccumulator> entry : payables.entrySet()) {
            String payableCode = FinanceTexts.code("FIN-AP", sequence);
            String postingKey = "SETTLEMENT|" + draft.code() + "|" + entry.getKey();
            finance.savePayable(new Payable(
                    payableCode, "PAYROLL", month, entry.getValue().name, postingKey, prepared.assessment().customerId(),
                    prepared.assessment().motherOrderCode(), entry.getValue().amount, ZERO, actorId(actor), now,
                    entry.getKey(), "", postingKey
            ), session);
            postings.add(new OrderSettlementPosting(payableLine++, OrderSettlementPosting.PAYABLE, payableCode, entry.getValue().amount));
        }
        settlements.savePostings(draft.code(), postings, session);
        OrderSettlement confirmed = settlements.markConfirmed(draft.code(), draft.rowVersion(), actorId(actor), month, now, session);
        return confirmed.code() + "|customer=" + confirmed.customerNet().toPlainString() + "|employee=" + confirmed.employeeNet().toPlainString();
    }

    private OrderSettlement store(Prepared prepared, WorkSession session) {
        documents.lockMotherOrder(prepared.assessment().motherOrderCode(), session);
        settlements.lockTarget(prepared.command().targetType(), prepared.command().targetCode(), session);
        OrderDeliveryAssessment fresh = deliveries.assess(prepared.command().targetType(), prepared.command().targetCode(), session, true);
        if (!fresh.deliveryRevision().equals(prepared.assessment().deliveryRevision()) || !fresh.eligible()) {
            throw new BusinessException("交货事实已变化，请刷新后重新结单");
        }
        OrderSettlement header = header(prepared, fresh);
        return settlements.saveDraft(header, prepared.items(), sources(fresh), prepared.command().expectedVersion(), session);
    }

    private Prepared prepare(SettlementCommand command, OperatorContext actor, Set<String> permissions, boolean confirming) {
        if (actor == null || actor.dataScope() == null) {
            throw new PermissionDeniedException("请先登录后再结单");
        }
        OrderDeliveryAssessment assessment = deliveries.assess(command.targetType(), command.targetCode());
        if (!assessment.feeTarget()) {
            throw new BusinessException("拆单后请按子单分别结单，母单只汇总");
        }
        if (!assessment.eligible()) {
            throw new BusinessException("尚未交齐，不能填写工费");
        }
        if (!assessment.deliveryRevision().equals(command.deliveryRevision())) {
            throw new BusinessException("交货事实已变化，请刷新后重新结单");
        }
        authorizer.assertCustomer(actor, assessment.customerId(), assessment.customerName());
        List<Priced> priced = price(command, assessment, confirming);
        boolean customer = priced.stream().anyMatch(row -> row.item().kind() == SettlementItemKind.CUSTOMER) || command.customerWaived();
        boolean employee = priced.stream().anyMatch(row -> row.item().kind() == SettlementItemKind.EMPLOYEE) || command.noInternalLabor();
        assertPermission(permissions, customer, employee, confirming, actor);
        if (confirming) {
            assertComplete(command, assessment, priced);
        }
        BigDecimal customerNet = sum(priced, SettlementItemKind.CUSTOMER);
        BigDecimal employeeNet = sum(priced, SettlementItemKind.EMPLOYEE);
        if (customerNet.signum() < 0) {
            throw new BusinessException("客户加工费不能为负");
        }
        return new Prepared(command, assessment, priced, customerNet, employeeNet);
    }

    private void assertComplete(SettlementCommand command, OrderDeliveryAssessment assessment, List<Priced> priced) {
        boolean positiveCustomer = priced.stream().anyMatch(row -> row.item().kind() == SettlementItemKind.CUSTOMER && row.net().signum() > 0);
        if (command.customerWaived()) {
            if (command.waiveReason().isBlank()) {
                throw new BusinessException("免收必须填写原因");
            }
            if (positiveCustomer) {
                throw new BusinessException("已填写客户加工费时不能同时选择整单免收");
            }
        } else if (!positiveCustomer) {
            throw new BusinessException("请填写客户加工费；免收必须明确选择并写明原因");
        }
        if (command.noInternalLabor()) {
            if (command.noLaborReason().isBlank()) {
                throw new BusinessException("无内部计件工费必须填写原因");
            }
            if (priced.stream().anyMatch(row -> row.item().kind() == SettlementItemKind.EMPLOYEE && row.net().signum() > 0)) {
                throw new BusinessException("已填写员工工费时不能同时选择无内部计件");
            }
            return;
        }
        Set<String> covered = new LinkedHashSet<>();
        Map<String, BigDecimal> used = new LinkedHashMap<>();
        for (Priced row : priced) {
            if (row.item().kind() != SettlementItemKind.EMPLOYEE) {
                continue;
            }
            LaborFact fact = assessment.laborFacts().stream()
                    .filter(item -> item.sourceRef().equals(row.item().sourceRef()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("员工工费没有对应的作业事实"));
            if (fact.weightOnly() && "PIECE".equals(row.item().chargeMode())) {
                throw new BusinessException("补重量不能按件计酬");
            }
            covered.add(fact.sourceRef());
            if ("PIECE".equals(row.item().chargeMode())) {
                assertPieceShare(fact, row, used);
            } else if ("GRAM".equals(row.item().chargeMode())) {
                assertGramShare(fact, row, used);
            } else if (!"FIXED".equals(row.item().chargeMode()) && !"WAIVED".equals(row.item().chargeMode())) {
                throw new BusinessException("无法识别的计酬方式");
            }
        }
        for (LaborFact fact : assessment.laborFacts()) {
            if (fact.weightOnly()) {
                continue;
            }
            if (!covered.contains(fact.sourceRef())) {
                throw new BusinessException("作业 " + fact.processName() + " 尚未确认工费或免计");
            }
        }
    }

    /** 按件只和同一来源的件数份额比较，不拿重量或人数代替。 */
    private void assertPieceShare(LaborFact fact, Priced row, Map<String, BigDecimal> used) {
        if (fact.weightOnly()) {
            throw new BusinessException("补重量不能按件计酬");
        }
        if (!sameUnit(row.item().quantityUnit(), "件", "PIECE")) {
            throw new BusinessException("按件计酬的数量单位与件数不一致");
        }
        if (fact.quantity() == null) {
            throw new BusinessException("按件计酬缺少相同单位的作业件数");
        }
        String key = fact.sourceRef() + "|PIECE";
        BigDecimal next = used.getOrDefault(key, BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP)).add(row.quantity());
        if (next.compareTo(fact.quantity()) > 0) {
            throw new BusinessException("同一作业的计酬件数不能超过实际件数");
        }
        used.put(key, next);
    }

    /** 按克只和已记录的计费重量比较。没有实测重量时允许交货后明确确认，但不能按人数拆同一来源。 */
    private void assertGramShare(LaborFact fact, Priced row, Map<String, BigDecimal> used) {
        if (!sameUnit(row.item().quantityUnit(), "克", "GRAM")) {
            throw new BusinessException("按克计酬的数量单位与重量不一致");
        }
        String key = fact.sourceRef() + "|GRAM";
        BigDecimal measured = fact.measuredWeight();
        if (measured != null && measured.signum() > 0) {
            BigDecimal next = used.getOrDefault(key, BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP)).add(row.quantity());
            if (next.compareTo(measured) > 0) {
                throw new BusinessException("同一作业的计酬重量不能超过已记录的计费重量");
            }
            used.put(key, next);
            return;
        }
        if (row.item().remark() == null || row.item().remark().isBlank()) {
            throw new BusinessException("没有实测重量时，按克计酬必须写明计费重量依据");
        }
        if (used.containsKey(key)) {
            throw new BusinessException("没有个人实测重量时，不能把同一来源按克分给多人");
        }
        used.put(key, row.quantity());
    }

    private static boolean sameUnit(String actual, String chinese, String code) {
        String text = actual == null ? "" : actual.trim();
        return text.isEmpty() || chinese.equals(text) || code.equalsIgnoreCase(text);
    }

    private List<Priced> price(SettlementCommand command, OrderDeliveryAssessment assessment, boolean confirming) {
        Set<Integer> lines = new LinkedHashSet<>();
        Set<String> losses = new LinkedHashSet<>();
        List<Priced> priced = new ArrayList<>();
        for (OrderSettlementItem item : command.items()) {
            if (!lines.add(item.lineNo()) || item.lineNo() <= 0) {
                throw new BusinessException("结单行号重复或无效");
            }
            if (item.kind() != SettlementItemKind.CUSTOMER && item.kind() != SettlementItemKind.EMPLOYEE) {
                throw new BusinessException("结单行种类无效");
            }
            BigDecimal gross;
            BigDecimal deduction = ZERO;
            BigDecimal net;
            BigDecimal quantity;
            BigDecimal unitPrice;
            ChargeUnit unit;
            switch (item.chargeMode()) {
                case "WAIVED" -> {
                    if (item.remark() == null || item.remark().isBlank()) {
                        throw new BusinessException("免收或免计必须填写原因");
                    }
                    gross = ZERO;
                    net = ZERO;
                    quantity = BigDecimal.ONE.setScale(3, RoundingMode.HALF_UP);
                    unitPrice = ZERO.setScale(4, RoundingMode.HALF_UP);
                    unit = ChargeUnit.JOB;
                }
                case "FIXED" -> {
                    if (item.grossAmount() == null) {
                        throw new BusinessException("固定金额不能为空");
                    }
                    gross = FinanceTexts.money(item.grossAmount());
                    if (gross.signum() < 0) {
                        throw new BusinessException("金额不能为负");
                    }
                    if (item.deduction() != null && item.deduction().signum() != 0) {
                        throw new BusinessException("固定金额行不能再扣减，请把减免写成单独说明");
                    }
                    net = gross;
                    quantity = BigDecimal.ONE.setScale(3, RoundingMode.HALF_UP);
                    unitPrice = gross.setScale(4, RoundingMode.HALF_UP);
                    unit = ChargeUnit.JOB;
                }
                case "PIECE", "GRAM" -> {
                    if (item.quantity() == null || item.unitPrice() == null) {
                        throw new BusinessException("按件或按克必须填写数量和单价");
                    }
                    quantity = item.quantity().setScale(3, RoundingMode.HALF_UP);
                    unitPrice = item.unitPrice().setScale(4, RoundingMode.HALF_UP);
                    if (quantity.signum() <= 0 || unitPrice.signum() < 0) {
                        throw new BusinessException("数量必须大于零，单价不能为负");
                    }
                    gross = FinanceTexts.money(quantity.multiply(unitPrice));
                    deduction = FinanceTexts.money(item.deduction() == null ? ZERO : item.deduction());
                    if (item.kind() == SettlementItemKind.CUSTOMER && deduction.signum() != 0) {
                        throw new BusinessException("客户加工费不能在原行扣减");
                    }
                    if (deduction.signum() < 0 || deduction.compareTo(gross) > 0) {
                        throw new BusinessException("扣减不能超过该行工费毛额");
                    }
                    net = gross.subtract(deduction);
                    unit = "GRAM".equals(item.chargeMode()) ? ChargeUnit.GRAM : ChargeUnit.PIECE;
                }
                default -> throw new BusinessException("无法识别的计费方式");
            }
            if (!item.lossSourceCode().isBlank() && !losses.add(item.lossSourceCode())) {
                throw new BusinessException("同一损耗依据不能重复扣减");
            }
            String payeeName = "";
            if (item.kind() == SettlementItemKind.EMPLOYEE && net.signum() > 0) {
                if (accounts == null) {
                    throw new BusinessException("计酬账号无法核对");
                }
                AuthUserAccount account = accounts.findByUsername(item.payeeAccount()).orElse(null);
                if (account == null || !account.enabled()) {
                    throw new BusinessException("计酬人没有可用账号，不能按姓名自动归属");
                }
                payeeName = account.operator();
            }
            String process = assessment.laborFacts().stream()
                    .filter(fact -> fact.sourceRef().equals(item.sourceRef()))
                    .map(LaborFact::processName)
                    .findFirst()
                    .orElse(item.title());
            OrderSettlementItem normalized = new OrderSettlementItem(
                    item.lineNo(), item.kind(), item.title().isBlank() ? process : item.title(), item.sourceRef(),
                    item.chargeMode(), quantity, item.quantityUnit(), unit.name(), unitPrice, gross, deduction, net,
                    item.kind() == SettlementItemKind.EMPLOYEE ? item.payeeAccount() : "", payeeName, item.remark(),
                    item.lossSourceCode()
            );
            priced.add(new Priced(normalized, gross, deduction, net, quantity, unitPrice, unit, process, payeeName));
        }
        if (confirming && priced.isEmpty() && !command.customerWaived() && !command.noInternalLabor()) {
            throw new BusinessException("结单明细不能为空");
        }
        return priced;
    }

    private void assertPermission(Set<String> permissions, boolean customer, boolean employee, boolean confirming, OperatorContext actor) {
        Set<String> granted = permissions == null ? Set.of() : permissions;
        if (confirming) {
            if (actor.dataScope() != DataScope.ALL
                    || !granted.contains(PermissionCode.FINANCE_BILLING_POST.name())
                    || !granted.contains(PermissionCode.FINANCE_PAYROLL.name())) {
                throw new PermissionDeniedException("确认结单需要同时具有加工费入账和工资核算权限，并且具有全公司数据范围");
            }
            return;
        }
        if (customer && !granted.contains(PermissionCode.FINANCE_BILLING_POST.name())) {
            throw new PermissionDeniedException("没有加工费入账权限");
        }
        if (employee && (actor.dataScope() != DataScope.ALL || !granted.contains(PermissionCode.FINANCE_PAYROLL.name()))) {
            throw new PermissionDeniedException("员工工费只对具有工资核算权限的全公司账号开放");
        }
    }

    private OrderSettlement header(Prepared prepared, OrderDeliveryAssessment assessment) {
        String code = FinanceTexts.code("SET", sequence);
        return new OrderSettlement(
                code, code, 1, "", assessment.targetType(), assessment.targetCode(), assessment.motherOrderCode(),
                assessment.customerId(), assessment.customerName(), SettlementStatus.DRAFT, 0, assessment.deliveryRevision(),
                prepared.customerNet(), prepared.employeeNet(), prepared.command().customerWaived(), prepared.command().waiveReason(),
                prepared.command().noInternalLabor(), prepared.command().noLaborReason(), "", "", FinanceTexts.now(), "", "", "NONE"
        );
    }

    private List<OrderSettlementSource> sources(OrderDeliveryAssessment assessment) {
        List<OrderSettlementSource> rows = new ArrayList<>();
        int line = 1;
        for (var fact : assessment.quantities()) {
            rows.add(new OrderSettlementSource(line++, "QUANTITY", fact.materialCode(),
                    fact.role() + "|" + fact.unit() + "|" + fact.required() + "|" + fact.netShipped()));
        }
        for (LaborFact fact : assessment.laborFacts()) {
            rows.add(new OrderSettlementSource(line++, "LABOR", fact.sourceRef(),
                    fact.processName() + "|" + fact.operatorName() + "|" + fact.quantity() + "|" + fact.measuredWeight()));
        }
        return rows;
    }

    private String payload(SettlementCommand command) {
        StringBuilder text = new StringBuilder();
        text.append(command.targetType()).append('|').append(command.targetCode()).append('|')
                .append(command.expectedVersion()).append('|').append(command.deliveryRevision()).append('|')
                .append(command.customerWaived()).append('|').append(command.waiveReason()).append('|')
                .append(command.noInternalLabor()).append('|').append(command.noLaborReason());
        for (OrderSettlementItem item : command.items()) {
            text.append('\n').append(item.lineNo()).append('|').append(item.kind()).append('|').append(item.chargeMode())
                    .append('|').append(item.quantity()).append('|').append(item.unitPrice()).append('|')
                    .append(item.grossAmount()).append('|').append(item.deduction()).append('|')
                    .append(item.payeeAccount()).append('|').append(item.sourceRef()).append('|')
                    .append(item.lossSourceCode()).append('|').append(item.remark());
        }
        return text.toString();
    }

    private static BigDecimal sum(List<Priced> priced, SettlementItemKind kind) {
        return priced.stream().filter(row -> row.item().kind() == kind).map(Priced::net).reduce(ZERO, BigDecimal::add);
    }

    private static String sourceKey(String settlementCode, int lineNo) {
        return "SETTLEMENT|" + settlementCode + "|" + lineNo;
    }

    private static String actorId(OperatorContext actor) {
        String username = actor.username() == null ? "" : actor.username().trim();
        return username.isEmpty() ? actor.operator() : username;
    }

    private static String write(String code) {
        return code;
    }

    private static String codeOf(String result) {
        int split = result.indexOf("|customer=");
        return split < 0 ? result : result.substring(0, split);
    }

    private void requireUnit() {
        if (workUnit == null) {
            throw new BusinessException("结单回放尚未装配");
        }
    }

    private record Prepared(
            SettlementCommand command,
            OrderDeliveryAssessment assessment,
            List<Priced> priced,
            BigDecimal customerNet,
            BigDecimal employeeNet
    ) {
        private List<OrderSettlementItem> items() {
            return priced.stream().map(Priced::item).toList();
        }
    }

    private record Priced(
            OrderSettlementItem item,
            BigDecimal gross,
            BigDecimal deduction,
            BigDecimal net,
            BigDecimal quantity,
            BigDecimal unitPrice,
            ChargeUnit unit,
            String process,
            String payeeName
    ) {
    }

    private static final class PayableAccumulator {
        private final String name;
        private BigDecimal amount = ZERO;

        private PayableAccumulator(String name) {
            this.name = name;
        }

        private void add(BigDecimal net) {
            amount = amount.add(net);
        }
    }
}
