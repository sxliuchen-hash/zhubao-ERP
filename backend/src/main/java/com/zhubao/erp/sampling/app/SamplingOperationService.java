package com.zhubao.erp.sampling.app;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.ProcessCatalog;
import com.zhubao.erp.business.domain.ProcessStep;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.domain.DocumentManagementRepository;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.document.domain.EvidenceCodes;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.sampling.domain.SamplingMakingChoice;
import com.zhubao.erp.sampling.domain.SamplingOperationReport;
import com.zhubao.erp.sampling.domain.SamplingOperationRepository;
import com.zhubao.erp.sampling.domain.SamplingOperationView;
import com.zhubao.erp.sampling.domain.SamplingOrder;
import com.zhubao.erp.sampling.domain.SamplingOrderRepository;
import com.zhubao.erp.sampling.domain.SamplingVersionDecisionKind;
import com.zhubao.erp.sampling.domain.SamplingVersionKind;
import com.zhubao.erp.sampling.domain.SamplingVersionRepository;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.app.RefactorPermissionContract;
import com.zhubao.erp.security.domain.ConfiguredProcessAccess;
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
 * 按打样自己的节点报工。不强制普通 15 道工序，也不推进相关大货。
 * 雕刻和车型可以同时选择。
 */
@Service
public class SamplingOperationService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final SamplingOrderRepository orders;
    private final SamplingVersionRepository versions;
    private final SamplingOperationRepository reports;
    private final DocumentManagementRepository documents;
    private final BusinessWorkUnit unit;
    private final PermissionGuard permissions;

    public SamplingOperationService(SamplingOrderRepository orders, SamplingVersionRepository versions,
                                    SamplingOperationRepository reports, DocumentManagementRepository documents,
                                    BusinessWorkUnit unit, PermissionGuard permissions) {
        this.orders = orders;
        this.versions = versions;
        this.reports = reports;
        this.documents = documents;
        this.unit = unit;
        this.permissions = permissions;
    }

    public List<SamplingOperationView> list(OperatorContext actor, String samplingCode) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.SAMPLING_VIEW, actor);
        SamplingOrder order = requireVisible(actor, samplingCode);
        return reports.list(order.samplingCode()).stream().map(this::view).toList();
    }

    public SamplingOperationView report(String requestId, OperatorContext actor, String samplingCode, String stepId,
                                        String versionKind, Integer versionNo, List<String> makingChoices,
                                        BigDecimal quantity, String unitName, String attachmentCode) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.SAMPLING_MAINTAIN, actor);
        SamplingOrder order = requireVisible(actor, samplingCode);
        ProcessStep step = samplingStep(stepId);
        ConfiguredProcessAccess.assertStep(actor, step);
        SamplingVersionKind kind = SamplingVersionKind.parse(versionKind);
        if (versionNo == null || versionNo < 1) {
            throw new BusinessException("请选择已确认版本");
        }
        SamplingMakingChoice choice = choice(step, makingChoices);
        BigDecimal counted = positive(quantity);
        String unitText = required(unitName, "请填写单位");
        String photo = required(attachmentCode, "没有有效的现场照片");
        String payload = step.id() + "|" + kind.name() + "|" + versionNo + "|"
                + (choice == null ? "" : choice.name()) + "|" + counted.toPlainString() + "|" + unitText + "|" + photo;
        String reportId = unit.execute(new CommandCall(
                requestId, actor.operator(), "REPORT_SAMPLING_OPERATION", "SAMPLING_OPERATION", order.samplingCode(), payload
        ), session -> write(session, actor, order, step, kind, versionNo, choice, counted, unitText, photo));
        return view(reports.find(reportId).orElseThrow(() -> new BusinessException("报工不存在")));
    }

    private String write(WorkSession session, OperatorContext actor, SamplingOrder order, ProcessStep step,
                         SamplingVersionKind kind, int versionNo, SamplingMakingChoice choice, BigDecimal quantity,
                         String unitName, String attachmentCode) {
        if (order.status() == com.zhubao.erp.sampling.domain.SamplingOrderStatus.COMPLETED) {
            throw new BusinessException("打样单已经完结，不能再报工");
        }
        if (versions.find(order.samplingCode(), kind, versionNo).isEmpty()) {
            throw new BusinessException("版本不存在");
        }
        boolean confirmed = versions.findDecision(order.samplingCode(), kind, versionNo)
                .filter(decision -> decision.decision() == SamplingVersionDecisionKind.CONFIRMED)
                .isPresent();
        if (!confirmed) {
            throw new BusinessException("版本与已确认版本不符");
        }
        EvidenceAttachment attachment = documents.findAttachmentByCode(attachmentCode).orElse(null);
        assertPhoto(attachment, order.samplingCode());
        if (reports.photoUsed(attachment.code(), session)) {
            throw new BusinessException("这张照片已经用于其他业务，不能当作本次拍摄");
        }
        String reportId = "SR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        reports.insert(new SamplingOperationReport(
                reportId, order.samplingCode(), step.id(), kind, versionNo, choice, quantity, unitName,
                attachment.code(), actor.operator(), LocalDate.now(ZONE).toString()
        ), session);
        return reportId;
    }

    private SamplingOperationView view(SamplingOperationReport report) {
        ProcessStep step = samplingStep(report.stepId());
        return new SamplingOperationView(
                report.reportId(), report.samplingCode(), report.stepId(), step.name(),
                report.versionKind().name(), report.versionNo(),
                report.makingChoice() == null ? "" : report.makingChoice().name(),
                report.makingChoice() == null ? "" : report.makingChoice().label(),
                report.quantity(), report.unit(), report.attachmentCode(), report.workerName(), report.reportedOn()
        );
    }

    private static ProcessStep samplingStep(String stepId) {
        try {
            ProcessStep step = ProcessCatalog.require(stepId);
            if (step.kind() != BusinessKind.SAMPLING) {
                throw new BusinessException("打样不使用普通加工工序");
            }
            return step;
        } catch (BusinessException ex) {
            if ("打样不使用普通加工工序".equals(ex.getMessage())) {
                throw ex;
            }
            throw new BusinessException("打样不使用普通加工工序");
        }
    }

    private static SamplingMakingChoice choice(ProcessStep step, List<String> makingChoices) {
        List<String> choices = makingChoices == null ? List.of() : makingChoices.stream()
                .map(item -> item == null ? "" : item.trim())
                .filter(item -> !item.isEmpty())
                .toList();
        SamplingMakingChoice choice = combinedChoice(choices);
        if (choice != null && !"SAMPLE_MAKE".equals(step.id())) {
            throw new BusinessException("该节点不能选择雕刻或车型");
        }
        return choice;
    }

    private static SamplingMakingChoice combinedChoice(List<String> choices) {
        if (choices.isEmpty()) {
            return null;
        }
        if (choices.size() == 1) {
            return SamplingMakingChoice.parse(choices.get(0));
        }
        if (choices.size() == 2 && choices.contains("CARVE") && choices.contains("SHAPE")) {
            return SamplingMakingChoice.CARVE_AND_SHAPE;
        }
        throw new BusinessException("请选择雕刻、车型，或两者同时");
    }

    private static void assertPhoto(EvidenceAttachment attachment, String samplingCode) {
        if (attachment == null || attachment.status() == null || attachment.status().isBlank()
                || "已下线".equals(attachment.status()) || "待核对".equals(attachment.status())) {
            throw new BusinessException("没有有效的现场照片");
        }
        if (attachment.objectType() == null || attachment.objectType().isBlank()
                || EvidenceCodes.HISTORICAL.equals(attachment.objectType())) {
            throw new BusinessException("历史照片不能当作本次拍摄");
        }
        if (!samplingCode.equals(attachment.businessCode()) || !"SAMPLING".equals(attachment.objectType())) {
            throw new BusinessException("照片不属于当前打样单");
        }
        String media = attachment.mediaType() == null ? "" : attachment.mediaType().toLowerCase(Locale.ROOT);
        if (!"image/jpeg".equals(media) && !"image/png".equals(media)) {
            throw new BusinessException("报工照片必须是可用图片");
        }
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
            throw new BusinessException("请填写本次数量");
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
}
