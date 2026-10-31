package com.zhubao.erp.goldwork.app;

import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.domain.MasterDataRepository;
import com.zhubao.erp.business.domain.ProcessStep;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.goldwork.domain.GoldworkMaterialIntent;
import com.zhubao.erp.goldwork.domain.GoldworkOrder;
import com.zhubao.erp.goldwork.domain.GoldworkOrderRepository;
import com.zhubao.erp.goldwork.domain.GoldworkOrderStatus;
import com.zhubao.erp.goldwork.domain.GoldworkOrderView;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.app.RefactorPermissionContract;
import com.zhubao.erp.security.domain.CustomerScopeRules;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 建立和查询独立金工单。不依赖打样单或普通加工母单。
 * 不记件数、重量、成色或费用。
 */
@Service
public class GoldworkOrderService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final MasterDataRepository customers;
    private final GoldworkOrderRepository orders;
    private final BusinessWorkUnit unit;
    private final PermissionGuard permissions;

    public GoldworkOrderService(MasterDataRepository customers, GoldworkOrderRepository orders,
                                BusinessWorkUnit unit, PermissionGuard permissions) {
        this.customers = customers;
        this.orders = orders;
        this.unit = unit;
        this.permissions = permissions;
    }

    public List<GoldworkOrderView> list(OperatorContext actor, Long customerId) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.GOLDWORK_VIEW, actor);
        assertScopeBound(actor);
        if (customerId != null && customerId <= 0) {
            throw new BusinessException("请选择客户");
        }
        if (customerId != null) {
            assertCustomerVisible(actor, requireCustomer(customerId));
        }
        return orders.list().stream()
                .filter(order -> customerId == null || customerId == order.customerId())
                .filter(order -> CustomerScopeRules.allowed(actor, order.customerId(), order.customerName()))
                .map(this::view)
                .toList();
    }

    public GoldworkOrderView open(String requestId, OperatorContext actor, Long customerId, String materialIntent,
                                  String requirement, String ownerName, String routeSteps,
                                  String motherOrderCode, String samplingCode) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.GOLDWORK_MAINTAIN, actor);
        assertScopeBound(actor);
        if (text(motherOrderCode).length() > 0) {
            throw new BusinessException("金工单不使用普通加工母单");
        }
        if (text(samplingCode).length() > 0) {
            throw new BusinessException("金工单不依赖打样单");
        }
        if (customerId == null || customerId <= 0) {
            throw new BusinessException("请选择客户");
        }
        GoldworkMaterialIntent intent = GoldworkMaterialIntent.parse(materialIntent);
        String requirementText = limited(requirement, "请填写金工要求", 500);
        String owner = limited(ownerName, "请填写负责人", 40);
        String route = GoldworkOrder.canonicalRoute(routeSteps);
        CustomerProfile customer = requireCustomer(customerId);
        assertCustomerVisible(actor, customer);
        String payload = customer.customerId() + "|" + intent.name() + "|" + mark(requirementText) + "|"
                + mark(owner) + "|" + route;
        String code = unit.execute(new CommandCall(
                requestId, actor.operator(), "OPEN_GOLDWORK_ORDER", "GOLDWORK_ORDER", "NEW", payload
        ), session -> insert(session, actor, customer, intent, requirementText, owner, route));
        return view(orders.find(code).orElseThrow(() -> new BusinessException("金工单不存在")));
    }

    private String insert(WorkSession session, OperatorContext actor, CustomerProfile customer,
                          GoldworkMaterialIntent intent, String requirement, String owner, String route) {
        CustomerProfile current = requireCustomer(customer.customerId());
        if (!current.enabled()) {
            throw new BusinessException("客户已停用");
        }
        LocalDate day = LocalDate.now(ZONE);
        List<String> existing = orders.lockDay(day, session);
        String code = BusinessTargetReference.nextGoldworkCode(day, existing, candidate -> orders.find(candidate).isPresent());
        orders.insert(new GoldworkOrder(
                code, current.customerId(), current.name(), intent, requirement, owner, route,
                GoldworkOrderStatus.OPEN, 1, actor.operator(), day.toString()
        ), session);
        return code;
    }

    private GoldworkOrderView view(GoldworkOrder order) {
        List<ProcessStep> route = order.route();
        return new GoldworkOrderView(
                order.goldworkCode(),
                order.reference().canonical(),
                order.customerId(),
                order.customerName(),
                order.materialIntent().name(),
                order.materialIntent().label(),
                order.requirement(),
                order.ownerName(),
                route.stream().map(ProcessStep::id).toList(),
                route.stream().map(ProcessStep::name).toList(),
                order.status().name(),
                order.status().label(),
                order.version(),
                order.openedBy(),
                order.openedOn()
        );
    }

    private CustomerProfile requireCustomer(long customerId) {
        return customers.findCustomer(customerId).orElseThrow(() -> new BusinessException("客户档案不存在"));
    }

    private static void assertScopeBound(OperatorContext actor) {
        if (actor.dataScope() == DataScope.CUSTOMER_RANGE
                && (actor.customerScope() == null || actor.customerScope().isEmpty())) {
            throw new PermissionDeniedException("客户范围账号没有绑定客户");
        }
    }

    private static void assertCustomerVisible(OperatorContext actor, CustomerProfile customer) {
        if (!CustomerScopeRules.allowed(actor, customer.customerId(), customer.name())) {
            throw new PermissionDeniedException("客户不在当前账号范围内");
        }
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String limited(String value, String message, int max) {
        String text = text(value);
        if (text.isEmpty()) {
            throw new BusinessException(message);
        }
        if (text.length() > max) {
            throw new BusinessException(message.replace("请填写", "") + "超过可保存长度");
        }
        return text;
    }

    private static String mark(String value) {
        return value.replace('|', '/');
    }
}
