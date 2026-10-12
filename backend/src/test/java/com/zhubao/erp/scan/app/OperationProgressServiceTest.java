package com.zhubao.erp.scan.app;

import com.zhubao.erp.business.app.BusinessDataAccess;
import com.zhubao.erp.business.app.BusinessDocumentService;
import com.zhubao.erp.business.app.BusinessOperationService;
import com.zhubao.erp.business.app.MotherOrderSplitService;
import com.zhubao.erp.business.app.ProcessStationService;
import com.zhubao.erp.business.app.QualityRequirementService;
import com.zhubao.erp.business.domain.ConfirmInboundCommand;
import com.zhubao.erp.business.domain.OutboundOrderRecord;
import com.zhubao.erp.business.domain.ProcessStationHandoff;
import com.zhubao.erp.business.domain.QualityCheckCommand;
import com.zhubao.erp.business.domain.QualityCheckLineCommand;
import com.zhubao.erp.business.domain.QualityRequirementCheck;
import com.zhubao.erp.business.domain.QualityVerdicts;
import com.zhubao.erp.business.domain.SplitMotherOrderCommand;
import com.zhubao.erp.business.domain.SplitMotherOrderLine;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.app.BusinessEvidenceService;
import com.zhubao.erp.document.app.DocumentManagementService;
import com.zhubao.erp.document.domain.BindEvidenceCommand;
import com.zhubao.erp.document.domain.DocumentPackage;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.document.domain.EvidenceCodes;
import com.zhubao.erp.document.infra.AttachmentStorageProperties;
import com.zhubao.erp.document.infra.InMemoryBusinessEvidenceLinkRepository;
import com.zhubao.erp.document.infra.InMemoryDocumentManagementRepository;
import com.zhubao.erp.document.infra.LocalAttachmentObjectStorage;
import com.zhubao.erp.finance.domain.ChargeUnit;
import com.zhubao.erp.finance.domain.PayrollLine;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.scan.domain.OperationProgressRepository;
import com.zhubao.erp.scan.domain.SaveOperationProgressCommand;
import com.zhubao.erp.scan.domain.ScanContextRequest;
import com.zhubao.erp.scan.domain.ScanContextResponse;
import com.zhubao.erp.scan.domain.ScanIntent;
import com.zhubao.erp.scan.domain.ScanProgressRecord;
import com.zhubao.erp.scan.infra.InMemoryOperationProgressRepository;
import com.zhubao.erp.scan.infra.JdbcOperationProgressRepository;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.AuthUserAccount;
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
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 过程说明只追加，不改库存、工资、工序交付、质检结论或出货单。内存和 JDBC 使用同一套行为。
 */
class OperationProgressServiceTest {

    private static final String THREE_LINES = "内壁刻字\n镶一颗主石\n镜面抛光";

    @TempDir
    Path tempDir;

    @Test
    void memoryAppendsNotesWithoutTouchingFormalFacts() {
        assertProgressBehavior(new InMemoryOperationProgressRepository());
    }

    @Test
    void jdbcAppendsNotesWithoutTouchingFormalFacts() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        String sql = Files.readString(Path.of("src/main/resources/db/mysql/V43__m3_operation_progress.sql"), StandardCharsets.UTF_8);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String item : MigrationCatalog.statements(sql)) {
                statement.execute(item);
            }
        }
        assertProgressBehavior(new JdbcOperationProgressRepository(dataSource));
    }

    private void assertProgressBehavior(OperationProgressRepository progress) {
        InMemoryBusinessDocumentRepository repository = new InMemoryBusinessDocumentRepository();
        InMemoryStockLedgerRepository stockRepository = new InMemoryStockLedgerRepository();
        StockLedgerService stocks = new StockLedgerService(stockRepository);
        InMemoryFinanceRepository finance = new InMemoryFinanceRepository();
        finance.replacePayroll("2026-10", List.of(payrollLine()));
        BusinessOperationService operations = new BusinessOperationService(stocks, null, repository);
        MotherOrderSplitService splits = new MotherOrderSplitService(repository, stocks);
        ScanCodeParser parser = new ScanCodeParser();
        BusinessDataAccess access = new BusinessDataAccess(repository, stocks);
        PermissionGuard permissions = new PermissionGuard();
        ProcessStationService stations = new ProcessStationService(repository, stocks, operations, parser, access);
        QualityRequirementService quality = new QualityRequirementService(repository, stocks, operations, parser, access);
        OperationProgressService service = new OperationProgressService(progress, repository, access, permissions);
        ScanContextService context = new ScanContextService(
                new BusinessDocumentService(repository), stocks, access, stations, quality, permissions, parser, progress
        );
        InMemoryDocumentManagementRepository documents = new InMemoryDocumentManagementRepository();
        BusinessEvidenceService evidence = evidenceService(repository, stocks, access, permissions, progress, documents);

        inbound(operations, "MD-S4-SPLIT", 1001L, "恒信珠宝");
        var split = splits.split(new SplitMotherOrderCommand("MD-S4-SPLIT", "何琳", List.of(
                new SplitMotherOrderLine(new BigDecimal("4"), new BigDecimal("8"), null),
                new SplitMotherOrderLine(new BigDecimal("6"), new BigDecimal("12"), null)
        )));
        String child = split.children().get(0).code();
        String sibling = split.children().get(1).code();
        inbound(operations, "MD-S4-KEEP", 1001L, "恒信珠宝");
        splits.keep("MD-S4-KEEP", "何琳");
        inbound(operations, "MD-S4-OTHER", 2002L, "其他客户");
        splits.keep("MD-S4-OTHER", "何琳");
        inbound(operations, "MD-S4-OTHER-MOTHER", 1001L, "恒信珠宝");
        splits.keep("MD-S4-OTHER-MOTHER", "何琳");

        AuthUserAccount carver = worker("雕刻");
        AuthUserAccount setter = worker("镶嵌");
        AuthUserAccount inspector = account("qc-s4", "质检员 周娜", RoleCode.QUALITY_INSPECTOR, DataScope.ALL, List.of());
        AuthUserAccount warehouse = account("wh-s4", "库管员 何琳", RoleCode.WAREHOUSE_KEEPER, DataScope.ALL, List.of());
        AuthUserAccount outsider = account("out-s4", "外部库管", RoleCode.WAREHOUSE_KEEPER, DataScope.CUSTOMER_RANGE, List.of("其他客户"));

        assertThat(context.query(request("JG|work_order|" + child), admin()).progress().progressRecords()).isEmpty();
        FormalFacts baseline = facts(repository, stocks, finance, "MD-S4-SPLIT");

        ScanProgressRecord first = service.save(command(child, "MD-S4-SPLIT", "PRODUCTION", "雕刻", "", "  第一刀  ", "req-child-0001"), carver);
        ScanProgressRecord second = service.save(command(child, "MD-S4-SPLIT", "production", "雕刻", "", "第二刀", "req-child-0002"), carver);
        ScanProgressRecord retried = service.save(command(child, "MD-S4-SPLIT", "PRODUCTION", "雕刻", "", "第一刀", "req-child-0001"), carver);
        ScanProgressRecord changed = service.save(command(child, "MD-S4-SPLIT", "PRODUCTION", "雕刻", "", "第一刀改口", "req-child-0001"), carver);
        ScanProgressRecord anotherRequest = service.save(command(child, "MD-S4-SPLIT", "PRODUCTION", "雕刻", "", "第一刀", "req-child-0009"), carver);

        assertThat(first.code()).startsWith("PG-");
        assertThat(first.stage()).isEqualTo("PRODUCTION");
        assertThat(first.note()).isEqualTo("第一刀");
        assertThat(first.operatorId()).isEqualTo("op-雕刻");
        assertThat(first.operatorName()).isEqualTo("雕刻工");
        assertThat(first.operatorRole()).isEqualTo("OPERATOR");
        assertThat(first.supplementary()).isFalse();
        assertThat(retried).isEqualTo(first);
        assertThat(List.of(first.code(), second.code(), changed.code(), anotherRequest.code())).doesNotHaveDuplicates();
        assertThat(progress.findByTarget(EvidenceCodes.WORK_ORDER, child)).hasSize(4);
        assertUnchanged(baseline, facts(repository, stocks, finance, "MD-S4-SPLIT"));

        assertThatThrownBy(() -> service.save(command(sibling, "MD-S4-SPLIT", "PRODUCTION", "雕刻", "", "另一张", "req-child-0001"), carver))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该请求不能跨单重放");
        assertThat(progress.findByTarget(EvidenceCodes.WORK_ORDER, sibling)).isEmpty();
        assertThatThrownBy(() -> service.save(command(child, "MD-S4-SPLIT", "PRODUCTION", "抛光", "", "跨岗位", "req-cross-station"), carver))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前账号不能做这道工序");
        assertThatThrownBy(() -> service.save(command(child, "MD-S4-SPLIT", "PRODUCTION", "镶嵌", "", "跨账号", "req-child-0002"), setter))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该请求不能跨账号重放");
        assertThatThrownBy(() -> service.save(command(child, "MD-S4-SPLIT", "QUALITY", "", "1", "质检员来写", "req-quality-deny"), carver))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("无权");
        assertThatThrownBy(() -> service.save(command(child, "MD-S4-SPLIT", "SHIPPING", "", "", "岗位来发货", "req-ship-deny"), carver))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("无权");
        assertThatThrownBy(() -> service.save(command(child, "MD-S4-SPLIT", "PRODUCTION", "雕刻", "", "质检来报工", "req-qc-prod"), inspector))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("无权");
        assertThatThrownBy(() -> service.save(command(child, "MD-S4-SPLIT", "PRODUCTION", "雕刻", "", "库管来报工", "req-wh-prod"), warehouse))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("无权");
        assertThatThrownBy(() -> service.save(command("MD-S4-SPLIT", "MD-S4-SPLIT", "PRODUCTION", "雕刻", "", "记到母单", "req-split-mother"), carver))
                .isInstanceOf(BusinessException.class)
                .hasMessage("已拆母单必须记到选中的子单");
        assertThatThrownBy(() -> service.save(command(child, "MD-S4-OTHER-MOTHER", "PRODUCTION", "雕刻", "", "串母单", "req-cross-mother"), carver))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不能跨母子单记录");
        assertThatThrownBy(() -> service.save(command(child, "MD-S4-SPLIT", "outbound_shipment", "", "", "伪装出货", "req-fake-ship"), warehouse))
                .isInstanceOf(BusinessException.class)
                .hasMessage("发货准备不是出货单");
        assertThat(progress.findByTarget(EvidenceCodes.MOTHER_ORDER, "MD-S4-SPLIT")).isEmpty();
        assertThat(progress.findByTarget(EvidenceCodes.WORK_ORDER, child)).hasSize(4);
        assertUnchanged(baseline, facts(repository, stocks, finance, "MD-S4-SPLIT"));

        ScanProgressRecord kept = service.save(command("MD-S4-KEEP", "MD-S4-KEEP", "PRODUCTION", "雕刻", "", "不拆母单也可以记", "req-keep-0001"), carver);
        assertThat(kept.targetType()).isEqualTo(EvidenceCodes.MOTHER_ORDER);
        assertThat(kept.supplementary()).isFalse();

        ScanProgressRecord shipping = service.save(command(child, "MD-S4-SPLIT", "SHIPPING", "", "", "正在打包", "req-ship-0001"), warehouse);
        assertThat(shipping.stage()).isEqualTo("SHIPPING");
        assertThat(shipping.supplementary()).isFalse();
        assertThat(shipping.operatorId()).isEqualTo("wh-s4");
        assertThat(shipping.operatorName()).isEqualTo("库管员 何琳");
        assertThat(repository.listOutboundOrders()).isEmpty();
        assertThatThrownBy(() -> service.save(command("MD-S4-OTHER", "MD-S4-OTHER", "SHIPPING", "", "", "拿别人的请求", "req-ship-0001"), outsider))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessage("该请求不能跨客户重放");
        assertThat(progress.findByTarget(EvidenceCodes.MOTHER_ORDER, "MD-S4-OTHER")).isEmpty();

        int beforeBind = progress.findByTarget(EvidenceCodes.WORK_ORDER, child).size();
        saveAttachment(documents, child, "ATT-S4-1");
        assertThatThrownBy(() -> evidence.bind(
                bind("ATT-S4-1", child, "PRODUCTION", "PROCESS", EvidenceCodes.PROGRESS_NOTE, "PG-MISSING", "雕刻", ""),
                BusinessDataAccess.contextOf(carver)
        )).isInstanceOf(BusinessException.class).hasMessage("过程记录不存在");
        assertThat(progress.findByTarget(EvidenceCodes.WORK_ORDER, child)).hasSize(beforeBind);
        evidence.bind(
                bind("ATT-S4-1", child, "PRODUCTION", "PROCESS", EvidenceCodes.PROGRESS_NOTE, first.code(), "雕刻", ""),
                BusinessDataAccess.contextOf(carver)
        );
        assertThat(progress.findByTarget(EvidenceCodes.WORK_ORDER, child)).hasSize(beforeBind);
        assertThat(service.save(command(child, "MD-S4-SPLIT", "PRODUCTION", "雕刻", "", "第一刀", "req-child-0001"), carver).code())
                .isEqualTo(first.code());

        ScanContextResponse loaded = context.query(request("JG|work_order|" + child), admin());
        assertThat(loaded.progress().progressRecords()).extracting(ScanProgressRecord::note)
                .contains("第一刀", "第二刀", "第一刀改口", "正在打包");
        assertThat(loaded.progress().progressRecords()).extracting(ScanProgressRecord::stage)
                .contains("PRODUCTION", "SHIPPING")
                .doesNotContain("outbound_shipment", "已发货");
        assertThat(context.query(request("JG|mother|MD-S4-SPLIT"), admin()).progress().progressRecords()).isEmpty();
        assertThat(context.query(request("JG|mother|MD-S4-KEEP"), admin()).progress().progressRecords())
                .extracting(ScanProgressRecord::code)
                .containsExactly(kept.code());
        assertUnchanged(baseline, facts(repository, stocks, finance, "MD-S4-SPLIT"));

        String sticker = "JG|work_order|" + child;
        stations.report(sticker, "雕刻", carver);
        FormalFacts afterHandoff = facts(repository, stocks, finance, "MD-S4-SPLIT");
        ScanProgressRecord supplement = service.save(command(child, "MD-S4-SPLIT", "PRODUCTION", "雕刻", "", "交付后补一句", "req-after-handoff"), carver);
        ScanProgressRecord stillOpen = service.save(command(child, "MD-S4-SPLIT", "PRODUCTION", "镶嵌", "", "镶嵌还在做", "req-setting-open"), setter);
        assertThat(supplement.supplementary()).isTrue();
        assertThat(stillOpen.supplementary()).isFalse();
        assertThat(repository.findMotherOrderByCode("MD-S4-SPLIT").orElseThrow().status()).isEqualTo("INBOUND_CONFIRMED");
        assertUnchanged(afterHandoff, facts(repository, stocks, finance, "MD-S4-SPLIT"));

        stations.report(sticker, "镶嵌", setter);
        stations.report(sticker, "抛光", worker("抛光"));
        quality.submit(new QualityCheckCommand(sticker, "别人写的姓名", List.of(
                new QualityCheckLineCommand(1, QualityVerdicts.FAIL, "刻字错了"),
                new QualityCheckLineCommand(2, QualityVerdicts.PASS, ""),
                new QualityCheckLineCommand(3, QualityVerdicts.PASS, "")
        )), inspector);
        QualityRequirementCheck submitted = repository.findQualityRequirementCheck(child).orElseThrow();
        FormalFacts afterQuality = facts(repository, stocks, finance, "MD-S4-SPLIT");
        ScanProgressRecord qualityNote = service.save(command(child, "MD-S4-SPLIT", "QUALITY", "", "1", "初检后补充说明", "req-quality-0001"), inspector);
        assertThat(qualityNote.supplementary()).isTrue();
        assertThat(qualityNote.stage()).isEqualTo("QUALITY");
        assertThat(qualityNote.operatorName()).isEqualTo("质检员 周娜");
        assertThat(repository.findQualityRequirementCheck(child).orElseThrow().outcome()).isEqualTo(submitted.outcome());
        assertUnchanged(afterQuality, facts(repository, stocks, finance, "MD-S4-SPLIT"));
        saveAttachment(documents, child, "ATT-S4-QC");
        evidence.bind(
                bind("ATT-S4-QC", child, "QUALITY", "CHECK", EvidenceCodes.QUALITY_CHECK, submitted.qualityCaseCode(), "", "1"),
                BusinessDataAccess.contextOf(inspector)
        );
        assertThat(repository.findQualityRequirementCheck(child).orElseThrow().outcome()).isEqualTo(QualityVerdicts.FAIL);
        assertUnchanged(afterQuality, facts(repository, stocks, finance, "MD-S4-SPLIT"));

        repository.saveOutboundOrder(outbound(child));
        FormalFacts afterShipment = facts(repository, stocks, finance, "MD-S4-SPLIT");
        ScanProgressRecord prepared = service.save(command(child, "MD-S4-SPLIT", "SHIPPING", "", "", "已经发过仍只是补记", "req-ship-0002"), warehouse);
        assertThat(prepared.supplementary()).isTrue();
        assertThat(prepared.stage()).isEqualTo("SHIPPING");
        assertThat(repository.findOutboundOrderByCode("OUT-S4-1").orElseThrow().status()).isEqualTo("SHIPPED");
        assertUnchanged(afterShipment, facts(repository, stocks, finance, "MD-S4-SPLIT"));
        assertThat(finance.listPayrollLines()).containsExactly(payrollLine());
    }

    private BusinessEvidenceService evidenceService(
            InMemoryBusinessDocumentRepository repository,
            StockLedgerService stocks,
            BusinessDataAccess access,
            PermissionGuard permissions,
            OperationProgressRepository progress,
            InMemoryDocumentManagementRepository documents
    ) {
        AttachmentStorageProperties storageProperties = new AttachmentStorageProperties();
        storageProperties.setLocalRoot(tempDir.toString());
        DocumentManagementService documentManagementService = new DocumentManagementService(
                documents, new LocalAttachmentObjectStorage(storageProperties)
        );
        return new BusinessEvidenceService(
                documents,
                new InMemoryBusinessEvidenceLinkRepository(),
                repository,
                stocks,
                documentManagementService,
                access,
                permissions,
                progress
        );
    }

    private void saveAttachment(InMemoryDocumentManagementRepository documents, String businessCode, String code) {
        if (documents.packages().stream().noneMatch(item -> item.code().equals("DOC-" + businessCode))) {
            documents.savePackage(new DocumentPackage(
                    "DOC-" + businessCode, businessCode, "过程", businessCode, "现场", "过程证据",
                    0, 0, "采集中", "本地", "2026-10-12 10:00:00", "green"
            ));
        }
        documents.saveAttachment(new EvidenceAttachment(
                code, "DOC-" + businessCode, "子单", businessCode, code + ".jpg", "image/jpeg",
                "local://evidence/" + code + ".jpg", "hot", "现场补拍", "已上传",
                "雕刻工", "sha256:" + code, "2026-10-12 10:00:00", "无", "green", EvidenceCodes.WORK_ORDER
        ));
    }

    private BindEvidenceCommand bind(
            String attachmentCode,
            String targetCode,
            String stage,
            String scene,
            String operationType,
            String operationCode,
            String processName,
            String clauseNo
    ) {
        return new BindEvidenceCommand(
                attachmentCode, EvidenceCodes.WORK_ORDER, targetCode, stage, scene,
                operationType, operationCode, processName, clauseNo
        );
    }

    private SaveOperationProgressCommand command(
            String targetCode,
            String motherOrderCode,
            String stage,
            String process,
            String clauseNo,
            String note,
            String requestId
    ) {
        String targetType = targetCode.startsWith("MD-") ? EvidenceCodes.MOTHER_ORDER : EvidenceCodes.WORK_ORDER;
        return new SaveOperationProgressCommand(targetType, targetCode, motherOrderCode, stage, process, clauseNo, note, requestId);
    }

    private ScanContextRequest request(String content) {
        return new ScanContextRequest(content, null, ScanIntent.LOOKUP);
    }

    private void inbound(BusinessOperationService operations, String code, long customerId, String customerName) {
        operations.confirmInbound(new ConfirmInboundCommand(
                code, customerId, customerId, "AU9999", "2026-10-08", "顺丰", "SF" + code.replace("-", ""),
                "", "何琳", "何琳", "戒托", "", null, "", "RAW-CUSTOMER",
                new BigDecimal("10"), "件", new BigDecimal("20"), "何琳", "", "测试入库",
                THREE_LINES, "2026-10-20", customerName
        ));
    }

    private OutboundOrderRecord outbound(String child) {
        return new OutboundOrderRecord(
                "OUT-S4-1", "MD-S4-SPLIT", child, 1001L, 1001L, "AU9999", "FG", "CUST",
                "2026-10-12", "成品", BigDecimal.ONE, "件", new BigDecimal("1.500"),
                "顺丰", "YT-S4", "", "库管员 何琳", "", null, "", "库管员 何琳", "", "",
                "SHIPPED", "STK-OUT-S4", "2026-10-12 16:00:00"
        );
    }

    private PayrollLine payrollLine() {
        return new PayrollLine(
                "PAY-S4-1", "2026-10", "雕刻工", "ZD-S4", "雕刻", "MD-S4-SPLIT", 1001L,
                BigDecimal.ONE, ChargeUnit.PIECE, new BigDecimal("10.00"), new BigDecimal("10.00"),
                BigDecimal.ZERO, new BigDecimal("10.00"), false, "PAY|ZD-S4|雕刻"
        );
    }

    private FormalFacts facts(
            InMemoryBusinessDocumentRepository repository,
            StockLedgerService stocks,
            InMemoryFinanceRepository finance,
            String motherCode
    ) {
        return new FormalFacts(
                stocks.ledgers(),
                stocks.balances(),
                repository.listAllProcessStationHandoffs(),
                repository.listAllQualityRequirementChecks(),
                repository.listOutboundOrders(),
                finance.listPayrollLines(),
                repository.findMotherOrderByCode(motherCode).orElseThrow().status()
        );
    }

    private void assertUnchanged(FormalFacts before, FormalFacts after) {
        assertThat(after.ledgers()).containsExactlyInAnyOrderElementsOf(before.ledgers());
        assertThat(after.balances()).containsExactlyInAnyOrderElementsOf(before.balances());
        assertThat(after.handoffs()).containsExactlyInAnyOrderElementsOf(before.handoffs());
        assertThat(after.checks()).containsExactlyInAnyOrderElementsOf(before.checks());
        assertThat(after.shipments()).containsExactlyInAnyOrderElementsOf(before.shipments());
        assertThat(after.payroll()).containsExactlyElementsOf(before.payroll());
        assertThat(after.motherStatus()).isEqualTo(before.motherStatus());
    }

    private AuthUserAccount admin() {
        return account("admin-s4", "系统管理员", RoleCode.ADMIN, DataScope.ALL, List.of());
    }

    private AuthUserAccount worker(String process) {
        return account("op-" + process, process + "工", RoleCode.OPERATOR, DataScope.ALL, List.of())
                .withAllowedProcesses(List.of(process));
    }

    private AuthUserAccount account(String username, String operator, RoleCode role, DataScope scope, List<String> customers) {
        return new AuthUserAccount(username, "hash", operator, role, scope, true, false, customers);
    }

    private record FormalFacts(
            List<StockLedgerEntry> ledgers,
            List<StockBalance> balances,
            List<ProcessStationHandoff> handoffs,
            List<QualityRequirementCheck> checks,
            List<OutboundOrderRecord> shipments,
            List<PayrollLine> payroll,
            String motherStatus
    ) {
    }
}
