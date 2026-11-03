package com.zhubao.erp.sampling.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.sampling.domain.SamplingDelivery;
import com.zhubao.erp.sampling.domain.SamplingDeliveryRepository;
import com.zhubao.erp.sampling.domain.SamplingFeeRepository;
import com.zhubao.erp.sampling.domain.SamplingOperationReport;
import com.zhubao.erp.sampling.domain.SamplingOperationRepository;
import com.zhubao.erp.sampling.domain.SamplingOrder;
import com.zhubao.erp.sampling.domain.SamplingOrderRepository;
import com.zhubao.erp.sampling.domain.SamplingOrderStatus;
import com.zhubao.erp.sampling.domain.SamplingOrderView;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.app.RefactorPermissionContract;
import com.zhubao.erp.security.domain.CustomerScopeRules;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 已报数量交完，并且每次样品交付都已确认样费或明确免收后，把打样单记为已完成。
 * 不写工资，也不把样费抵进大货。
 */
@Service
public class SamplingCompletionService {

    private final SamplingOrderRepository orders;
    private final SamplingOperationRepository operations;
    private final SamplingDeliveryRepository deliveries;
    private final SamplingFeeRepository fees;
    private final BusinessWorkUnit unit;
    private final PermissionGuard permissions;

    public SamplingCompletionService(SamplingOrderRepository orders, SamplingOperationRepository operations,
                                     SamplingDeliveryRepository deliveries, SamplingFeeRepository fees,
                                     BusinessWorkUnit unit, PermissionGuard permissions) {
        this.orders = orders;
        this.operations = operations;
        this.deliveries = deliveries;
        this.fees = fees;
        this.unit = unit;
        this.permissions = permissions;
    }

    public SamplingOrderView complete(String requestId, OperatorContext actor, String samplingCode) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.SAMPLING_MAINTAIN, actor);
        SamplingOrder order = requireVisible(actor, samplingCode);
        String code = order.samplingCode();
        unit.execute(new CommandCall(
                requestId, actor.operator(), "COMPLETE_SAMPLING_ORDER", "SAMPLING_ORDER", code, code
        ), session -> {
            SamplingOrder current = orders.find(code).orElseThrow(() -> new BusinessException("打样单不存在"));
            if (current.status() == SamplingOrderStatus.COMPLETED) {
                return code;
            }
            finish(current);
            orders.replace(new SamplingOrder(
                    current.samplingCode(), current.customerId(), current.customerName(), current.materialIntent(),
                    current.requirement(), current.ownerName(), SamplingOrderStatus.COMPLETED, current.version() + 1,
                    current.openedBy(), current.openedOn()
            ), current.version(), session);
            return code;
        });
        return view(orders.find(code).orElseThrow(() -> new BusinessException("打样单不存在")));
    }

    private SamplingOrderView view(SamplingOrder order) {
        return new SamplingOrderView(
                order.samplingCode(), order.reference().canonical(), order.customerId(), order.customerName(),
                order.materialIntent().name(), order.materialIntent().label(), order.requirement(), order.ownerName(),
                order.status().name(), order.status().label(), order.version(), order.openedBy(), order.openedOn());
    }

    private void finish(SamplingOrder order) {
        List<SamplingOperationReport> reports = operations.list(order.samplingCode()).stream()
                .filter(report -> "SAMPLE_MAKE".equals(report.stepId()))
                .toList();
        if (reports.isEmpty()) {
            throw new BusinessException("还没有选料与加工的实物报工，不能完结");
        }
        String unitName = reports.get(0).unit();
        if (reports.stream().anyMatch(report -> !unitName.equals(report.unit()))) {
            throw new BusinessException("报工单位不一致，不能完结");
        }
        BigDecimal reported = reports.stream().map(SamplingOperationReport::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<SamplingDelivery> shipped = deliveries.list(order.samplingCode());
        if (shipped.stream().anyMatch(delivery -> !unitName.equals(delivery.unit()))) {
            throw new BusinessException("交付单位必须与报工一致");
        }
        BigDecimal delivered = shipped.stream().map(SamplingDelivery::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (delivered.compareTo(reported) < 0) {
            throw new BusinessException("已报数量还没有交完，不能完结");
        }
        for (SamplingDelivery delivery : shipped) {
            if (fees.findByDelivery(delivery.deliveryId()).isEmpty()) {
                throw new BusinessException("还有样品交付没有确认样费或明确免收");
            }
        }
    }

    private SamplingOrder requireVisible(OperatorContext actor, String samplingCode) {
        SamplingOrder order = orders.find(samplingCode).orElseThrow(() -> new BusinessException("打样单不存在"));
        if (!CustomerScopeRules.allowed(actor, order.customerId(), order.customerName())) {
            throw new PermissionDeniedException("不能查看其他客户的打样单");
        }
        return order;
    }
}
