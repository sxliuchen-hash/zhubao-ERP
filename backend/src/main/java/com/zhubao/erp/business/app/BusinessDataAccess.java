package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.AuthUserAccountRepository;
import com.zhubao.erp.security.domain.CustomerScopeRules;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/** 正式业务按真实单据归属、客户范围和岗位关联授权，不匹配展示文本中的关键字。 */
@Component
public class BusinessDataAccess {

    private final BusinessDocumentRepository documents;
    private final StockLedgerService stocks;
    private final AuthUserAccountRepository accounts;
    private com.zhubao.erp.inventory.domain.StockDocumentRepository stockDocuments;

    public BusinessDataAccess(BusinessDocumentRepository documents, StockLedgerService stocks) {
        this(documents, stocks, null);
    }

    public BusinessDataAccess(BusinessDocumentRepository documents, StockLedgerService stocks,
                              AuthUserAccountRepository accounts) {
        this.documents = documents;
        this.stocks = stocks;
        this.accounts = accounts;
    }

    @Autowired
    public BusinessDataAccess(BusinessDocumentRepository documents, StockLedgerService stocks, AuthUserAccountRepository accounts,
                              com.zhubao.erp.inventory.domain.StockDocumentRepository stockDocuments) {
        this(documents, stocks, accounts); this.stockDocuments = stockDocuments;
    }

    public static OperatorContext contextOf(AuthUserAccount account) {
        if (account == null) throw new PermissionDeniedException("请先登录后再操作业务");
        return new OperatorContext(account.operator(), account.role(), account.dataScope(), false, "",
                account.customerScope(), account.coverDuties(), account.username(), account.teamCode(),
                account.departmentCode());
    }

    public boolean canRead(OperatorContext context, String businessCode) {
        if (context == null || ownershipConflicts(text(businessCode))) return false;
        var receipt = stockDocuments == null ? null : stockDocuments.find(text(businessCode)).orElse(null);
        if (receipt != null) {
            if (!"PREP_RECEIPT".equals(receipt.kind()) || ownerOf(text(businessCode)) != null) return false;
            return DocumentVisibility.canSee(context, receipt.customerId(), receipt.customerName(), "inventory",
                    List.of(receipt.operatorName(), receipt.handoverPerson()), directory());
        }
        if (context.dataScope() == DataScope.ALL) return true;
        BusinessOwner owner = ownerOf(text(businessCode));
        return owner != null && visibleOwner(context, owner);
    }

    public boolean canRead(OperatorContext context, StockDimension dimension) {
        if (context == null || dimension == null) return false;
        if (context.dataScope() == DataScope.ALL) return true;
        MotherOrderRecord mother = documents.findMotherOrderByCode(text(dimension.motherOrderCode())).orElse(null);
        if (mother == null || !Objects.equals(mother.customerId(), dimension.customerId())) return false;
        return DocumentVisibility.canSee(context, dimension.customerId(), mother.customerName(), "inventory",
                participants(mother, text(dimension.workOrderCode())), directory());
    }

    public void assertCanRead(OperatorContext context, String businessCode) {
        if (!canRead(context, businessCode)) throw new PermissionDeniedException("单号不在当前账号的业务数据范围内");
    }

    /**
     * 通用标签只在单号已经是单据时核对客户范围。尚未建档的编码不读取客户事实。
     */
    public void assertCanReadIfStored(OperatorContext context, String businessCode) {
        String code = text(businessCode);
        if (code.isEmpty()) return;
        if (context == null) throw new PermissionDeniedException("请先登录后再打印");
        if (ownershipConflicts(code) || ownerOf(code) != null) assertCanRead(context, code);
    }

    public void assertCustomer(OperatorContext context, Long customerId, String customerName) {
        if (context == null || !customerVisible(context, customerId, customerName)) {
            throw new PermissionDeniedException("客户不在当前账号的授权范围内");
        }
    }

    /** 命令中的客户必须与真实母单一致，客户名称只从服务端读取。 */
    public void assertDocumentCustomer(OperatorContext context, Long customerId, String motherOrderCode) {
        MotherOrderRecord mother = documents.findMotherOrderByCode(text(motherOrderCode)).orElse(null);
        if (mother == null) {
            if (context == null || context.dataScope() != DataScope.ALL) {
                throw new PermissionDeniedException("无法确认单据的真实客户归属");
            }
            return;
        }
        if (!Objects.equals(mother.customerId(), customerId)) throw new PermissionDeniedException("请求客户与单据真实客户不一致");
        assertCanRead(context, mother.code());
    }

    /** 首次拆单尚无本人关联，写入岗位另行授权后按真实母单客户检查。 */
    public void assertDocumentCustomer(OperatorContext context, String motherOrderCode) {
        MotherOrderRecord mother = documents.findMotherOrderByCode(text(motherOrderCode))
                .orElseThrow(() -> new PermissionDeniedException("无法确认单据的真实客户归属"));
        if (!visibleOwner(context, new BusinessOwner(mother, mother.code(), "inbound"))) {
            throw new PermissionDeniedException("客户不在当前账号的授权范围内");
        }
    }

    private boolean visibleOwner(OperatorContext context, BusinessOwner owner) {
        return DocumentVisibility.canSee(context, owner.mother().customerId(), owner.mother().customerName(),
                owner.area(), participants(owner.mother(), owner.orderCode()), directory());
    }

    private List<AuthUserAccount> directory() {
        return accounts == null ? List.of() : accounts.findAll();
    }

    private List<String> participants(MotherOrderRecord mother, String orderCode) {
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        names.add(mother.operator());
        names.add(mother.receiverName());
        names.add(mother.unpackSigner());
        String code = orderCode == null || orderCode.isBlank() ? mother.code() : orderCode;
        documents.listProcessStationHandoffs(code).forEach(row -> names.add(row.operator()));
        documents.findQualityRequirementCheck(code).ifPresent(row -> names.add(row.inspector()));
        return names;
    }

    private boolean customerVisible(OperatorContext context, Long customerId, String customerName) {
        return CustomerScopeRules.allowed(context, customerId, customerName);
    }

    /** 子单与所属母单客户不一致，或同一单号同时落到不同客户时，不挑选其中一边。 */
    private boolean ownershipConflicts(String code) {
        if (code.isEmpty()) return false;
        ChildWorkOrderRecord child = documents.findChildWorkOrderByCode(code).orElse(null);
        MotherOrderRecord sameCodeMother = documents.findMotherOrderByCode(code).orElse(null);
        if (child != null && sameCodeMother != null
                && CustomerScopeRules.identityConflicts(child.customerId(), sameCodeMother.customerId())) {
            return true;
        }
        if (child == null) return false;
        MotherOrderRecord parent = documents.findMotherOrderByCode(text(child.motherOrderCode())).orElse(null);
        return parent != null && CustomerScopeRules.identityConflicts(child.customerId(), parent.customerId());
    }

    private boolean areaVisible(OperatorContext context, String area) {
        if (context.dataScope() == DataScope.CUSTOMER_RANGE) return true;
        if (context.dataScope() == DataScope.SELF) return false;
        return switch (area) {
            case "inbound", "inventory" -> context.role() == RoleCode.RECEIVER || context.role() == RoleCode.WAREHOUSE_KEEPER
                    || context.role() == RoleCode.BUSINESS_MANAGER;
            case "production" -> context.role() == RoleCode.WORKSHOP_LEADER || context.role() == RoleCode.BUSINESS_MANAGER;
            case "quality" -> context.role() == RoleCode.QUALITY_INSPECTOR || context.role() == RoleCode.BUSINESS_MANAGER;
            default -> false;
        };
    }

    private boolean related(OperatorContext context, MotherOrderRecord mother, String orderCode) {
        String operator = text(context.operator());
        if (operator.isEmpty()) return false;
        if (List.of(text(mother.operator()), text(mother.receiverName()), text(mother.unpackSigner())).contains(operator)) return true;
        String code = orderCode.isEmpty() ? mother.code() : orderCode;
        return documents.listProcessStationHandoffs(code).stream().anyMatch(row -> operator.equals(row.operator()))
                || documents.findQualityRequirementCheck(code).map(row -> operator.equals(row.inspector())).orElse(false);
    }

    private BusinessOwner ownerOf(String code) {
        if (code.isEmpty()) return null;
        MotherOrderRecord mother = documents.findMotherOrderByCode(code).orElse(null);
        if (mother != null) return new BusinessOwner(mother, code, "inbound");
        var child = documents.findChildWorkOrderByCode(code).orElse(null);
        if (child != null) return owner(child.motherOrderCode(), child.code(), "production");
        var outbound = documents.findOutboundOrderByCode(code).orElse(null);
        if (outbound != null) return owner(outbound.motherOrderCode(), text(outbound.workOrderCode()), "inventory");
        var check = documents.listAllQualityRequirementChecks().stream()
                .filter(row -> code.equals(row.qualityCaseCode()) || code.equals(row.reworkOrderCode())).findFirst().orElse(null);
        if (check != null) return owner(check.motherOrderCode(), check.orderCode(), "quality");
        // 兼容旧返工、售后等仅登记库存事件的单据，归属来自真实来源或目标维度。
        BusinessOwner resolved = null;
        for (var entry : stocks.ledgers()) {
            if (!code.equals(entry.documentCode())) continue;
            for (StockDimension dimension : java.util.stream.Stream.of(entry.from(), entry.to()).filter(Objects::nonNull).toList()) {
                BusinessOwner candidate = owner(dimension.motherOrderCode(), text(dimension.workOrderCode()), "inventory");
                if (candidate == null || !Objects.equals(candidate.mother().customerId(), dimension.customerId())) return null;
                // 同一业务号出现不同母单时不能选第一条归属替有限范围账号授权。
                if (resolved != null && !resolved.mother().code().equals(candidate.mother().code())) return null;
                if (resolved == null || !candidate.orderCode().isEmpty()) resolved = candidate;
            }
        }
        return resolved;
    }

    private BusinessOwner owner(String motherCode, String orderCode, String area) {
        return documents.findMotherOrderByCode(text(motherCode)).map(mother -> new BusinessOwner(mother, orderCode, area)).orElse(null);
    }

    private static String text(String value) { return value == null ? "" : value.trim(); }

    private record BusinessOwner(MotherOrderRecord mother, String orderCode, String area) { }
}
