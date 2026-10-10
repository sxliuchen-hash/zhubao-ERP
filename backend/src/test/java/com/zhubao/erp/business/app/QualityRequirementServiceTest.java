package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ConfirmInboundCommand;
import com.zhubao.erp.business.domain.ProcessStationHandoff;
import com.zhubao.erp.business.domain.ProcessingRequirementClauses;
import com.zhubao.erp.business.domain.QualityCheckCommand;
import com.zhubao.erp.business.domain.QualityCheckLine;
import com.zhubao.erp.business.domain.QualityCheckLineCommand;
import com.zhubao.erp.business.domain.QualityCheckResult;
import com.zhubao.erp.business.domain.QualityCheckView;
import com.zhubao.erp.business.domain.QualityRequirementCheck;
import com.zhubao.erp.business.domain.QualityReworkResult;
import com.zhubao.erp.business.domain.QualityVerdicts;
import com.zhubao.erp.business.domain.SplitMotherOrderCommand;
import com.zhubao.erp.business.domain.SplitMotherOrderLine;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.scan.app.ScanCodeParser;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 质检对照加工要求：逐条有结果才能提交，质检员取登录账号，不合格才冻结并可开返工。
 */
class QualityRequirementServiceTest {

    private static final String THREE_LINES = "内壁刻字\n镶一颗主石\n\n镜面抛光";

    private InMemoryStockLedgerRepository stockRepository;
    private StockLedgerService stockLedgerService;
    private InMemoryBusinessDocumentRepository documents;
    private BusinessOperationService operations;
    private MotherOrderSplitService splitService;
    private ProcessStationService stationService;
    private QualityRequirementService qualityService;

    @BeforeEach
    void setUp() {
        stockRepository = new InMemoryStockLedgerRepository();
        stockLedgerService = new StockLedgerService(stockRepository);
        documents = new InMemoryBusinessDocumentRepository();
        operations = new BusinessOperationService(stockLedgerService, null, documents);
        splitService = new MotherOrderSplitService(documents, stockLedgerService);
        ScanCodeParser parser = new ScanCodeParser();
        stationService = new ProcessStationService(documents, stockLedgerService, operations, parser);
        qualityService = new QualityRequirementService(documents, stockLedgerService, operations, parser);
    }

    @Test
    void clausesSplitOnNewlinesAndASingleParagraphStaysOneClause() {
        assertThat(ProcessingRequirementClauses.split(THREE_LINES)).containsExactly("内壁刻字", "镶一颗主石", "镜面抛光");
        assertThat(ProcessingRequirementClauses.split("甲\r\n\r\n乙")).containsExactly("甲", "乙");
        assertThat(ProcessingRequirementClauses.split("整段算一条，不要拆开")).containsExactly("整段算一条，不要拆开");
        assertThat(ProcessingRequirementClauses.split("  \n  ")).isEmpty();
    }

    @Test
    void threeClausesMustAllBeJudgedAndInspectorComesFromTheSignedInAccount() {
        String childCode = finishedChild("MD-QC-LINES", THREE_LINES);
        String sticker = "JG|work_order|" + childCode;
        AuthUserAccount inspector = qc();

        QualityCheckView view = qualityService.lookup(sticker, inspector);

        assertThat(view.ready()).isTrue();
        assertThat(view.lines()).extracting(QualityCheckLine::clause).containsExactly("内壁刻字", "镶一颗主石", "镜面抛光");
        assertThat(view.inspector()).isEqualTo("质检员 周娜");
        assertThat(view.qualityCaseCode()).isBlank();
        assertThatThrownBy(() -> qualityService.lookup(sticker, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先登录");

        assertThatThrownBy(() -> qualityService.submit(new QualityCheckCommand(
                sticker,
                "别人",
                List.of(line(1, QualityVerdicts.PASS, ""), line(2, QualityVerdicts.PASS, ""))
        ), inspector))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("都要有结果");
        assertThat(documents.findQualityRequirementCheck(childCode)).isEmpty();

        assertThatThrownBy(() -> qualityService.submit(command(sticker, "别人", List.of(
                line(1, QualityVerdicts.PASS, ""),
                line(2, QualityVerdicts.CONCESSION, "  "),
                line(3, QualityVerdicts.PASS, "")
        )), inspector))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须写原因");

        QualityCheckResult submitted = qualityService.submit(command(sticker, "别人", List.of(
                line(1, QualityVerdicts.PASS, ""),
                line(2, QualityVerdicts.PASS, "可以不写但写了也留着"),
                line(3, QualityVerdicts.PASS, "")
        )), inspector);

        assertThat(submitted.inspector()).isEqualTo("质检员 周娜");
        assertThat(submitted.outcome()).isEqualTo(QualityVerdicts.PASS);
        assertThat(submitted.frozen()).isFalse();
        assertThat(submitted.qualityCaseCode()).isEqualTo("QC-" + childCode);
        assertThat(submitted.motherOrderCode()).isEqualTo("MD-QC-LINES");
        assertThat(submitted.orderCode()).isEqualTo(childCode);
        QualityRequirementCheck stored = documents.findQualityRequirementCheck(childCode).orElseThrow();
        assertThat(stored.inspector()).isEqualTo("质检员 周娜");
        assertThat(stored.inspector()).isNotEqualTo("别人");
        assertThat(quantity(childCode, StockStatus.SEMI_FINISHED)).isEqualByComparingTo("4");
        assertThat(quantity(childCode, StockStatus.FROZEN)).isEqualByComparingTo("0");
        assertThat(stockLedgerService.ledgers()).extracting(StockLedgerEntry::documentType)
                .doesNotContain("quality_freeze");
        assertThat(stockLedgerService.ledgers()).extracting(StockLedgerEntry::remark)
                .noneMatch(remark -> remark != null && remark.contains("别人"));
        assertThatThrownBy(() -> qualityService.submit(command(sticker, "质检员 周娜", List.of(
                line(1, QualityVerdicts.PASS, ""),
                line(2, QualityVerdicts.PASS, ""),
                line(3, QualityVerdicts.PASS, "")
        )), inspector))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已经做过质检对照");
        assertThatThrownBy(() -> qualityService.openRework(sticker, inspector))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能开返工");
    }

    @Test
    void unfinishedLastProcessOrStockThatIsNotSemiFinishedCannotBeSubmitted() {
        String childCode = splitOneChild("MD-QC-EARLY", THREE_LINES);
        String sticker = "JG|work_order|" + childCode;
        AuthUserAccount inspector = qc();

        QualityCheckView beforeFinish = qualityService.lookup(sticker, inspector);
        assertThat(beforeFinish.ready()).isFalse();
        assertThat(beforeFinish.blockReason()).contains("最后一道工序还没完成");
        assertThatThrownBy(() -> qualityService.submit(allPass(sticker), inspector))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("最后一道工序还没完成");
        assertThat(quantity(childCode, StockStatus.SEMI_FINISHED)).isEqualByComparingTo("0");

        documents.saveProcessStationHandoff(new ProcessStationHandoff(childCode, "雕刻", "雕刻工", "STK-1", "2026-10-08T10:00:00"));
        documents.saveProcessStationHandoff(new ProcessStationHandoff(childCode, "镶嵌", "镶嵌工", "STK-2", "2026-10-08T11:00:00"));
        documents.saveProcessStationHandoff(new ProcessStationHandoff(childCode, "抛光", "抛光工", "STK-3", "2026-10-08T12:00:00"));

        QualityCheckView stillWip = qualityService.lookup(sticker, inspector);
        assertThat(stillWip.ready()).isFalse();
        assertThat(stillWip.blockReason()).contains("还不是半成品");
        assertThatThrownBy(() -> qualityService.submit(allPass(sticker), inspector))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("还不是半成品");
        assertThat(documents.findQualityRequirementCheck(childCode)).isEmpty();
        assertThat(stockLedgerService.ledgers()).extracting(StockLedgerEntry::documentType)
                .doesNotContain("quality_freeze");
    }

    @Test
    void failedClauseFreezesSemiFinishedAndReworkStaysLinkedToTheOriginalOrder() {
        String childCode = finishedChild("MD-QC-FAIL", THREE_LINES);
        String sticker = "JG|work_order|" + childCode;
        AuthUserAccount inspector = qc();

        QualityCheckResult submitted = qualityService.submit(command(sticker, "车间组长 马杰", List.of(
                line(1, QualityVerdicts.PASS, ""),
                line(2, QualityVerdicts.FAIL, "石位松"),
                line(3, QualityVerdicts.CONCESSION, "边角可接受")
        )), inspector);

        assertThat(submitted.inspector()).isEqualTo("质检员 周娜");
        assertThat(submitted.outcome()).isEqualTo(QualityVerdicts.FAIL);
        assertThat(submitted.frozen()).isTrue();
        assertThat(submitted.lines()).extracting(QualityCheckLine::verdict)
                .containsExactly(QualityVerdicts.PASS, QualityVerdicts.FAIL, QualityVerdicts.CONCESSION);
        assertThat(quantity(childCode, StockStatus.SEMI_FINISHED)).isEqualByComparingTo("0");
        assertThat(quantity(childCode, StockStatus.FROZEN)).isEqualByComparingTo("4");
        assertThat(weight(childCode, StockStatus.FROZEN)).isEqualByComparingTo("8");
        StockLedgerEntry freezeLedger = stockLedgerService.ledgers().stream()
                .filter(entry -> "quality_freeze".equals(entry.documentType()))
                .findFirst()
                .orElseThrow();
        assertThat(freezeLedger.documentCode()).isEqualTo(submitted.qualityCaseCode());
        assertThat(freezeLedger.from().stockStatus()).isEqualTo(StockStatus.SEMI_FINISHED);
        assertThat(freezeLedger.to().stockStatus()).isEqualTo(StockStatus.FROZEN);
        assertThat(freezeLedger.from().motherOrderCode()).isEqualTo("MD-QC-FAIL");
        assertThat(freezeLedger.from().workOrderCode()).isEqualTo(childCode);
        assertThat(freezeLedger.remark()).contains("质检员 周娜", "石位松", "MD-QC-FAIL", childCode);
        assertThat(freezeLedger.remark()).doesNotContain("车间组长 马杰");

        QualityReworkResult rework = qualityService.openRework(sticker, inspector);

        assertThat(rework.reworkOrderCode()).isEqualTo("FG-" + childCode);
        assertThat(rework.qualityCaseCode()).isEqualTo(submitted.qualityCaseCode());
        assertThat(rework.orderCode()).isEqualTo(childCode);
        assertThat(rework.motherOrderCode()).isEqualTo("MD-QC-FAIL");
        assertThat(rework.fromStockStatus()).isEqualTo("FROZEN");
        assertThat(rework.toStockStatus()).isEqualTo("REWORK");
        assertThat(quantity(childCode, StockStatus.FROZEN)).isEqualByComparingTo("0");
        assertThat(quantity(childCode, StockStatus.REWORK)).isEqualByComparingTo("4");
        assertThat(weight(childCode, StockStatus.REWORK)).isEqualByComparingTo("8");
        StockLedgerEntry reworkLedger = stockLedgerService.ledgers().stream()
                .filter(entry -> "rework_dispatch".equals(entry.documentType()))
                .findFirst()
                .orElseThrow();
        assertThat(reworkLedger.documentCode()).isEqualTo(rework.reworkOrderCode());
        assertThat(reworkLedger.from().stockStatus()).isEqualTo(StockStatus.FROZEN);
        assertThat(reworkLedger.to().stockStatus()).isEqualTo(StockStatus.REWORK);
        assertThat(reworkLedger.from().motherOrderCode()).isEqualTo("MD-QC-FAIL");
        assertThat(reworkLedger.from().workOrderCode()).isEqualTo(childCode);
        assertThat(reworkLedger.remark()).contains(submitted.qualityCaseCode(), "MD-QC-FAIL", childCode, "质检员 周娜");
        assertThat(documents.findQualityRequirementCheck(childCode).orElseThrow().reworkOrderCode())
                .isEqualTo(rework.reworkOrderCode());
        assertThatThrownBy(() -> qualityService.openRework(sticker, inspector))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已经开过返工");
    }

    @Test
    void concessionIsRecordedWithoutMovingStockToFrozen() {
        String childCode = finishedChild("MD-QC-CONCESSION", THREE_LINES);
        String sticker = "JG|work_order|" + childCode;

        QualityCheckResult submitted = qualityService.submit(command(sticker, "别人", List.of(
                line(1, QualityVerdicts.PASS, ""),
                line(2, QualityVerdicts.CONCESSION, "刻线略浅，客户接受"),
                line(3, QualityVerdicts.PASS, "")
        )), qc());

        assertThat(submitted.inspector()).isEqualTo("质检员 周娜");
        assertThat(submitted.outcome()).isEqualTo(QualityVerdicts.CONCESSION);
        assertThat(submitted.frozen()).isFalse();
        assertThat(submitted.ledgerCode()).isBlank();
        assertThat(quantity(childCode, StockStatus.SEMI_FINISHED)).isEqualByComparingTo("4");
        assertThat(quantity(childCode, StockStatus.FROZEN)).isEqualByComparingTo("0");
        assertThat(stockLedgerService.ledgers()).extracting(StockLedgerEntry::documentType)
                .doesNotContain("quality_freeze");
        QualityRequirementCheck stored = documents.findQualityRequirementCheck(childCode).orElseThrow();
        assertThat(stored.lines()).filteredOn(line -> line.lineNo() == 2)
                .singleElement()
                .satisfies(line -> {
                    assertThat(line.verdict()).isEqualTo(QualityVerdicts.CONCESSION);
                    assertThat(line.reason()).isEqualTo("刻线略浅，客户接受");
                });
        assertThatThrownBy(() -> qualityService.openRework(sticker, qc()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能开返工");
    }

    @Test
    void keptMotherStickerCanBeCheckedAfterTheLastProcess() {
        inbound("MD-QC-KEEP", "10", "20", "整段算一条");
        splitService.keep("MD-QC-KEEP", "何琳");
        String sticker = "JG|mother|MD-QC-KEEP";

        assertThat(qualityService.lookup(sticker, qc()).blockReason()).contains("最后一道工序还没完成");
        finish(sticker);

        QualityCheckView view = qualityService.lookup(sticker, qc());
        assertThat(view.orderCode()).isEqualTo("MD-QC-KEEP");
        assertThat(view.lines()).extracting(QualityCheckLine::clause).containsExactly("整段算一条");
        assertThat(view.ready()).isTrue();

        QualityCheckResult submitted = qualityService.submit(command(sticker, "别人", List.of(
                line(1, QualityVerdicts.PASS, "")
        )), qc());

        assertThat(submitted.inspector()).isEqualTo("质检员 周娜");
        assertThat(submitted.orderCode()).isEqualTo("MD-QC-KEEP");
        assertThat(submitted.motherOrderCode()).isEqualTo("MD-QC-KEEP");
        assertThat(submitted.frozen()).isFalse();
        assertThat(quantity("MD-QC-KEEP", StockStatus.SEMI_FINISHED)).isEqualByComparingTo("10");
    }

    @Test
    void splitMotherStickerCannotBeCheckedAsAMotherOrder() {
        String childCode = splitOneChild("MD-QC-SPLIT", THREE_LINES);
        assertThat(childCode).isNotBlank();

        assertThatThrownBy(() -> qualityService.lookup("JG|mother|MD-QC-SPLIT", qc()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请扫描子单贴纸");
    }

    private QualityCheckCommand allPass(String sticker) {
        return command(sticker, "别人", List.of(
                line(1, QualityVerdicts.PASS, ""),
                line(2, QualityVerdicts.PASS, ""),
                line(3, QualityVerdicts.PASS, "")
        ));
    }

    private QualityCheckCommand command(String sticker, String ignoredInspector, List<QualityCheckLineCommand> lines) {
        return new QualityCheckCommand(sticker, ignoredInspector, lines);
    }

    private QualityCheckLineCommand line(int lineNo, String verdict, String reason) {
        return new QualityCheckLineCommand(lineNo, verdict, reason);
    }

    private String finishedChild(String motherCode, String requirement) {
        String childCode = splitOneChild(motherCode, requirement);
        finish("JG|work_order|" + childCode);
        return childCode;
    }

    private void finish(String sticker) {
        stationService.report(sticker, "雕刻", worker("雕刻"));
        stationService.report(sticker, "镶嵌", worker("镶嵌"));
        stationService.report(sticker, "抛光", worker("抛光"));
    }

    private String splitOneChild(String motherCode, String requirement) {
        inbound(motherCode, "10", "20", requirement);
        var view = splitService.split(new SplitMotherOrderCommand(motherCode, "何琳", List.of(
                new SplitMotherOrderLine(new BigDecimal("4"), new BigDecimal("8"), null),
                new SplitMotherOrderLine(new BigDecimal("6"), new BigDecimal("12"), null)
        )));
        return view.children().get(0).code();
    }

    private void inbound(String code, String quantity, String weight, String requirement) {
        operations.confirmInbound(new ConfirmInboundCommand(
                code,
                1001L,
                1001L,
                "AU9999",
                "2026-10-08",
                "顺丰",
                "SF" + code.replace("-", ""),
                "",
                "何琳",
                "何琳",
                "戒托",
                "",
                null,
                "",
                "RAW-CUSTOMER",
                new BigDecimal(quantity),
                "件",
                new BigDecimal(weight),
                "何琳",
                "",
                "测试入库",
                requirement,
                "2026-10-20",
                "恒信珠宝"
        ));
    }

    private AuthUserAccount qc() {
        return new AuthUserAccount(
                "qc",
                "hash",
                "质检员 周娜",
                RoleCode.QUALITY_INSPECTOR,
                DataScope.DEPARTMENT,
                true,
                true
        );
    }

    private AuthUserAccount worker(String process) {
        return new AuthUserAccount(
                process,
                "hash",
                process + "工",
                RoleCode.OPERATOR,
                DataScope.SELF,
                true,
                true
        ).withAllowedProcesses(List.of(process));
    }

    private BigDecimal quantity(String orderCode, StockStatus status) {
        return stockLedgerService.balances().stream()
                .filter(balance -> orderCode.equals(balance.dimension().workOrderCode()))
                .filter(balance -> balance.dimension().stockStatus() == status)
                .map(StockBalance::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal weight(String orderCode, StockStatus status) {
        return stockLedgerService.balances().stream()
                .filter(balance -> orderCode.equals(balance.dimension().workOrderCode()))
                .filter(balance -> balance.dimension().stockStatus() == status)
                .map(StockBalance::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
