package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.ActivityBagRepository;
import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.business.domain.BusinessTargetRole;
import com.zhubao.erp.business.domain.NodePortionBalance;
import com.zhubao.erp.business.domain.NodePortionReader;
import com.zhubao.erp.business.domain.NodePortionWriter;
import com.zhubao.erp.business.domain.ProcessAvailableQuantity;
import com.zhubao.erp.business.domain.QuantityInput;
import com.zhubao.erp.business.domain.WholeBagReport;
import com.zhubao.erp.business.domain.WholeBagReportCommand;
import com.zhubao.erp.business.domain.WholeBagReportRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.domain.BusinessEvidenceLink;
import com.zhubao.erp.document.domain.BusinessEvidenceLinkRepository;
import com.zhubao.erp.document.domain.DocumentManagementRepository;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.document.domain.EvidenceCodes;
import com.zhubao.erp.document.domain.EvidenceLinkKey;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一次正常整袋报工。校验预期工序、本次照片、数量和袋版本，成功后推进袋版本。
 * 同一请求回放原批次。过期表单不能被解释为下一道。不要求先记 100%，不写库存，也不产生工资。
 */
@Service
public class WholeBagReportService {

    static final String ACTION = "WHOLE_BAG_REPORT";
    private static final String OPERATION = "whole_bag_report";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<Integer> PROGRESS = Set.of(0, 30, 60, 90, 100);

    private final ActivityBagRepository bags;
    private final ProcessAvailabilityQuery availability;
    private final NodePortionReader portions;
    private final NodePortionWriter portionWriter;
    private final WholeBagReportRepository reports;
    private final DocumentManagementRepository documents;
    private final BusinessEvidenceLinkRepository links;
    private final BusinessWorkUnit workUnit;
    private final Probe probe;
    private final ConcurrentHashMap<String, Object> bagLocks = new ConcurrentHashMap<>();

    @Autowired
    public WholeBagReportService(ActivityBagRepository bags, ProcessAvailabilityQuery availability,
                                 NodePortionReader portions, NodePortionWriter portionWriter,
                                 WholeBagReportRepository reports, DocumentManagementRepository documents,
                                 BusinessEvidenceLinkRepository links, BusinessWorkUnit workUnit) {
        this(bags, availability, portions, portionWriter, reports, documents, links, workUnit, Probe.NONE);
    }

    public WholeBagReportService(ActivityBagRepository bags, ProcessAvailabilityQuery availability,
                                 NodePortionReader portions, NodePortionWriter portionWriter,
                                 WholeBagReportRepository reports, DocumentManagementRepository documents,
                                 BusinessEvidenceLinkRepository links, BusinessWorkUnit workUnit, Probe probe) {
        this.bags = bags;
        this.availability = availability;
        this.portions = portions;
        this.portionWriter = portionWriter;
        this.reports = reports;
        this.documents = documents;
        this.links = links;
        this.workUnit = workUnit;
        this.probe = probe == null ? Probe.NONE : probe;
    }

    public WholeBagReport report(WholeBagReportCommand command, OperatorContext actor) {
        if (actor == null || actor.operator() == null || actor.operator().isBlank()) {
            throw new BusinessException("请先登录后再报工");
        }
        if (actor.dataScope() == DataScope.CUSTOMER_RANGE && actor.customerScope().isEmpty()) {
            throw new PermissionDeniedException("客户范围为空，不能报工");
        }
        if (command == null || command.requestId() == null || command.requestId().isBlank()) {
            throw new BusinessException("交工必须提供稳定请求编号");
        }
        if (workUnit == null) {
            throw new BusinessException("整袋报工回放尚未装配");
        }
        QuantityInput input = command.quantity();
        if (input == null) {
            throw new BusinessException("确认数量不能为空或为负");
        }
        if (input.source() == QuantityInput.Source.PHOTO_COUNT) {
            throw new BusinessException("拍照计数尚未接入，请改用手填数量");
        }
        if (command.progressPercent() != null && !PROGRESS.contains(command.progressPercent())) {
            throw new BusinessException("进度只能是 0%、30%、60%、90% 或 100%，也可以不填");
        }
        String bagId = text(command.bagId());
        synchronized (bagLocks.computeIfAbsent(bagId, ignored -> new Object())) {
            ActivityBag bag = bags.find(bagId).orElseThrow(() -> new BusinessException("袋不存在"));
            BusinessTargetReference reference = BusinessTargetReference.parse(bag.targetCode());
            if (reference.kind() != BusinessKind.PROCESSING) {
                throw new BusinessException("普通加工报工只能用于母单或子单");
            }
            String targetType = reference.role() == BusinessTargetRole.WORK_ORDER
                    ? EvidenceCodes.WORK_ORDER : EvidenceCodes.MOTHER_ORDER;
            String payload = bag.id() + "|" + command.bagVersion() + "|" + text(command.expectedStepId()) + "|"
                    + input.confirmedQuantity().toPlainString()
                    + "|MANUAL|" + text(command.attachmentCode()) + "|"
                    + (command.progressPercent() == null ? "" : command.progressPercent());
            String text = workUnit.execute(new CommandCall(
                    command.requestId(), actor.operator().trim(), ACTION, targetType, bag.targetCode(), payload
            ), session -> WholeBagReportText.write(execute(command, actor, bag.id(), targetType, input, session)));
            return WholeBagReportText.read(text);
        }
    }

    private WholeBagReport execute(WholeBagReportCommand command, OperatorContext actor, String bagId,
                                   String targetType, QuantityInput input, WorkSession session) {
        ActivityBag current = bags.lock(bagId, session).orElseThrow(() -> new BusinessException("袋不存在"));
        if (current.version() != command.bagVersion()) {
            throw new BusinessException("袋已被其他人修改，请刷新后重试");
        }
        String expectedStep = text(command.expectedStepId());
        if (expectedStep.isBlank()) {
            throw new BusinessException("请按当前工序提交");
        }
        ProcessAvailableQuantity available = availability.lookup(current.id(), actor);
        if (available.blockingReasons().contains("缺少生产报工权限")) {
            throw new PermissionDeniedException("缺少生产报工权限");
        }
        if (!available.actionable()) {
            throw new BusinessException(available.blockingReasons().get(0));
        }
        if (!expectedStep.equals(available.stepId())) {
            throw new BusinessException("当前工序已变化，请刷新后重试");
        }
        if (input.confirmedQuantity().compareTo(available.availableQuantity()) != 0) {
            throw new BusinessException("正常整袋报工必须一次提交当前可处理数量");
        }
        EvidenceAttachment attachment = documents.findAttachmentByCode(text(command.attachmentCode())).orElse(null);
        assertPhoto(attachment, current.targetCode(), targetType);
        if (links.findAll().stream().anyMatch(link -> attachment.code().equals(link.attachmentCode()))) {
            throw new BusinessException("这张照片已经用于其他业务，不能当作本次拍摄");
        }
        movePortion(current, available, input.confirmedQuantity(), session);
        String now = TIME.format(LocalDateTime.now());
        String batchCode = "WB" + UUID.randomUUID().toString().replace("-", "");
        WholeBagReport report = new WholeBagReport(
                batchCode, command.requestId().trim(), current.id(), current.version(), current.targetCode(),
                available.stepId(), input.confirmedQuantity(), attachment.code(), actor.operator().trim(),
                command.progressPercent(), now, actor.username().isBlank() ? null : actor.username());
        reports.save(report, session);
        links.insert(link(report, targetType), session);
        bags.save(new ActivityBag(
                current.id(), current.targetCode(), current.materialCode(), current.requirementVersion(),
                current.sourceKey(), current.parentId(), current.closed(), current.quantity(), current.weight(),
                current.version() + 1), current.version(), session);
        probe.afterPrepared();
        return report;
    }

    private void movePortion(ActivityBag bag, ProcessAvailableQuantity available, BigDecimal quantity, WorkSession session) {
        NodePortionBalance current = portions.find(bag.id(), available.stepId(), session).orElse(null);
        BigDecimal arrived = current == null ? quantity : current.arrived();
        BigDecimal handled = current == null ? BigDecimal.ZERO : current.handled();
        if (arrived.subtract(handled).compareTo(quantity) != 0) {
            throw new BusinessException("正常整袋报工必须一次提交当前可处理数量");
        }
        portionWriter.save(new NodePortionBalance(bag.id(), available.stepId(), arrived, handled.add(quantity)), session);
        if (available.nextStepId().isBlank()) {
            return;
        }
        NodePortionBalance next = portions.find(bag.id(), available.nextStepId(), session)
                .orElse(new NodePortionBalance(bag.id(), available.nextStepId(), BigDecimal.ZERO, BigDecimal.ZERO));
        portionWriter.save(new NodePortionBalance(
                bag.id(), available.nextStepId(), next.arrived().add(quantity), next.handled()), session);
    }

    private void assertPhoto(EvidenceAttachment attachment, String targetCode, String targetType) {
        if (attachment == null || attachment.status() == null || attachment.status().isBlank()
                || "已下线".equals(attachment.status()) || "待核对".equals(attachment.status())) {
            throw new BusinessException("没有有效的现场照片");
        }
        if (attachment.objectType() == null || attachment.objectType().isBlank()
                || EvidenceCodes.HISTORICAL.equals(attachment.objectType())) {
            throw new BusinessException("历史照片不能当作本次拍摄");
        }
        if (!targetCode.equals(attachment.businessCode()) || !targetType.equals(attachment.objectType())) {
            throw new BusinessException("照片不属于当前加工目标");
        }
        String media = attachment.mediaType() == null ? "" : attachment.mediaType().toLowerCase(Locale.ROOT);
        if (!"image/jpeg".equals(media) && !"image/png".equals(media)) {
            throw new BusinessException("报工照片必须是可用图片");
        }
    }

    private BusinessEvidenceLink link(WholeBagReport report, String targetType) {
        String stage = EvidenceCodes.PRODUCTION;
        String scene = "PROCESS";
        String hash = EvidenceLinkKey.hash(
                targetType, report.targetCode(), stage, scene, OPERATION, report.batchCode(),
                report.stepId(), "", report.attachmentCode());
        return new BusinessEvidenceLink(
                "EV" + UUID.randomUUID().toString().replace("-", ""),
                report.attachmentCode(),
                targetType,
                report.targetCode(),
                stage,
                scene,
                OPERATION,
                report.batchCode(),
                report.stepId(),
                "",
                report.operatorName(),
                report.createdAt(),
                hash);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    /** 测试在保存之后触发失败，用来核对工作单元会撤回报工、份额和证据。 */
    public interface Probe {
        Probe NONE = () -> {
        };

        void afterPrepared();
    }
}
