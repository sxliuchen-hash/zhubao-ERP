package com.zhubao.erp.document.app;

import com.zhubao.erp.business.app.BusinessDataAccess;
import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.OrderChange;
import com.zhubao.erp.business.domain.OrderChangeRepository;
import com.zhubao.erp.business.domain.OutboundOrderRecord;
import com.zhubao.erp.business.domain.ProcessStationHandoff;
import com.zhubao.erp.business.domain.QualityRequirementCheck;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.domain.BindEvidenceCommand;
import com.zhubao.erp.document.domain.BusinessEvidenceLink;
import com.zhubao.erp.document.domain.BusinessEvidenceLinkPage;
import com.zhubao.erp.document.domain.BusinessEvidenceLinkRepository;
import com.zhubao.erp.document.domain.DocumentAuditEvent;
import com.zhubao.erp.document.domain.DocumentAttachmentPage;
import com.zhubao.erp.document.domain.DocumentManagementRepository;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.document.domain.EvidenceBoard;
import com.zhubao.erp.document.domain.EvidenceCodes;
import com.zhubao.erp.document.domain.EvidenceLinkKey;
import com.zhubao.erp.document.domain.EvidenceLinkPage;
import com.zhubao.erp.document.domain.EvidenceLinkView;
import com.zhubao.erp.document.domain.EvidencePageFilter;
import com.zhubao.erp.document.domain.HistoricalEvidencePage;
import com.zhubao.erp.document.domain.HistoricalEvidenceView;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.scan.domain.OperationProgressRecord;
import com.zhubao.erp.scan.domain.OperationProgressRepository;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 把已落盘附件引用到真实单据上的一次操作。不报工、不判定质检、不扣库存。
 */
@Service
public class BusinessEvidenceService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String RETIRED = "已下线";

    private final DocumentManagementRepository documents;
    private final BusinessEvidenceLinkRepository links;
    private final BusinessDocumentRepository businessDocuments;
    private final StockLedgerService stocks;
    private final DocumentManagementService documentManagementService;
    private final BusinessDataAccess dataAccess;
    private final PermissionGuard permissionGuard;
    private final OperationProgressRepository progressRecords;
    private final AtomicInteger sequence = new AtomicInteger();
    private OrderChangeRepository orderChanges;

    public BusinessEvidenceService(
            DocumentManagementRepository documents,
            BusinessEvidenceLinkRepository links,
            BusinessDocumentRepository businessDocuments,
            StockLedgerService stocks,
            DocumentManagementService documentManagementService,
            BusinessDataAccess dataAccess,
            PermissionGuard permissionGuard
    ) {
        this(documents, links, businessDocuments, stocks, documentManagementService, dataAccess, permissionGuard, null);
    }

    @Autowired
    public BusinessEvidenceService(
            DocumentManagementRepository documents,
            BusinessEvidenceLinkRepository links,
            BusinessDocumentRepository businessDocuments,
            StockLedgerService stocks,
            DocumentManagementService documentManagementService,
            BusinessDataAccess dataAccess,
            PermissionGuard permissionGuard,
            OperationProgressRepository progressRecords
    ) {
        this.documents = documents;
        this.links = links;
        this.businessDocuments = businessDocuments;
        this.stocks = stocks;
        this.documentManagementService = documentManagementService;
        this.dataAccess = dataAccess;
        this.permissionGuard = permissionGuard;
        this.progressRecords = progressRecords;
    }

    @Autowired(required = false)
    public void setOrderChanges(OrderChangeRepository orderChanges) {
        this.orderChanges = orderChanges;
    }

    public BusinessEvidenceLink bind(BindEvidenceCommand command, OperatorContext operator) {
        if (operator == null || operator.operator() == null || operator.operator().isBlank()) {
            throw new BusinessException("请先登录后再关联证据");
        }
        String attachmentCode = required(command.attachmentCode(), "附件编号不能为空");
        String targetType = requiredObjectType(command.targetType());
        String targetCode = required(command.targetCode(), "单据编号不能为空");
        String stage = requiredStage(command.stage());
        String scene = requiredScene(command.scene());
        String operationType = required(command.operationType(), "操作类型不能为空");
        String operationCode = required(command.operationCode(), "操作编号不能为空");
        String processName = EvidenceLinkKey.text(command.processName());
        String clauseNo = EvidenceLinkKey.text(command.clauseNo());
        checkLength(targetType, EvidenceLinkKey.TARGET_TYPE_MAX, "单据类型");
        checkLength(targetCode, EvidenceLinkKey.TARGET_CODE_MAX, "单据编号");
        checkLength(stage, EvidenceLinkKey.STAGE_MAX, "业务阶段");
        checkLength(scene, EvidenceLinkKey.SCENE_MAX, "证据场景");
        checkLength(operationType, EvidenceLinkKey.OPERATION_TYPE_MAX, "操作类型");
        checkLength(operationCode, EvidenceLinkKey.OPERATION_CODE_MAX, "操作编号");
        checkLength(processName, EvidenceLinkKey.PROCESS_MAX, "工序");
        checkLength(clauseNo, EvidenceLinkKey.CLAUSE_MAX, "条款号");
        checkLength(attachmentCode, EvidenceLinkKey.ATTACHMENT_MAX, "附件编号");

        permissionGuard.assertAllowed(PermissionCode.DOCUMENT_CAPTURE, operator);
        permissionGuard.assertAllowed(permissionFor(stage, operationType), operator);
        assertReadable(operator, targetCode);

        EvidenceAttachment attachment = documents.findAttachmentByCode(attachmentCode)
                .orElse(null);
        if (attachment == null || !canReadQuietly(operator, attachment.businessCode())) {
            throw new PermissionDeniedException("单号不在当前账号的业务数据范围内");
        }
        if (RETIRED.equals(attachment.status())) {
            throw new BusinessException("附件已下线，不能再绑定：" + attachmentCode);
        }
        if ("待核对".equals(attachment.status())) {
            throw new BusinessException("附件待核对，不能绑定：" + attachmentCode);
        }
        String origin = EvidenceLinkKey.text(attachment.objectType());
        if (origin.isEmpty()) {
            throw new BusinessException("旧附件来源不确定，只能回看，不能绑定");
        }
        if (!origin.equals(targetType) || !targetCode.equals(attachment.businessCode())) {
            throw new BusinessException("附件不属于当前单据");
        }
        assertOperation(stage, operationType, operationCode, processName, clauseNo, targetType, targetCode);

        String hash = EvidenceLinkKey.hash(
                targetType, targetCode, stage, scene, operationType, operationCode, processName, clauseNo, attachmentCode
        );
        BusinessEvidenceLink candidate = new BusinessEvidenceLink(
                nextLinkCode(),
                attachmentCode,
                targetType,
                targetCode,
                stage,
                scene,
                operationType,
                operationCode,
                processName,
                clauseNo,
                operator.operator().trim(),
                LocalDateTime.now().format(DATE_TIME),
                hash
        );
        Optional<BusinessEvidenceLink> existing = links.findByKeyHash(hash);
        if (existing.isPresent()) {
            assertSameDimensions(existing.get(), candidate);
            return existing.get();
        }
        try {
            links.insert(candidate);
        } catch (RuntimeException ex) {
            BusinessEvidenceLink again = links.findByTarget(targetType, targetCode).stream()
                    .filter(row -> EvidenceLinkKey.sameDimensions(row, candidate))
                    .findFirst()
                    .orElse(null);
            if (again == null) {
                throw ex;
            }
            return again;
        }
        documents.saveAuditEvent(new DocumentAuditEvent(
                candidate.linkedAt(),
                "DOC-AUD-LINK-" + candidate.linkCode(),
                attachment.documentCode(),
                "绑定证据",
                candidate.operatorName(),
                "引用 " + attachmentCode + " 到 " + stage + "/" + scene + "，操作 " + operationType + " " + operationCode,
                "green"
        ));
        return candidate;
    }

    public EvidenceBoard query(
            String targetType,
            String targetCode,
            String stage,
            String operationType,
            String operationCode,
            OperatorContext operator
    ) {
        String type = requiredObjectType(targetType);
        String code = required(targetCode, "单据编号不能为空");
        if (operator == null || operator.operator() == null || operator.operator().isBlank()) {
            throw new BusinessException("请先登录后再查看证据");
        }
        assertReadable(operator, code);
        String stageFilter = EvidenceLinkKey.text(stage);
        String operationTypeFilter = EvidenceLinkKey.text(operationType);
        String operationCodeFilter = EvidenceLinkKey.text(operationCode);
        List<BusinessEvidenceLink> targetLinks = links.findByTarget(type, code);
        List<EvidenceAttachment> targetAttachments = documents.attachmentsByBusinessCode(code);
        Map<String, EvidenceAttachment> attachmentsByCode = new HashMap<>();
        targetAttachments.forEach(attachment -> attachmentsByCode.putIfAbsent(attachment.code(), attachment));
        List<BusinessEvidenceLink> matched = targetLinks.stream()
                .filter(link -> stageFilter.isEmpty() || stageFilter.equals(link.stage()))
                .filter(link -> operationTypeFilter.isEmpty() || operationTypeFilter.equals(link.operationType()))
                .filter(link -> operationCodeFilter.isEmpty() || operationCodeFilter.equals(link.operationCode()))
                .toList();
        List<EvidenceLinkView> views = matched.stream().map(link -> viewOf(link, attachmentsByCode)).toList();
        Set<String> linkedAttachments = targetLinks.stream()
                .map(BusinessEvidenceLink::attachmentCode)
                .collect(java.util.stream.Collectors.toSet());
        List<HistoricalEvidenceView> historical = targetAttachments.stream()
                .filter(attachment -> code.equals(attachment.businessCode()))
                .filter(attachment -> !linkedAttachments.contains(attachment.code()))
                .filter(attachment -> {
                    String origin = EvidenceLinkKey.text(attachment.objectType());
                    return origin.isEmpty() || origin.equals(type);
                })
                .map(this::historicalOf)
                .toList();
        return new EvidenceBoard(views, historical);
    }

    public EvidenceLinkPage queryLinkedPage(
            String targetType,
            String targetCode,
            String stage,
            String operationType,
            String operationCode,
            long afterId,
            int limit,
            OperatorContext operator
    ) {
        return queryLinkedPage(
                targetType,
                targetCode,
                stage,
                operationType,
                operationCode,
                afterId,
                limit,
                operator,
                EvidencePageFilter.empty()
        );
    }

    public EvidenceLinkPage queryLinkedPage(
            String targetType,
            String targetCode,
            String stage,
            String operationType,
            String operationCode,
            long afterId,
            int limit,
            OperatorContext operator,
            EvidencePageFilter filter
    ) {
        String type = requiredObjectType(targetType);
        String code = required(targetCode, "单据编号不能为空");
        if (operator == null || operator.operator() == null || operator.operator().isBlank()) {
            throw new BusinessException("请先登录后再查看证据");
        }
        assertReadable(operator, code);
        if (afterId < 0) {
            throw new BusinessException("证据引用游标不能小于 0");
        }
        if (limit < 1 || limit > 100) {
            throw new BusinessException("证据引用分页大小必须在 1 到 100 之间");
        }

        NormalizedEvidencePageFilter attachmentFilter = normalizeFilter(filter);
        String stageFilter = EvidenceLinkKey.text(stage);
        String operationTypeFilter = EvidenceLinkKey.text(operationType);
        String operationCodeFilter = EvidenceLinkKey.text(operationCode);
        int batchLimit = limit + 1;
        long scanAfterId = afterId;
        List<BusinessEvidenceLinkPage.Entry> visible = new ArrayList<>();
        Map<String, EvidenceAttachment> attachmentsByCode = new HashMap<>();
        while (visible.size() <= limit) {
            BusinessEvidenceLinkPage page = links.findPageByTarget(type, code, scanAfterId, batchLimit);
            if (page.entries().isEmpty()) {
                break;
            }
            long lastScannedId = scanAfterId;
            for (BusinessEvidenceLinkPage.Entry entry : page.entries()) {
                lastScannedId = entry.id();
                BusinessEvidenceLink link = entry.link();
                if (!matches(link, stageFilter, operationTypeFilter, operationCodeFilter)) {
                    continue;
                }
                EvidenceAttachment attachment = documents.findAttachmentByCode(link.attachmentCode()).orElse(null);
                boolean sameBusiness = attachment != null && code.equals(attachment.businessCode());
                if (attachmentFilter.active()
                        && (!sameBusiness || !matches(attachment, attachmentFilter))) {
                    continue;
                }
                if (sameBusiness) {
                    attachmentsByCode.putIfAbsent(attachment.code(), attachment);
                }
                visible.add(entry);
                if (visible.size() > limit) {
                    break;
                }
            }
            if (visible.size() > limit) {
                break;
            }
            scanAfterId = lastScannedId;
            if (!page.hasMore()) {
                break;
            }
        }

        int itemCount = Math.min(limit, visible.size());
        List<EvidenceLinkView> items = visible.subList(0, itemCount).stream()
                .map(entry -> viewOf(entry.link(), attachmentsByCode))
                .toList();
        boolean hasMore = visible.size() > limit;
        Long nextCursor = hasMore && itemCount > 0 ? visible.get(itemCount - 1).id() : null;
        return new EvidenceLinkPage(items, nextCursor, hasMore);
    }

    private boolean matches(
            BusinessEvidenceLink link,
            String stageFilter,
            String operationTypeFilter,
            String operationCodeFilter
    ) {
        return (stageFilter.isEmpty() || stageFilter.equals(link.stage()))
                && (operationTypeFilter.isEmpty() || operationTypeFilter.equals(link.operationType()))
                && (operationCodeFilter.isEmpty() || operationCodeFilter.equals(link.operationCode()));
    }

    private boolean matches(EvidenceAttachment attachment, NormalizedEvidencePageFilter filter) {
        if (filter.uploadedFrom() != null || filter.uploadedTo() != null) {
            LocalDate uploadedDate = uploadedDate(attachment.uploadedAt());
            if (uploadedDate == null) {
                return false;
            }
            if (filter.uploadedFrom() != null && uploadedDate.isBefore(filter.uploadedFrom())) {
                return false;
            }
            if (filter.uploadedTo() != null && uploadedDate.isAfter(filter.uploadedTo())) {
                return false;
            }
        }
        if (!filter.mediaCategory().isEmpty() && !matchesMediaCategory(attachment.mediaType(), filter.mediaCategory())) {
            return false;
        }
        return filter.ownerKeyword().isEmpty()
                || EvidenceLinkKey.text(attachment.owner()).toLowerCase(Locale.ROOT)
                .contains(filter.ownerKeyword());
    }

    private boolean matchesMediaCategory(String mediaType, String category) {
        String type = EvidenceLinkKey.text(mediaType).toLowerCase(Locale.ROOT);
        return switch (category) {
            case "image" -> type.startsWith("image/");
            case "video" -> type.startsWith("video/");
            case "pdf" -> "application/pdf".equals(type);
            default -> false;
        };
    }

    private LocalDate uploadedDate(String uploadedAt) {
        String text = EvidenceLinkKey.text(uploadedAt);
        if (text.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(text.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private NormalizedEvidencePageFilter normalizeFilter(EvidencePageFilter filter) {
        EvidencePageFilter source = filter == null ? EvidencePageFilter.empty() : filter;
        LocalDate uploadedFrom = parseDate(source.uploadedFrom(), "上传开始日期");
        LocalDate uploadedTo = parseDate(source.uploadedTo(), "上传结束日期");
        if (uploadedFrom != null && uploadedTo != null && uploadedFrom.isAfter(uploadedTo)) {
            throw new BusinessException("上传日期起止范围无效");
        }
        String mediaCategory = EvidenceLinkKey.text(source.mediaCategory()).toLowerCase(Locale.ROOT);
        if (!mediaCategory.isEmpty() && !Set.of("image", "video", "pdf").contains(mediaCategory)) {
            throw new BusinessException("媒体分类只支持 image、video、pdf");
        }
        String ownerKeyword = EvidenceLinkKey.text(source.ownerKeyword()).toLowerCase(Locale.ROOT);
        return new NormalizedEvidencePageFilter(uploadedFrom, uploadedTo, mediaCategory, ownerKeyword);
    }

    private LocalDate parseDate(String raw, String label) {
        String text = EvidenceLinkKey.text(raw);
        if (text.isEmpty()) {
            return null;
        }
        if (!text.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new BusinessException(label + "必须是 yyyy-MM-dd");
        }
        try {
            return LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(label + "不是有效日期");
        }
    }

    private record NormalizedEvidencePageFilter(
            LocalDate uploadedFrom,
            LocalDate uploadedTo,
            String mediaCategory,
            String ownerKeyword
    ) {
        private boolean active() {
            return uploadedFrom != null
                    || uploadedTo != null
                    || !mediaCategory.isEmpty()
                    || !ownerKeyword.isEmpty();
        }
    }

    public HistoricalEvidencePage queryHistoricalPage(
            String targetType,
            String targetCode,
            long afterId,
            int limit,
            OperatorContext operator
    ) {
        String type = requiredObjectType(targetType);
        String code = required(targetCode, "单据编号不能为空");
        if (operator == null || operator.operator() == null || operator.operator().isBlank()) {
            throw new BusinessException("请先登录后再查看证据");
        }
        assertReadable(operator, code);
        if (afterId < 0) {
            throw new BusinessException("附件游标不能小于 0");
        }
        if (limit < 1 || limit > 100) {
            throw new BusinessException("历史证据分页大小必须在 1 到 100 之间");
        }

        Set<String> linkedAttachments = links.findByTarget(type, code).stream()
                .map(BusinessEvidenceLink::attachmentCode)
                .collect(java.util.stream.Collectors.toSet());
        int batchLimit = limit + 1;
        long scanAfterId = afterId;
        List<DocumentAttachmentPage.Entry> visible = new ArrayList<>();
        while (visible.size() <= limit) {
            DocumentAttachmentPage page = documents.attachmentsPageByBusinessCode(code, scanAfterId, batchLimit);
            if (page.entries().isEmpty()) {
                break;
            }
            long lastScannedId = scanAfterId;
            for (DocumentAttachmentPage.Entry entry : page.entries()) {
                lastScannedId = entry.id();
                EvidenceAttachment attachment = entry.attachment();
                if (linkedAttachments.contains(attachment.code())) {
                    continue;
                }
                String origin = EvidenceLinkKey.text(attachment.objectType());
                if (!origin.isEmpty() && !origin.equals(type)) {
                    continue;
                }
                visible.add(entry);
                if (visible.size() > limit) {
                    break;
                }
            }
            if (visible.size() > limit) {
                break;
            }
            scanAfterId = lastScannedId;
            if (!page.hasMore()) {
                break;
            }
        }

        int itemCount = Math.min(limit, visible.size());
        List<HistoricalEvidenceView> items = visible.subList(0, itemCount).stream()
                .map(entry -> historicalOf(entry.attachment()))
                .toList();
        boolean hasMore = visible.size() > limit;
        Long nextCursor = hasMore && itemCount > 0 ? visible.get(itemCount - 1).id() : null;
        return new HistoricalEvidencePage(items, nextCursor, hasMore);
    }

    private void assertOperation(
            String stage,
            String operationType,
            String operationCode,
            String processName,
            String clauseNo,
            String targetType,
            String targetCode
    ) {
        if (EvidenceCodes.INTAKE.equals(stage) && EvidenceCodes.INBOUND_CONFIRM.equals(operationType)) {
            assertIntake(operationCode, targetType, targetCode);
            return;
        }
        if (EvidenceCodes.PRODUCTION.equals(stage)
                && (EvidenceCodes.PROCESS_STATION_REPORT.equals(operationType)
                || EvidenceCodes.PRODUCTION_REPORT.equals(operationType))) {
            assertProduction(operationType, operationCode, processName, targetType, targetCode);
            return;
        }
        if (EvidenceCodes.QUALITY.equals(stage) && EvidenceCodes.QUALITY_CHECK.equals(operationType)) {
            assertQualityCheck(operationCode, targetType, targetCode);
            return;
        }
        if (EvidenceCodes.QUALITY.equals(stage) && EvidenceCodes.QUALITY_FREEZE.equals(operationType)) {
            assertQualityLedger(EvidenceCodes.QUALITY_FREEZE, operationCode, targetType, targetCode);
            return;
        }
        if (EvidenceCodes.QUALITY.equals(stage) && EvidenceCodes.QUALITY_RELEASE.equals(operationType)) {
            assertQualityLedger(EvidenceCodes.QUALITY_RELEASE, operationCode, targetType, targetCode);
            return;
        }
        if ("REWORK".equals(stage) && EvidenceCodes.REWORK_DISPATCH.equals(operationType)) {
            assertReworkDispatch(operationCode, targetType, targetCode);
            return;
        }
        if ("REWORK".equals(stage) && EvidenceCodes.REWORK_REPORT.equals(operationType)) {
            assertReworkReport(operationCode, targetType, targetCode);
            return;
        }
        if (EvidenceCodes.SHIPPING.equals(stage) && EvidenceCodes.OUTBOUND_SHIPMENT.equals(operationType)) {
            assertShipment(operationCode, targetType, targetCode);
            return;
        }
        if ("ORDER_CHANGE".equals(stage) && "ORDER_CHANGE".equals(operationType)) {
            assertOrderChange(operationCode, targetType, targetCode);
            return;
        }
        if (EvidenceCodes.PROGRESS_NOTE.equals(operationType)) {
            assertProgressNote(stage, operationCode, processName, clauseNo, targetType, targetCode);
            return;
        }
        throw new BusinessException("当前阶段不能绑定这种操作");
    }

    private void assertProgressNote(
            String stage,
            String operationCode,
            String processName,
            String clauseNo,
            String targetType,
            String targetCode
    ) {
        if (!EvidenceCodes.PRODUCTION.equals(stage)
                && !EvidenceCodes.QUALITY.equals(stage)
                && !EvidenceCodes.SHIPPING.equals(stage)) {
            throw new BusinessException("当前阶段不能绑定这种操作");
        }
        if (progressRecords == null) {
            throw new BusinessException("过程记录不存在");
        }
        OperationProgressRecord record = progressRecords.findByCode(operationCode).orElse(null);
        if (record == null) {
            throw new BusinessException("过程记录不存在");
        }
        if (!stage.equals(record.stage())
                || !targetType.equals(record.targetType())
                || !targetCode.equals(record.targetCode())) {
            throw new BusinessException("证据必须绑到这条过程记录所在的单据和阶段");
        }
        if (!processName.isEmpty() && !processName.equals(record.process())) {
            throw new BusinessException("证据工序与过程记录不一致");
        }
        if (!clauseNo.isEmpty() && !clauseNo.equals(record.clauseNo())) {
            throw new BusinessException("证据条款与过程记录不一致");
        }
    }

    private void assertOrderChange(String operationCode, String targetType, String targetCode) {
        if (orderChanges == null) {
            throw new BusinessException("改单证据尚未装配");
        }
        if (!EvidenceCodes.MOTHER_ORDER.equals(targetType)) {
            throw new BusinessException("改单证据必须绑到母单");
        }
        OrderChange change = orderChanges.findById(operationCode).orElse(null);
        if (change == null) {
            throw new BusinessException("改单不存在");
        }
        if (!change.motherOrderCode().equals(targetCode)) {
            throw new BusinessException("改单证据必须绑到这张改单的母单");
        }
    }

    private void assertIntake(String operationCode, String targetType, String targetCode) {
        if (!EvidenceCodes.MOTHER_ORDER.equals(targetType)) {
            throw new BusinessException("来料证据只能绑到来料母单");
        }
        MotherOrderRecord mother = businessDocuments.findMotherOrderByCode(targetCode)
                .orElseThrow(() -> new BusinessException("单据不存在"));
        if (!operationCode.equals(EvidenceLinkKey.text(mother.ledgerCode()))) {
            throw new BusinessException("操作编号不是已存在的服务端记录");
        }
        requireLedger(operationCode, EvidenceCodes.INBOUND_CONFIRM, mother.code());
    }

    private void assertProduction(
            String operationType,
            String operationCode,
            String processName,
            String targetType,
            String targetCode
    ) {
        if (processName.isEmpty()) {
            throw new BusinessException("生产证据必须写明工序");
        }
        assertWorkDocument(targetType, targetCode);
        ProcessStationHandoff handoff = businessDocuments.findProcessStationHandoff(targetCode, processName)
                .orElseThrow(() -> new BusinessException("工序交付不存在"));
        if (!operationCode.equals(handoff.ledgerCode())) {
            throw new BusinessException("操作编号不是已存在的服务端记录");
        }
        requireLedger(operationCode, operationType, targetCode);
    }

    private void assertQualityCheck(String operationCode, String targetType, String targetCode) {
        QualityRequirementCheck check = requireCheck(targetType, targetCode);
        if (!operationCode.equals(check.qualityCaseCode())) {
            throw new BusinessException("操作编号不是已存在的服务端记录");
        }
    }

    private void assertQualityLedger(String operationType, String operationCode, String targetType, String targetCode) {
        QualityRequirementCheck check = requireCheck(targetType, targetCode);
        StockLedgerEntry ledger = requireLedger(operationCode, operationType, check.qualityCaseCode());
        if (EvidenceCodes.QUALITY_FREEZE.equals(operationType)
                && !operationCode.equals(EvidenceLinkKey.text(check.ledgerCode()))) {
            throw new BusinessException("操作编号不是已存在的服务端记录");
        }
        if (!check.qualityCaseCode().equals(ledger.documentCode())) {
            throw new BusinessException("操作编号不是已存在的服务端记录");
        }
    }

    private void assertReworkDispatch(String operationCode, String targetType, String targetCode) {
        QualityRequirementCheck check = reworkCheck(targetType, targetCode);
        String reworkCode = EvidenceLinkKey.text(check.reworkOrderCode());
        if (reworkCode.isEmpty() || !operationCode.equals(reworkCode)) {
            throw new BusinessException("操作编号不是已存在的服务端记录");
        }
        requireLedger(findLedgerCode(EvidenceCodes.REWORK_DISPATCH, reworkCode), EvidenceCodes.REWORK_DISPATCH, reworkCode);
    }

    private void assertReworkReport(String operationCode, String targetType, String targetCode) {
        QualityRequirementCheck check = reworkCheck(targetType, targetCode);
        String reworkCode = EvidenceLinkKey.text(check.reworkOrderCode());
        if (reworkCode.isEmpty()) {
            throw new BusinessException("操作编号不是已存在的服务端记录");
        }
        requireLedger(operationCode, EvidenceCodes.REWORK_REPORT, reworkCode);
    }

    private void assertShipment(String operationCode, String targetType, String targetCode) {
        if (!EvidenceCodes.OUTBOUND_ORDER.equals(targetType) || !operationCode.equals(targetCode)) {
            throw new BusinessException("发货证据只能绑到已出货的出货单");
        }
        OutboundOrderRecord outbound = businessDocuments.findOutboundOrderByCode(targetCode)
                .orElseThrow(() -> new BusinessException("出货单不存在"));
        if (EvidenceLinkKey.text(outbound.ledgerCode()).isEmpty()) {
            throw new BusinessException("操作编号不是已存在的服务端记录");
        }
        requireLedger(outbound.ledgerCode(), EvidenceCodes.OUTBOUND_SHIPMENT, outbound.code());
    }

    private QualityRequirementCheck requireCheck(String targetType, String targetCode) {
        assertWorkDocument(targetType, targetCode);
        return businessDocuments.findQualityRequirementCheck(targetCode)
                .orElseThrow(() -> new BusinessException("质检记录不存在"));
    }

    private QualityRequirementCheck reworkCheck(String targetType, String targetCode) {
        if (EvidenceCodes.REWORK.equals(targetType)) {
            return businessDocuments.listAllQualityRequirementChecks().stream()
                    .filter(row -> targetCode.equals(row.reworkOrderCode()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("单据不存在"));
        }
        assertWorkDocument(targetType, targetCode);
        return businessDocuments.findQualityRequirementCheck(targetCode)
                .orElseThrow(() -> new BusinessException("质检记录不存在"));
    }

    private void assertWorkDocument(String targetType, String targetCode) {
        if (EvidenceCodes.MOTHER_ORDER.equals(targetType)) {
            businessDocuments.findMotherOrderByCode(targetCode)
                    .orElseThrow(() -> new BusinessException("单据不存在"));
            if (!businessDocuments.listChildWorkOrders(targetCode).isEmpty()) {
                throw new BusinessException("已拆母单不能代替子单绑定证据");
            }
            return;
        }
        if (EvidenceCodes.WORK_ORDER.equals(targetType)) {
            businessDocuments.findChildWorkOrderByCode(targetCode)
                    .orElseThrow(() -> new BusinessException("单据不存在"));
            return;
        }
        throw new BusinessException("证据只能绑到同一张真实单据");
    }

    private StockLedgerEntry requireLedger(String ledgerCode, String documentType, String documentCode) {
        if (ledgerCode == null || ledgerCode.isBlank()) {
            throw new BusinessException("操作编号不是已存在的服务端记录");
        }
        return stocks.ledgers().stream()
                .filter(entry -> ledgerCode.equals(entry.code()))
                .filter(entry -> documentType.equals(entry.documentType()))
                .filter(entry -> documentCode.equals(entry.documentCode()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("操作编号不是已存在的服务端记录"));
    }

    private String findLedgerCode(String documentType, String documentCode) {
        return stocks.ledgers().stream()
                .filter(entry -> documentType.equals(entry.documentType()))
                .filter(entry -> documentCode.equals(entry.documentCode()))
                .map(StockLedgerEntry::code)
                .findFirst()
                .orElseThrow(() -> new BusinessException("操作编号不是已存在的服务端记录"));
    }

    private PermissionCode permissionFor(String stage, String operationType) {
        if ("ORDER_CHANGE".equals(stage)) {
            return PermissionCode.BUSINESS_PRODUCTION_REPORT;
        }
        if (EvidenceCodes.INTAKE.equals(stage)) {
            return PermissionCode.BUSINESS_INBOUND_CONFIRM;
        }
        if (EvidenceCodes.PRODUCTION.equals(stage)) {
            return PermissionCode.BUSINESS_PRODUCTION_REPORT;
        }
        if (EvidenceCodes.QUALITY_RELEASE.equals(operationType)) {
            return PermissionCode.BUSINESS_QUALITY_RELEASE;
        }
        if (EvidenceCodes.REWORK_DISPATCH.equals(operationType)) {
            return PermissionCode.BUSINESS_REWORK_DISPATCH;
        }
        if (EvidenceCodes.REWORK_REPORT.equals(operationType)) {
            return PermissionCode.BUSINESS_REWORK_REPORT;
        }
        if (EvidenceCodes.SHIPPING.equals(stage)) {
            return PermissionCode.BUSINESS_OUTBOUND_SHIPMENT;
        }
        if (EvidenceCodes.QUALITY.equals(stage)) {
            return PermissionCode.BUSINESS_QUALITY_FREEZE;
        }
        throw new BusinessException("当前阶段不能绑定这种操作");
    }

    private EvidenceLinkView viewOf(BusinessEvidenceLink link, Map<String, EvidenceAttachment> attachmentsByCode) {
        EvidenceAttachment attachment = attachmentsByCode.get(link.attachmentCode());
        boolean retired = attachment != null && RETIRED.equals(attachment.status());
        return new EvidenceLinkView(
                link.linkCode(),
                link.attachmentCode(),
                link.targetType(),
                link.targetCode(),
                link.stage(),
                link.scene(),
                EvidenceCodes.sceneLabel(link.scene()),
                link.operationType(),
                link.operationCode(),
                link.processName(),
                link.clauseNo(),
                link.operatorName(),
                link.linkedAt(),
                attachment == null ? "" : attachment.fileName(),
                attachment == null ? "" : attachment.mediaType(),
                attachment == null ? "" : attachment.checksum(),
                attachment == null ? "" : attachment.status(),
                retired || attachment == null ? "" : previewOrEmpty(attachment),
                EvidenceCodes.LINKED
        );
    }

    private HistoricalEvidenceView historicalOf(EvidenceAttachment attachment) {
        boolean retired = RETIRED.equals(attachment.status());
        boolean uncertain = EvidenceLinkKey.text(attachment.objectType()).isEmpty();
        return new HistoricalEvidenceView(
                attachment.code(),
                attachment.businessCode(),
                EvidenceLinkKey.text(attachment.objectType()),
                attachment.fileName(),
                attachment.mediaType(),
                attachment.checksum(),
                attachment.status(),
                attachment.uploadedAt(),
                uncertain ? EvidenceCodes.HISTORICAL_LABEL : "已上传待关联",
                uncertain ? "" : EvidenceCodes.sceneCode(attachment.businessType()),
                retired ? "" : previewOrEmpty(attachment),
                uncertain ? EvidenceCodes.HISTORICAL : EvidenceCodes.UPLOADED_PENDING_LINK
        );
    }

    private String previewOrEmpty(EvidenceAttachment attachment) {
        try {
            return documentManagementService.previewUrl(attachment.code()).url();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private void assertReadable(OperatorContext operator, String businessCode) {
        if (dataAccess == null || !dataAccess.canRead(operator, businessCode)) {
            throw new PermissionDeniedException("单号不在当前账号的业务数据范围内");
        }
    }

    private boolean canReadQuietly(OperatorContext operator, String businessCode) {
        return dataAccess != null && dataAccess.canRead(operator, businessCode);
    }

    private void assertSameDimensions(BusinessEvidenceLink existing, BusinessEvidenceLink candidate) {
        if (!EvidenceLinkKey.sameDimensions(existing, candidate)) {
            throw new BusinessException("证据引用键冲突，请换一张单据重试");
        }
    }

    private String requiredObjectType(String raw) {
        String type = EvidenceCodes.canonicalObjectType(raw);
        if (type.isEmpty()) {
            throw new BusinessException("不支持的单据类型");
        }
        return type;
    }

    private String requiredStage(String raw) {
        String stage = EvidenceLinkKey.text(raw);
        if (!EvidenceCodes.STAGES.contains(stage)) {
            throw new BusinessException("不支持的业务阶段");
        }
        return stage;
    }

    private String requiredScene(String raw) {
        String scene = EvidenceLinkKey.text(raw).toUpperCase(java.util.Locale.ROOT);
        if (EvidenceCodes.sceneLabel(scene) == null) {
            throw new BusinessException("不支持的采集场景");
        }
        return scene;
    }

    private String required(String value, String message) {
        String text = EvidenceLinkKey.text(value);
        if (text.isEmpty()) {
            throw new BusinessException(message);
        }
        return text;
    }

    private void checkLength(String value, int max, String label) {
        if (value.length() > max) {
            throw new BusinessException(label + "超过可保存长度");
        }
    }

    private String nextLinkCode() {
        return "LNK-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.now())
                + "-" + sequence.incrementAndGet();
    }
}
