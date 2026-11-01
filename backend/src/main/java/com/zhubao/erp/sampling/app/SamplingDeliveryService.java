package com.zhubao.erp.sampling.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.sampling.domain.SamplingDelivery;
import com.zhubao.erp.sampling.domain.SamplingDeliveryRepository;
import com.zhubao.erp.sampling.domain.SamplingDeliveryView;
import com.zhubao.erp.sampling.domain.SamplingMaterialRepository;
import com.zhubao.erp.sampling.domain.SamplingMaterialUse;
import com.zhubao.erp.sampling.domain.SamplingOperationReport;
import com.zhubao.erp.sampling.domain.SamplingOperationRepository;
import com.zhubao.erp.sampling.domain.SamplingOrder;
import com.zhubao.erp.sampling.domain.SamplingOrderRepository;
import com.zhubao.erp.sampling.domain.SamplingVersionDecisionKind;
import com.zhubao.erp.sampling.domain.SamplingVersionKind;
import com.zhubao.erp.sampling.domain.SamplingVersionRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 实物样品按选料与加工的报工份额分次交付。设计、建模及确认流转不叠加成实物数量。
 * 不写普通发货，也不记样费。
 */
@Service
public class SamplingDeliveryService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final SamplingOrderRepository orders;
    private final SamplingVersionRepository versions;
    private final SamplingOperationRepository operations;
    private final SamplingMaterialRepository materials;
    private final SamplingDeliveryRepository deliveries;
    private final BusinessWorkUnit unit;
    private final PermissionGuard permissions;

    public SamplingDeliveryService(SamplingOrderRepository orders, SamplingVersionRepository versions,
                                   SamplingOperationRepository operations, SamplingMaterialRepository materials,
                                   SamplingDeliveryRepository deliveries, BusinessWorkUnit unit, PermissionGuard permissions) {
        this.orders = orders;
        this.versions = versions;
        this.operations = operations;
        this.materials = materials;
        this.deliveries = deliveries;
        this.unit = unit;
        this.permissions = permissions;
    }

    public List<SamplingDeliveryView> list(OperatorContext actor, String samplingCode) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.SAMPLING_VIEW, actor);
        SamplingOrder order = requireVisible(actor, samplingCode);
        return deliveries.list(order.samplingCode()).stream().map(delivery -> view(order, delivery)).toList();
    }

    public SamplingDeliveryView deliver(String requestId, OperatorContext actor, String samplingCode, String versionKind,
                                        Integer versionNo, String materialUseId, BigDecimal quantity, String unitName,
                                        String credentialCode) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.SAMPLING_MAINTAIN, actor);
        SamplingOrder order = requireVisible(actor, samplingCode);
        SamplingVersionKind kind = SamplingVersionKind.parse(versionKind);
        if (versionNo == null || versionNo < 1) {
            throw new BusinessException("请选择已确认版本");
        }
        BigDecimal counted = positive(quantity);
        String unitText = required(unitName, "请填写单位");
        String credential = required(credentialCode, "请填写交付凭据");
        String payload = kind.name() + "|" + versionNo + "|" + mark(materialUseId) + "|"
                + counted.toPlainString() + "|" + unitText + "|" + mark(credential);
        String deliveryId = unit.execute(new CommandCall(
                requestId, actor.operator(), "DELIVER_SAMPLING", "SAMPLING_DELIVERY", order.samplingCode(), payload
        ), session -> write(session, order, kind, versionNo, materialUseId, counted, unitText, credential));
        SamplingOrder current = orders.find(order.samplingCode()).orElseThrow(() -> new BusinessException("打样单不存在"));
        return view(current, deliveries.find(deliveryId).orElseThrow(() -> new BusinessException("交付不存在")));
    }

    private String write(WorkSession session, SamplingOrder order, SamplingVersionKind kind, int versionNo,
                         String materialUseId, BigDecimal quantity, String unitName, String credentialCode) {
        if (versions.find(order.samplingCode(), kind, versionNo).isEmpty()) {
            throw new BusinessException("版本不存在");
        }
        boolean confirmed = versions.findDecision(order.samplingCode(), kind, versionNo)
                .filter(decision -> decision.decision() == SamplingVersionDecisionKind.CONFIRMED)
                .isPresent();
        if (!confirmed) {
            throw new BusinessException("版本与已确认版本不符");
        }
        List<SamplingOperationReport> reports = deliverableReports(order.samplingCode(), kind, versionNo);
        if (reports.isEmpty()) {
            throw new BusinessException("还没有选料与加工的实物报工，不能交付样品");
        }
        String unit = reports.get(0).unit();
        if (reports.stream().anyMatch(report -> !unit.equals(report.unit())) || !unit.equals(unitName)) {
            throw new BusinessException("交付单位必须与报工一致");
        }
        BigDecimal reported = reports.stream().map(SamplingOperationReport::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal already = deliveries.lockVersion(order.samplingCode(), kind, versionNo, session).stream()
                .map(SamplingDelivery::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (already.add(quantity).compareTo(reported) > 0) {
            throw new BusinessException("交付数量超过已报工份额");
        }
        String source = materialUseId == null ? "" : materialUseId.trim();
        if (!source.isEmpty()) {
            SamplingMaterialUse use = materials.find(source).orElseThrow(() -> new BusinessException("领用不存在"));
            if (!use.samplingCode().equals(order.samplingCode())) {
                throw new BusinessException("领用不属于这张打样单");
            }
        }
        String deliveryId = "SD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        deliveries.insert(new SamplingDelivery(
                deliveryId, order.samplingCode(), kind, versionNo, source, quantity, unitName, credentialCode,
                LocalDate.now(ZONE).toString()
        ), session);
        return deliveryId;
    }

    private SamplingDeliveryView view(SamplingOrder order, SamplingDelivery delivery) {
        List<SamplingOperationReport> reports = deliverableReports(order.samplingCode(), delivery.versionKind(), delivery.versionNo());
        BigDecimal reported = reports.stream()
                .map(SamplingOperationReport::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal delivered = deliveries.list(order.samplingCode()).stream()
                .filter(item -> item.versionKind() == delivery.versionKind() && item.versionNo() == delivery.versionNo())
                .map(SamplingDelivery::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String owner = "";
        if (!delivery.materialUseId().isEmpty()) {
            owner = materials.find(delivery.materialUseId()).map(use -> use.owner().name()).orElse("");
        }
        return new SamplingDeliveryView(
                delivery.deliveryId(), delivery.samplingCode(), delivery.versionKind().name(), delivery.versionNo(),
                delivery.materialUseId(), owner, delivery.quantity(), delivery.unit(), delivery.credentialCode(),
                delivery.deliveredOn(), reports.isEmpty() || reported.compareTo(delivered) < 0 ? null : reported.subtract(delivered),
                false, order.status().name(), "UNCONFIRMED"
        );
    }

    private List<SamplingOperationReport> deliverableReports(String samplingCode, SamplingVersionKind kind, int versionNo) {
        return operations.list(samplingCode).stream()
                .filter(report -> report.versionKind() == kind && report.versionNo() == versionNo && "SAMPLE_MAKE".equals(report.stepId()))
                .toList();
    }

    private SamplingOrder requireVisible(OperatorContext actor, String samplingCode) {
        if (actor.dataScope() == DataScope.CUSTOMER_RANGE
                && (actor.customerScope() == null || actor.customerScope().isEmpty())) {
            throw new PermissionDeniedException("客户范围账号没有绑定客户");
        }
        SamplingOrder order = orders.find(samplingCode == null ? "" : samplingCode.trim())
                .orElseThrow(() -> new BusinessException("打样单不存在"));
        if (!CustomerScopeRules.allowed(actor, order.customerId(), order.customerName())) {
            throw new PermissionDeniedException("客户不在当前账号范围内");
        }
        return order;
    }

    private static BigDecimal positive(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessException("交付数量必须大于零");
        }
        return StockMeasurement.processingScale(quantity);
    }

    private static String required(String value, String message) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw new BusinessException(message);
        }
        return text;
    }

    private static String mark(String value) {
        return value == null ? "" : value.trim().replace('|', '/');
    }
}
