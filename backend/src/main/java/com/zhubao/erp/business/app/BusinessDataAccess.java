package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/** 正式业务按真实单据归属、客户范围和岗位关联授权，不匹配展示文本中的关键字。 */
@Component
public class BusinessDataAccess {

    private final BusinessDocumentRepository documents;
    private final StockLedgerService stocks;

    public BusinessDataAccess(BusinessDocumentRepository documents, StockLedgerService stocks) {
        this.documents = documents;
        this.stocks = stocks;
    }

    public static OperatorContext contextOf(AuthUserAccount account) {
        if (account == null) throw new PermissionDeniedException("请先登录后再操作业务");
        return new OperatorContext(account.operator(), account.role(), account.dataScope(), false, "", account.customerScope());
    }

    public boolean canRead(OperatorContext context, String businessCode) {
        if (context == null) return false;
        if (context.dataScope() == DataScope.ALL) return true;
        BusinessOwner owner = ownerOf(text(businessCode));
        return owner != null && customerVisible(context, owner.mother().customerId(), owner.mother().customerName())
                && (areaVisible(context, owner.area()) || related(context, owner.mother(), owner.orderCode()));
    }

    public boolean canRead(OperatorContext context, StockDimension dimension) {
        if (context == null || dimension == null) return false;
        if (context.dataScope() == DataScope.ALL) return true;
        MotherOrderRecord mother = documents.findMotherOrderByCode(text(dimension.motherOrderCode())).orElse(null);
        if (mother == null || !Objects.equals(mother.customerId(), dimension.customerId())) return false;
        return customerVisible(context, dimension.customerId(), mother.customerName())
                && (areaVisible(context, "inventory") || related(context, mother, text(dimension.workOrderCode())));
    }

    public void assertCanRead(OperatorContext context, String businessCode) {
        if (!canRead(context, businessCode)) throw new PermissionDeniedException("单号不在当前账号的业务数据范围内");
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
        assertCustomer(context, mother.customerId(), mother.customerName());
    }

    /** 首次拆单尚无本人关联，写入岗位另行授权后按真实母单客户检查。 */
    public void assertDocumentCustomer(OperatorContext context, String motherOrderCode) {
        MotherOrderRecord mother = documents.findMotherOrderByCode(text(motherOrderCode))
                .orElseThrow(() -> new PermissionDeniedException("无法确认单据的真实客户归属"));
        assertCustomer(context, mother.customerId(), mother.customerName());
    }

    private boolean customerVisible(OperatorContext context, Long customerId, String customerName) {
        if (context.dataScope() == DataScope.ALL) return true;
        if (context.customerScope().isEmpty()) return context.dataScope() != DataScope.CUSTOMER_RANGE;
        return context.customerScope().stream().map(BusinessDataAccess::text).anyMatch(value ->
                !value.isEmpty() && (value.equals(text(customerName)) || customerId != null
                        && (value.equals(customerId.toString()) || value.equals("客户#" + customerId))));
    }

    private boolean areaVisible(OperatorContext context, String area) {
        if (context.dataScope() == DataScope.CUSTOMER_RANGE) return true;
        if (context.dataScope() == DataScope.SELF) return false;
        return switch (area) {
            case "inbound", "inventory" -> context.role() == RoleCode.RECEIVER || context.role() == RoleCode.WAREHOUSE_KEEPER;
            case "production" -> context.role() == RoleCode.WORKSHOP_LEADER;
            case "quality" -> context.role() == RoleCode.QUALITY_INSPECTOR;
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
