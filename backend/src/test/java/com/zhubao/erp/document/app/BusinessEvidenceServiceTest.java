package com.zhubao.erp.document.app;

import com.zhubao.erp.business.app.BusinessDataAccess;
import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.OutboundOrderRecord;
import com.zhubao.erp.business.domain.ProcessStationHandoff;
import com.zhubao.erp.business.domain.QualityCheckLine;
import com.zhubao.erp.business.domain.QualityRequirementCheck;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.domain.BindEvidenceCommand;
import com.zhubao.erp.document.domain.BusinessEvidenceLink;
import com.zhubao.erp.document.domain.BusinessEvidenceLinkRepository;
import com.zhubao.erp.document.domain.DocumentAuditEvent;
import com.zhubao.erp.document.domain.DocumentManagementRepository;
import com.zhubao.erp.document.domain.DocumentPackage;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.document.domain.EvidenceAttachmentUploadResult;
import com.zhubao.erp.document.domain.EvidenceBoard;
import com.zhubao.erp.document.domain.EvidenceCodes;
import com.zhubao.erp.document.infra.AttachmentStorageProperties;
import com.zhubao.erp.document.infra.DocumentCaptureProperties;
import com.zhubao.erp.document.infra.InMemoryBusinessEvidenceLinkRepository;
import com.zhubao.erp.document.infra.InMemoryDocumentManagementRepository;
import com.zhubao.erp.document.infra.JdbcBusinessEvidenceLinkRepository;
import com.zhubao.erp.document.infra.JdbcDocumentManagementRepository;
import com.zhubao.erp.document.infra.LocalAttachmentObjectStorage;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.zhubao.erp.document.AttachmentTestMedia.jpeg;

/**
 * 证据引用的内存与 JDBC 行为。绑定不写库存，也不把同客户的母子单当成同一张单据。
 */
class BusinessEvidenceServiceTest {

    private static final String MOTHER = "MD-S3-001";
    private static final String CHILD = "ZD-S3-001";
    private static final String INBOUND_LEDGER = "STK-IN-S3";
    private static final String PROCESS_LEDGER = "STK-PROC-S3";
    private static final String REWORK = "FG-ZD-S3-001";
    private static final String REWORK_LEDGER = "STK-RW-S3";
    private static final String SHIPMENT = "OUT-S3-001";
    private static final String SHIP_LEDGER = "STK-OUT-S3";

    @TempDir
    Path tempDir;

    @Test
    void memoryKeepsSeparateLinksAndRejectsStolenBinds() {
        Fixture fixture = memoryFixture();
        assertEvidenceBehavior(fixture);
    }

    @Test
    void jdbcKeepsSeparateLinksAndEnforcesNormalizedUniqueKey() throws Exception {
        JdbcDataSource dataSource = dataSource();
        createDocumentSchema(dataSource);
        Fixture fixture = fixture(
                new InMemoryBusinessDocumentRepository(),
                new JdbcDocumentManagementRepository(dataSource),
                new JdbcBusinessEvidenceLinkRepository(dataSource)
        );
        assertEvidenceBehavior(fixture);
        assertThat(count(dataSource, "select count(*) from doc_business_evidence_link where process_name is null")).isZero();
        BusinessEvidenceLink first = fixture.links.findAll().get(0);
        assertThatThrownBy(() -> fixture.links.insert(first))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void metadataFailureKeepsOriginalForReconciliationAndRetryCanSave() throws Exception {
        InMemoryDocumentManagementRepository delegate = new InMemoryDocumentManagementRepository();
        FailOnceRepository repository = new FailOnceRepository(delegate);
        InMemoryBusinessDocumentRepository business = new InMemoryBusinessDocumentRepository();
        business.saveMotherOrder(mother(MOTHER, INBOUND_LEDGER));
        DocumentCaptureService capture = capture(business, repository, 8, 32);
        OperatorContext receiver = receiver();
        byte[] photo = jpeg("photo");

        assertThatThrownBy(() -> capture.capture(
                "mother", MOTHER, "UNPACK", "a.jpg", "image/jpeg",
                new java.io.ByteArrayInputStream(photo), photo.length, receiver
        )).isInstanceOf(BusinessException.class).hasMessageContaining("原件已保存");
        assertThat(fileCount()).isEqualTo(1);
        assertThat(delegate.attachments()).noneMatch(item -> MOTHER.equals(item.businessCode()) && "手机采集".equals(item.source()));

        EvidenceAttachmentUploadResult saved = capture.capture(
                "mother", MOTHER, "UNPACK", "a.jpg", "image/jpeg",
                new java.io.ByteArrayInputStream(photo), photo.length, receiver
        );
        assertThat(saved.code()).startsWith("ATT-CAP-");
        assertThat(fileCount()).isEqualTo(2);
        assertThat(delegate.attachments())
                .filteredOn(item -> saved.code().equals(item.code()))
                .singleElement()
                .extracting(EvidenceAttachment::objectType)
                .isEqualTo(EvidenceCodes.MOTHER_ORDER);
    }

    private void assertEvidenceBehavior(Fixture fixture) {
        prepareFacts(fixture);
        int ledgersBefore = fixture.stocks.ledgers().size();
        byte[] photo = "same".getBytes(StandardCharsets.UTF_8);
        EvidenceAttachmentUploadResult unpack = upload(fixture, "mother", MOTHER, "UNPACK", photo);
        EvidenceAttachmentUploadResult weigh = upload(fixture, EvidenceCodes.MOTHER_ORDER, MOTHER, "WEIGH", photo);

        assertThat(weigh.code()).isEqualTo(unpack.code());
        assertThat(fileCount()).isEqualTo(1);
        EvidenceAttachment stored = fixture.documents.attachments().stream()
                .filter(item -> unpack.code().equals(item.code()))
                .findFirst()
                .orElseThrow();
        assertThat(stored.objectType()).isEqualTo(EvidenceCodes.MOTHER_ORDER);
        assertThat(stored.businessType()).isEqualTo("拆包");

        EvidenceBoard waiting = fixture.evidence.query(EvidenceCodes.MOTHER_ORDER, MOTHER, "", "", "", fixture.admin);
        assertThat(waiting.links()).isEmpty();
        assertThat(waiting.historical())
                .anySatisfy(item -> {
                    assertThat(item.attachmentCode()).isEqualTo(unpack.code());
                    assertThat(item.fileState()).isEqualTo(EvidenceCodes.UPLOADED_PENDING_LINK);
                    assertThat(item.label()).isEqualTo("已上传待关联");
                    assertThat(item.sceneCode()).isEqualTo("UNPACK");
                });

        BusinessEvidenceLink unpackLink = fixture.evidence.bind(bind(
                unpack.code(), EvidenceCodes.MOTHER_ORDER, MOTHER, EvidenceCodes.INTAKE, "UNPACK",
                EvidenceCodes.INBOUND_CONFIRM, INBOUND_LEDGER, null, null
        ), fixture.receiver);
        BusinessEvidenceLink weighLink = fixture.evidence.bind(bind(
                unpack.code(), EvidenceCodes.MOTHER_ORDER, MOTHER, EvidenceCodes.INTAKE, "WEIGH",
                EvidenceCodes.INBOUND_CONFIRM, INBOUND_LEDGER, "", ""
        ), fixture.receiver);
        BusinessEvidenceLink repeated = fixture.evidence.bind(bind(
                unpack.code(), EvidenceCodes.MOTHER_ORDER, MOTHER, EvidenceCodes.INTAKE, "UNPACK",
                EvidenceCodes.INBOUND_CONFIRM, INBOUND_LEDGER, " ", null
        ), fixture.receiver);

        assertThat(weighLink.linkCode()).isNotEqualTo(unpackLink.linkCode());
        assertThat(repeated.linkCode()).isEqualTo(unpackLink.linkCode());
        assertThat(fixture.links.findAll())
                .filteredOn(link -> MOTHER.equals(link.targetCode()) && EvidenceCodes.INTAKE.equals(link.stage()))
                .hasSize(2);
        assertThat(fixture.documents.auditEvents())
                .filteredOn(event -> "绑定证据".equals(event.action()) && event.detail().contains(unpack.code()))
                .hasSize(2);
        EvidenceBoard linked = fixture.evidence.query(
                EvidenceCodes.MOTHER_ORDER, MOTHER, EvidenceCodes.INTAKE, EvidenceCodes.INBOUND_CONFIRM, INBOUND_LEDGER, fixture.admin
        );
        assertThat(linked.links()).extracting(view -> view.scene()).containsExactlyInAnyOrder("UNPACK", "WEIGH");
        assertThat(linked.links()).allSatisfy(view -> {
            assertThat(view.attachmentCode()).isEqualTo(unpack.code());
            assertThat(view.fileState()).isEqualTo(EvidenceCodes.LINKED);
            assertThat(view.sceneLabel()).isIn("拆包", "称重");
        });
        assertThat(linked.historical()).noneMatch(item -> unpack.code().equals(item.attachmentCode()));
        assertThat(fixture.stocks.ledgers()).hasSize(ledgersBefore);

        assertThatThrownBy(() -> fixture.evidence.bind(bind(
                unpack.code(), EvidenceCodes.WORK_ORDER, CHILD, EvidenceCodes.PRODUCTION, "PROCESS",
                EvidenceCodes.PROCESS_STATION_REPORT, PROCESS_LEDGER, "抛光", ""
        ), fixture.leader)).isInstanceOf(BusinessException.class).hasMessageContaining("不属于当前单据");

        EvidenceAttachmentUploadResult childPhoto = upload(fixture, "work_order", CHILD, "PROCESS", "child".getBytes(StandardCharsets.UTF_8));
        BusinessEvidenceLink processLink = fixture.evidence.bind(bind(
                childPhoto.code(), EvidenceCodes.WORK_ORDER, CHILD, EvidenceCodes.PRODUCTION, "PROCESS",
                EvidenceCodes.PROCESS_STATION_REPORT, PROCESS_LEDGER, "抛光", ""
        ), fixture.operator);
        assertThatThrownBy(() -> fixture.evidence.bind(bind(
                childPhoto.code(), EvidenceCodes.WORK_ORDER, CHILD, EvidenceCodes.PRODUCTION, "CHECK",
                EvidenceCodes.PROCESS_STATION_REPORT, "HO-EMPTY", "抛光", ""
        ), fixture.operator)).isInstanceOf(BusinessException.class).hasMessageContaining("不是已存在的服务端记录");
        assertThat(processLink.operationCode()).isEqualTo(PROCESS_LEDGER);
        assertThatThrownBy(() -> fixture.evidence.bind(bind(
                unpack.code(), EvidenceCodes.MOTHER_ORDER, MOTHER, EvidenceCodes.PRODUCTION, "PROCESS",
                EvidenceCodes.PROCESS_STATION_REPORT, PROCESS_LEDGER, "抛光", ""
        ), fixture.operator)).isInstanceOf(BusinessException.class).hasMessageContaining("已拆母单");

        BusinessEvidenceLink clauseOne = fixture.evidence.bind(bind(
                childPhoto.code(), EvidenceCodes.WORK_ORDER, CHILD, EvidenceCodes.QUALITY, "CHECK",
                EvidenceCodes.QUALITY_CHECK, "QC-" + CHILD, null, "1"
        ), fixture.inspector);
        BusinessEvidenceLink clauseTwo = fixture.evidence.bind(bind(
                childPhoto.code(), EvidenceCodes.WORK_ORDER, CHILD, EvidenceCodes.QUALITY, "CHECK",
                EvidenceCodes.QUALITY_CHECK, "QC-" + CHILD, "", "2"
        ), fixture.inspector);
        BusinessEvidenceLink clauseOneAgain = fixture.evidence.bind(bind(
                childPhoto.code(), EvidenceCodes.WORK_ORDER, CHILD, EvidenceCodes.QUALITY, "CHECK",
                EvidenceCodes.QUALITY_CHECK, "QC-" + CHILD, "", "1"
        ), fixture.inspector);
        assertThat(clauseTwo.linkCode()).isNotEqualTo(clauseOne.linkCode());
        assertThat(clauseOneAgain.linkCode()).isEqualTo(clauseOne.linkCode());

        fixture.evidence.bind(bind(
                childPhoto.code(), EvidenceCodes.WORK_ORDER, CHILD, "REWORK", "DEFECT",
                EvidenceCodes.REWORK_DISPATCH, REWORK, "", ""
        ), fixture.inspector);
        assertThatThrownBy(() -> fixture.evidence.bind(bind(
                childPhoto.code(), EvidenceCodes.WORK_ORDER, CHILD, "REWORK", "DEFECT",
                EvidenceCodes.REWORK_DISPATCH, "DR-DEVICE-1", "", ""
        ), fixture.inspector)).isInstanceOf(BusinessException.class).hasMessageContaining("不是已存在的服务端记录");

        EvidenceAttachmentUploadResult shipmentPhoto = upload(fixture, "outbound", SHIPMENT, "PACK", "ship".getBytes(StandardCharsets.UTF_8));
        fixture.evidence.bind(bind(
                shipmentPhoto.code(), EvidenceCodes.OUTBOUND_ORDER, SHIPMENT, EvidenceCodes.SHIPPING, "PACK",
                EvidenceCodes.OUTBOUND_SHIPMENT, SHIPMENT, "", ""
        ), fixture.keeper);
        assertThatThrownBy(() -> fixture.evidence.bind(bind(
                shipmentPhoto.code(), EvidenceCodes.OUTBOUND_ORDER, "OUT-DRAFT", EvidenceCodes.SHIPPING, "WAYBILL",
                EvidenceCodes.OUTBOUND_SHIPMENT, "OUT-DRAFT", "", ""
        ), fixture.keeper)).isInstanceOf(BusinessException.class);

        EvidenceAttachment historical = historicalAttachment(fixture, "ATT-OLD-S3", MOTHER);
        EvidenceBoard board = fixture.evidence.query(EvidenceCodes.MOTHER_ORDER, MOTHER, "", "", "", fixture.admin);
        assertThat(board.historical()).anySatisfy(item -> {
            assertThat(item.attachmentCode()).isEqualTo("ATT-OLD-S3");
            assertThat(item.label()).isEqualTo(EvidenceCodes.HISTORICAL_LABEL);
            assertThat(item.fileState()).isEqualTo(EvidenceCodes.HISTORICAL);
            assertThat(item.objectType()).isEmpty();
            assertThat(item.sceneCode()).isEmpty();
        });
        assertThat(board.links()).noneMatch(link -> historical.code().equals(link.attachmentCode()));
        assertThatThrownBy(() -> fixture.evidence.bind(bind(
                historical.code(), EvidenceCodes.MOTHER_ORDER, MOTHER, EvidenceCodes.INTAKE, "FINISH",
                EvidenceCodes.INBOUND_CONFIRM, INBOUND_LEDGER, "", ""
        ), fixture.receiver)).isInstanceOf(BusinessException.class).hasMessageContaining("只能回看");

        retire(fixture, unpack.code());
        EvidenceBoard retired = fixture.evidence.query(EvidenceCodes.MOTHER_ORDER, MOTHER, "", "", "", fixture.admin);
        assertThat(retired.links()).anyMatch(link -> unpack.code().equals(link.attachmentCode()) && link.previewUrl().isEmpty());
        assertThatThrownBy(() -> fixture.documentManagementService.previewUrl(unpack.code()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已下线");
        assertThatThrownBy(() -> fixture.evidence.bind(bind(
                unpack.code(), EvidenceCodes.MOTHER_ORDER, MOTHER, EvidenceCodes.INTAKE, "FINISH",
                EvidenceCodes.INBOUND_CONFIRM, INBOUND_LEDGER, "", ""
        ), fixture.receiver)).isInstanceOf(BusinessException.class).hasMessageContaining("已下线");

        assertThatThrownBy(() -> upload(fixture, "mother", MOTHER, "UNPACK", "123456789".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("大小限制");
        assertThatThrownBy(() -> fixture.capture.capture(
                "mother", MOTHER, "UNPACK", "note.txt", "text/plain",
                new java.io.ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)), 1, fixture.receiver
        )).isInstanceOf(BusinessException.class).hasMessageContaining("只接受");
        assertThatThrownBy(() -> fixture.capture.storeVerified(
                "work_order", "NO-SUCH-ORDER", "PROCESS", "x.jpg", "image/jpeg",
                new java.io.ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)), 1, fixture.operator
        )).isInstanceOf(BusinessException.class).hasMessageContaining("无法确认");

        assertThatThrownBy(() -> fixture.evidence.bind(bind(
                childPhoto.code(), EvidenceCodes.WORK_ORDER, CHILD, EvidenceCodes.INTAKE, "UNPACK",
                EvidenceCodes.INBOUND_CONFIRM, INBOUND_LEDGER, "", ""
        ), fixture.operator)).isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> fixture.evidence.query(EvidenceCodes.MOTHER_ORDER, MOTHER, "", "", "", fixture.outsider))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("业务数据范围");
        assertThatThrownBy(() -> fixture.evidence.bind(bind(
                "ATT-DOES-NOT-EXIST", EvidenceCodes.MOTHER_ORDER, MOTHER, EvidenceCodes.INTAKE, "UNPACK",
                EvidenceCodes.INBOUND_CONFIRM, INBOUND_LEDGER, "", ""
        ), fixture.outsider)).isInstanceOf(PermissionDeniedException.class);

        assertThat(fixture.business.listMotherOrders()).extracting(MotherOrderRecord::code).contains(MOTHER);
        assertThat(fixture.stocks.ledgers()).hasSize(ledgersBefore);
    }

    private void prepareFacts(Fixture fixture) {
        fixture.business.saveMotherOrder(mother(MOTHER, INBOUND_LEDGER));
        fixture.business.saveChildWorkOrder(child());
        fixture.business.saveProcessStationHandoff(new ProcessStationHandoff(
                CHILD, "抛光", "操作工 李华", PROCESS_LEDGER, "2026-10-12 11:00:00"
        ));
        fixture.business.saveQualityRequirementCheck(new QualityRequirementCheck(
                "QC-" + CHILD, CHILD, MOTHER, "质检员 周娜", "PASS", false, "", REWORK,
                "2026-10-12 12:00:00", List.of(new QualityCheckLine(1, "无砂眼", "合格", ""))
        ));
        fixture.business.saveOutboundOrder(outbound());
        saveLedger(fixture, INBOUND_LEDGER, EvidenceCodes.INBOUND_CONFIRM, MOTHER, null);
        saveLedger(fixture, PROCESS_LEDGER, EvidenceCodes.PROCESS_STATION_REPORT, CHILD, CHILD);
        saveLedger(fixture, REWORK_LEDGER, EvidenceCodes.REWORK_DISPATCH, REWORK, MOTHER);
        saveLedger(fixture, SHIP_LEDGER, EvidenceCodes.OUTBOUND_SHIPMENT, SHIPMENT, CHILD);
    }

    private EvidenceAttachmentUploadResult upload(Fixture fixture, String objectType, String businessCode, String scene, byte[] content) {
        OperatorContext actor = fixture.admin;
        byte[] media = jpeg(new String(content, StandardCharsets.UTF_8));
        return fixture.capture.storeVerified(
                objectType, businessCode, scene, "evidence.jpg", "image/jpeg",
                new java.io.ByteArrayInputStream(media), media.length, actor
        );
    }

    private EvidenceAttachment historicalAttachment(Fixture fixture, String code, String businessCode) {
        if (fixture.documents.packages().stream().noneMatch(item -> item.code().equals("DOC-" + businessCode))) {
            fixture.documents.savePackage(new DocumentPackage(
                    "DOC-" + businessCode, businessCode, "来料", businessCode, "收货员", "旧证据",
                    0, 0, "采集中", "本地", "2026-10-01 08:00:00", "green"
            ));
        }
        EvidenceAttachment attachment = new EvidenceAttachment(
                code, "DOC-" + businessCode, "来料母单", businessCode, "old.jpg", "image/jpeg",
                "local://evidence/old.jpg", "hot 180d -> archive", "PWA 拍照上传", "已上传",
                "收货员 何琴", "sha256:old-" + code, "2026-10-01 08:00:00", "无", "green", null
        );
        fixture.documents.saveAttachment(attachment);
        return attachment;
    }

    private void retire(Fixture fixture, String code) {
        EvidenceAttachment attachment = fixture.documents.attachments().stream()
                .filter(item -> code.equals(item.code()))
                .findFirst()
                .orElseThrow();
        fixture.documents.saveAttachment(new EvidenceAttachment(
                attachment.code(), attachment.documentCode(), attachment.businessType(), attachment.businessCode(),
                attachment.fileName(), attachment.mediaType(), attachment.storageLocation(), attachment.storagePolicy(),
                attachment.source(), "已下线", attachment.owner(), attachment.checksum(), attachment.uploadedAt(),
                attachment.fallbackAction(), "gray", attachment.objectType()
        ));
    }

    private void saveLedger(Fixture fixture, String code, String documentType, String documentCode, String workOrderCode) {
        fixture.ledgerRepository.save(new StockLedgerEntry(
                code,
                Instant.parse("2026-10-12T02:00:00Z"),
                documentType,
                documentCode,
                null,
                new StockDimension(OwnerType.CUSTOMER, 1001L, 1001L, MOTHER, workOrderCode, "MAT-S3", "RAW", StockStatus.RAW_WAITING),
                BigDecimal.ONE,
                new BigDecimal("1.000"),
                ""
        ));
    }

    private BindEvidenceCommand bind(
            String attachmentCode,
            String targetType,
            String targetCode,
            String stage,
            String scene,
            String operationType,
            String operationCode,
            String processName,
            String clauseNo
    ) {
        return new BindEvidenceCommand(
                attachmentCode, targetType, targetCode, stage, scene, operationType, operationCode, processName, clauseNo
        );
    }

    private Fixture memoryFixture() {
        return fixture(
                new InMemoryBusinessDocumentRepository(),
                new InMemoryDocumentManagementRepository(),
                new InMemoryBusinessEvidenceLinkRepository()
        );
    }

    private Fixture fixture(
            InMemoryBusinessDocumentRepository business,
            DocumentManagementRepository documents,
            BusinessEvidenceLinkRepository links
    ) {
        InMemoryStockLedgerRepository ledgerRepository = new InMemoryStockLedgerRepository();
        StockLedgerService stocks = new StockLedgerService(ledgerRepository);
        AttachmentStorageProperties storageProperties = new AttachmentStorageProperties();
        storageProperties.setLocalRoot(tempDir.toString());
        DocumentCaptureProperties captureProperties = new DocumentCaptureProperties();
        captureProperties.setPhotoMaxBytes(8);
        captureProperties.setVideoMaxBytes(32);
        LocalAttachmentObjectStorage storage = new LocalAttachmentObjectStorage(storageProperties);
        DocumentManagementService documentManagementService = new DocumentManagementService(documents, storage);
        DocumentCaptureService capture = new DocumentCaptureService(
                documents, storage, documentManagementService, business, captureProperties
        );
        BusinessEvidenceService evidence = new BusinessEvidenceService(
                documents, links, business, stocks, documentManagementService,
                new BusinessDataAccess(business, stocks), new PermissionGuard()
        );
        return new Fixture(
                business, documents, links, stocks, ledgerRepository, capture, evidence, documentManagementService,
                new OperatorContext("系统管理员", RoleCode.ADMIN, DataScope.ALL),
                receiver(),
                new OperatorContext("操作工 李华", RoleCode.OPERATOR, DataScope.ALL),
                new OperatorContext("质检员 周娜", RoleCode.QUALITY_INSPECTOR, DataScope.ALL),
                new OperatorContext("库管员 何琳", RoleCode.WAREHOUSE_KEEPER, DataScope.ALL),
                new OperatorContext("车间主管", RoleCode.WORKSHOP_LEADER, DataScope.CUSTOMER_RANGE, false, "", List.of("恒信珠宝")),
                new OperatorContext("其他客户收货", RoleCode.RECEIVER, DataScope.CUSTOMER_RANGE, false, "", List.of("雅辰饰品"))
        );
    }

    private DocumentCaptureService capture(
            BusinessDocumentRepository business,
            DocumentManagementRepository repository,
            long photoMax,
            long videoMax
    ) {
        AttachmentStorageProperties storageProperties = new AttachmentStorageProperties();
        storageProperties.setLocalRoot(tempDir.toString());
        DocumentCaptureProperties captureProperties = new DocumentCaptureProperties();
        captureProperties.setPhotoMaxBytes(photoMax);
        captureProperties.setVideoMaxBytes(videoMax);
        LocalAttachmentObjectStorage storage = new LocalAttachmentObjectStorage(storageProperties);
        return new DocumentCaptureService(
                repository, storage, new DocumentManagementService(repository, storage), business, captureProperties
        );
    }

    private long fileCount() {
        if (!Files.exists(tempDir)) {
            return 0;
        }
        try (var files = Files.walk(tempDir)) {
            return files.filter(Files::isRegularFile).count();
        } catch (java.io.IOException ex) {
            throw new java.io.UncheckedIOException(ex);
        }
    }

    private OperatorContext receiver() {
        return new OperatorContext("收货员 何琴", RoleCode.RECEIVER, DataScope.ALL);
    }

    private MotherOrderRecord mother(String code, String ledgerCode) {
        return new MotherOrderRecord(
                code, 1001L, 1001L, "MAT-S3", "2026-10-12", "顺丰", "SF-S3", "",
                "收货员 何琴", "收货员 何琴", "戒托", "CUSTOMER", true, "RAW",
                BigDecimal.ONE, "件", new BigDecimal("1.500"), "RAW_WAITING", ledgerCode,
                "测试", null, "", "收货员 何琴", "", "", "INBOUND_CONFIRMED",
                "2026-10-12T10:00:00", "抛光", "2026-10-20", "恒信珠宝"
        );
    }

    private ChildWorkOrderRecord child() {
        return new ChildWorkOrderRecord(
                CHILD, MOTHER, 1001L, "恒信珠宝", "抛光", "2026-10-20", "MAT-S3", "戒托",
                BigDecimal.ONE, "件", new BigDecimal("1.500"), "WIP", "抛光", "VER-S3",
                "STK-SPLIT-S3", "车间主管", "2026-10-12 10:30:00"
        );
    }

    private OutboundOrderRecord outbound() {
        return new OutboundOrderRecord(
                SHIPMENT, MOTHER, CHILD, 1001L, 1001L, "MAT-S3", "FG", "CUST",
                "2026-10-12", "成品", BigDecimal.ONE, "件", new BigDecimal("1.500"),
                "顺丰", "YT-S3", "", "库管员 何琳", "", null, "", "库管员 何琳", "", "",
                "SHIPPED", SHIP_LEDGER, "2026-10-12 16:00:00"
        );
    }

    private JdbcDataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private void createDocumentSchema(JdbcDataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table doc_package (
                        id bigint primary key auto_increment,
                        code varchar(80) not null unique,
                        title varchar(120) not null,
                        category varchar(50) not null,
                        business_code varchar(80) not null,
                        owner_name varchar(80) not null,
                        purpose varchar(500) not null,
                        required_evidence int not null,
                        linked_evidence int not null,
                        status varchar(50) not null,
                        retention_policy varchar(200) not null,
                        last_updated varchar(30) not null,
                        tone varchar(20) not null
                    )
                    """);
            statement.execute("""
                    create table doc_evidence_attachment (
                        id bigint primary key auto_increment,
                        code varchar(80) not null unique,
                        document_code varchar(80) not null,
                        business_type varchar(50) not null,
                        business_code varchar(80) not null,
                        file_name varchar(200) not null,
                        media_type varchar(80) not null,
                        storage_location varchar(500) not null,
                        storage_policy varchar(120) not null,
                        source varchar(80) not null,
                        status varchar(50) not null,
                        owner_name varchar(80) not null,
                        checksum varchar(120) not null,
                        uploaded_at varchar(30) not null,
                        fallback_action varchar(300) not null,
                        tone varchar(20) not null
                    )
                    """);
            statement.execute("""
                    create table doc_audit_event (
                        id bigint primary key auto_increment,
                        event_code varchar(80) not null unique,
                        document_code varchar(80) not null,
                        event_time varchar(30) not null,
                        action varchar(80) not null,
                        operator_name varchar(80) not null,
                        detail varchar(500) not null,
                        tone varchar(20) not null
                    )
                    """);
            String sql = Files.readString(Path.of("src/main/resources/db/mysql/V42__m3_business_evidence_link.sql"));
            for (String part : MigrationCatalog.statements(sql)) {
                statement.execute(part);
            }
        }
    }

    private int count(JdbcDataSource dataSource, String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private record Fixture(
            InMemoryBusinessDocumentRepository business,
            DocumentManagementRepository documents,
            BusinessEvidenceLinkRepository links,
            StockLedgerService stocks,
            InMemoryStockLedgerRepository ledgerRepository,
            DocumentCaptureService capture,
            BusinessEvidenceService evidence,
            DocumentManagementService documentManagementService,
            OperatorContext admin,
            OperatorContext receiver,
            OperatorContext operator,
            OperatorContext inspector,
            OperatorContext keeper,
            OperatorContext leader,
            OperatorContext outsider
    ) {
    }

    private static final class FailOnceRepository implements DocumentManagementRepository {
        private final DocumentManagementRepository delegate;
        private boolean failAttachment = true;

        private FailOnceRepository(DocumentManagementRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public List<DocumentPackage> packages() {
            return delegate.packages();
        }

        @Override
        public List<EvidenceAttachment> attachments() {
            return delegate.attachments();
        }

        @Override
        public List<DocumentAuditEvent> auditEvents() {
            return delegate.auditEvents();
        }

        @Override
        public void savePackage(DocumentPackage documentPackage) {
            delegate.savePackage(documentPackage);
        }

        @Override
        public void saveAttachment(EvidenceAttachment attachment) {
            if (failAttachment) {
                failAttachment = false;
                throw new IllegalStateException("metadata down");
            }
            delegate.saveAttachment(attachment);
        }

        @Override
        public void saveAuditEvent(DocumentAuditEvent auditEvent) {
            delegate.saveAuditEvent(auditEvent);
        }
    }
}
