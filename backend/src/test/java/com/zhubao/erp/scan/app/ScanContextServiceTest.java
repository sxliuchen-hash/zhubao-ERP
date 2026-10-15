package com.zhubao.erp.scan.app;

import com.zhubao.erp.business.app.BusinessDataAccess;
import com.zhubao.erp.business.app.BusinessDocumentService;
import com.zhubao.erp.business.app.BusinessOperationService;
import com.zhubao.erp.business.app.MotherOrderSplitService;
import com.zhubao.erp.business.app.ProcessStationService;
import com.zhubao.erp.business.app.QualityRequirementService;
import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.ConfirmInboundCommand;
import com.zhubao.erp.business.domain.OutboundOrderRecord;
import com.zhubao.erp.business.domain.QualityCheckCommand;
import com.zhubao.erp.business.domain.QualityCheckLineCommand;
import com.zhubao.erp.business.domain.QualityVerdicts;
import com.zhubao.erp.business.domain.SplitMotherOrderCommand;
import com.zhubao.erp.business.domain.SplitMotherOrderLine;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.scan.domain.ScanContextAction;
import com.zhubao.erp.scan.domain.ScanContextRequest;
import com.zhubao.erp.scan.domain.ScanContextResponse;
import com.zhubao.erp.scan.domain.ScanIntent;
import com.zhubao.erp.scan.domain.ScanObjectType;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 统一扫码上下文只读真实单据，不把查询当成报工、质检或发货。
 */
class ScanContextServiceTest {

    private static final String THREE_LINES = "内壁刻字\n镶一颗主石\n\n镜面抛光";

    private InMemoryBusinessDocumentRepository repository;
    private StockLedgerService stocks;
    private BusinessOperationService operations;
    private MotherOrderSplitService splits;
    private ProcessStationService stations;
    private QualityRequirementService quality;
    private ScanContextService context;

    @BeforeEach
    void setUp() {
        InMemoryStockLedgerRepository stockRepository = new InMemoryStockLedgerRepository();
        stocks = new StockLedgerService(stockRepository);
        repository = new InMemoryBusinessDocumentRepository();
        operations = new BusinessOperationService(stocks, null, repository);
        splits = new MotherOrderSplitService(repository, stocks);
        ScanCodeParser parser = new ScanCodeParser();
        BusinessDataAccess access = new BusinessDataAccess(repository, stocks);
        stations = new ProcessStationService(repository, stocks, operations, parser, access);
        quality = new QualityRequirementService(repository, stocks, operations, parser, access);
        context = new ScanContextService(
                new BusinessDocumentService(repository),
                stocks,
                access,
                stations,
                quality,
                new PermissionGuard(),
                parser
        );
    }

    @Test
    void childKeepsItsOwnRequirementAndQueryDoesNotWriteLedger() {
        String childCode = splitChild("MD-SCAN-CHILD", THREE_LINES);
        replaceRequirement(childCode, "子单实际要求");
        int ledgers = stocks.ledgers().size();

        ScanContextResponse response = context.query(
                new ScanContextRequest("JG|work_order|" + childCode, null, ScanIntent.LOOKUP),
                admin());

        assertThat(response.requiresSelection()).isFalse();
        assertThat(response.target().objectType()).isEqualTo(ScanObjectType.WORK_ORDER);
        assertThat(response.target().code()).isEqualTo(childCode);
        assertThat(response.target().motherOrderCode()).isEqualTo("MD-SCAN-CHILD");
        assertThat(response.target().reference()).isEqualTo("WORK_ORDER:" + childCode);
        assertThat(response.order().requirement()).isEqualTo("子单实际要求");
        assertThat(response.order().customerName()).isEqualTo("恒信珠宝");
        assertThat(response.order().weightUnit()).isNull();
        assertThat(response.requirements().motherRequirement()).isEqualTo(THREE_LINES);
        assertThat(response.requirements().motherSource()).isEqualTo(ScanContextService.SOURCE_MOTHER);
        assertThat(response.requirements().workOrderRequirement()).isEqualTo("子单实际要求");
        assertThat(response.requirements().workOrderSource()).isEqualTo(ScanContextService.SOURCE_WORK_ORDER);
        assertThat(response.progress().progressRecords()).isEmpty();
        assertThat(response.progress().reinspection().available()).isFalse();
        assertThat(response.operationContext().balanceSelectionRequired()).isFalse();
        assertThat(response.operationContext().selectedBalance().workOrderCode()).isEqualTo(childCode);
        assertThat(response.operationContext().selectedBalance().stockStatus()).isEqualTo("WIP");
        assertThat(stocks.ledgers()).hasSize(ledgers);
        assertThat(repository.findQualityRequirementCheck(childCode)).isEmpty();
    }

    @Test
    void splitMotherRequiresSelectingAChildInsteadOfTheFirstOne() {
        String first = splitChild("MD-SCAN-SPLIT", "母单要求");
        String second = repository.listChildWorkOrders("MD-SCAN-SPLIT").stream()
                .map(ChildWorkOrderRecord::code)
                .filter(code -> !code.equals(first))
                .findFirst()
                .orElseThrow();
        int ledgers = stocks.ledgers().size();

        ScanContextResponse response = context.query(
                new ScanContextRequest("JG|mother|MD-SCAN-SPLIT", null, ScanIntent.PRODUCTION),
                worker("雕刻"));

        assertThat(response.requiresSelection()).isTrue();
        assertThat(response.target().objectType()).isEqualTo(ScanObjectType.MOTHER_ORDER);
        assertThat(response.target().workOrderCode()).isNull();
        assertThat(response.relatedTargets()).extracting(target -> target.code()).containsExactlyInAnyOrder(first, second);
        assertThat(response.operationContext().formalOperations()).isEmpty();
        assertThat(response.operationContext().selectedBalance()).isNull();
        assertThat(response.actions()).anyMatch(action ->
                ScanContextService.SELECT_CHILD.equals(action.code()) && action.allowed());
        assertThat(action(response, ScanContextService.REPORT_PROCESS).allowed()).isFalse();
        assertThat(action(response, ScanContextService.REPORT_PROCESS).blockKind()).isEqualTo(ScanContextService.BLOCK_STATE);
        assertThat(stocks.ledgers()).hasSize(ledgers);
    }

    @Test
    void keptMotherCanExposeTheBoundProcessWithoutReporting() {
        inbound("MD-SCAN-KEEP", "4", "8", "不拆要求");
        splits.keep("MD-SCAN-KEEP", "何琳");
        int ledgers = stocks.ledgers().size();

        ScanContextResponse response = context.query(
                new ScanContextRequest("JG|mother|MD-SCAN-KEEP", null, ScanIntent.PRODUCTION),
                worker("雕刻"));

        assertThat(response.requiresSelection()).isFalse();
        assertThat(response.order().requirement()).isEqualTo("不拆要求");
        assertThat(action(response, ScanContextService.REPORT_PROCESS).allowed()).isTrue();
        assertThat(response.requirements().stationRequirements())
                .extracting(item -> item.process())
                .containsExactly("雕刻");
        assertThat(stocks.ledgers()).hasSize(ledgers);
        assertThat(repository.listProcessStationHandoffs("MD-SCAN-KEEP")).isEmpty();
    }

    @Test
    void bareChildCodeIsAmbiguousWithAWaybillUntilTheTypeIsExplicit() {
        String childCode = splitChild("MD-SCAN-AMBIGUOUS", "母单要求");

        ScanContextResponse ambiguous = context.query(
                new ScanContextRequest(childCode, null, ScanIntent.LOOKUP),
                admin());

        assertThat(ambiguous.requiresSelection()).isTrue();
        assertThat(ambiguous.target().objectType()).isNull();
        assertThat(ambiguous.order().customerName()).isNull();
        assertThat(ambiguous.relatedTargets()).extracting(target -> target.objectType())
                .contains(ScanObjectType.WORK_ORDER, ScanObjectType.EXPRESS_NO);

        ScanContextResponse selected = context.query(
                new ScanContextRequest(childCode, ScanObjectType.WORK_ORDER, null),
                admin());

        assertThat(selected.requiresSelection()).isFalse();
        assertThat(selected.target().code()).isEqualTo(childCode);
        assertThat(selected.target().objectType()).isEqualTo(ScanObjectType.WORK_ORDER);
    }

    @Test
    void reworkTraceKeepsTheOriginalFailureAndDoesNotClaimReinspection() {
        String childCode = finishedChild("MD-SCAN-REWORK");
        String sticker = "JG|work_order|" + childCode;
        quality.submit(new QualityCheckCommand(sticker, "别人", List.of(
                new QualityCheckLineCommand(1, QualityVerdicts.FAIL, "刻字错了"),
                new QualityCheckLineCommand(2, QualityVerdicts.PASS, ""),
                new QualityCheckLineCommand(3, QualityVerdicts.PASS, "")
        )), inspector());
        quality.openRework(sticker, inspector());
        int ledgers = stocks.ledgers().size();

        ScanContextResponse response = context.query(
                new ScanContextRequest("JG|rework|FG-" + childCode, null, ScanIntent.QUALITY),
                inspector());

        assertThat(response.target().objectType()).isEqualTo(ScanObjectType.REWORK);
        assertThat(response.target().reworkOrderCode()).isEqualTo("FG-" + childCode);
        assertThat(response.target().workOrderCode()).isEqualTo(childCode);
        assertThat(response.requirements().motherRequirement()).isEqualTo(THREE_LINES);
        assertThat(response.progress().quality().frozen()).isTrue();
        assertThat(response.progress().quality().outcome()).isEqualTo(QualityVerdicts.FAIL);
        assertThat(response.progress().quality().lines()).extracting(line -> line.reason()).contains("刻字错了");
        assertThat(response.progress().reinspection().available()).isFalse();
        assertThat(response.progress().reinspection().reason()).contains("原不合格记录仍保留");
        assertThat(action(response, ScanContextService.RELEASE).allowed()).isFalse();
        assertThat(action(response, ScanContextService.RELEASE).blockReason()).contains("原不合格记录仍保留");
        assertThat(action(response, ScanContextService.REPORT_PROCESS).allowed()).isFalse();
        assertThat(action(response, ScanContextService.SUBMIT_QUALITY).blockReason()).contains("不能覆盖原不合格记录");
        assertThat(response.progress().progressRecords()).isEmpty();
        assertThat(stocks.ledgers()).hasSize(ledgers);
        assertThat(repository.findQualityRequirementCheck(childCode).orElseThrow().outcome()).isEqualTo(QualityVerdicts.FAIL);
    }

    @Test
    void outboundTraceShowsItsSourceAndDoesNotShipAgain() {
        String childCode = splitChild("MD-SCAN-OUT", "母单要求");
        repository.saveOutboundOrder(new OutboundOrderRecord(
                "OUT-SCAN-1",
                "MD-SCAN-OUT",
                childCode,
                1001L,
                1001L,
                "AU9999",
                "RAW-CUSTOMER",
                "CUST",
                "2026-10-11",
                "成品",
                new BigDecimal("4"),
                "件",
                new BigDecimal("8"),
                "顺丰",
                "SF12345678",
                "",
                "何琳",
                "",
                null,
                "",
                "何琳",
                "",
                "",
                "SHIPPED",
                "LG-1",
                "2026-10-11T10:00:00"
        ));
        int ledgers = stocks.ledgers().size();

        ScanContextResponse response = context.query(
                new ScanContextRequest("JG|outbound|OUT-SCAN-1", null, ScanIntent.SHIPPING),
                warehouse());

        assertThat(response.target().outboundOrderCode()).isEqualTo("OUT-SCAN-1");
        assertThat(response.target().motherOrderCode()).isEqualTo("MD-SCAN-OUT");
        assertThat(response.target().workOrderCode()).isEqualTo(childCode);
        assertThat(response.order().customerName()).isEqualTo("恒信珠宝");
        assertThat(response.requirements().workOrderRequirement()).isEqualTo("母单要求");
        assertThat(response.progress().shipments()).extracting(shipment -> shipment.outboundCode()).contains("OUT-SCAN-1");
        assertThat(action(response, ScanContextService.SHIP).allowed()).isFalse();
        assertThat(action(response, ScanContextService.SHIP).blockReason()).contains("不能再次扣库存");
        assertThat(stocks.ledgers()).hasSize(ledgers);
    }

    @Test
    void newWaybillStaysEmptyAndShippingIntentDoesNotOpenTheInboundMother() {
        inbound("MD-20261011-008", "4", "8", "来料要求");
        String courier = "SF" + "MD-20261011-008".replace("-", "");

        ScanContextResponse intake = context.query(
                new ScanContextRequest(courier, ScanObjectType.EXPRESS_NO, ScanIntent.INTAKE),
                receiver());
        assertThat(intake.requiresSelection()).isTrue();
        assertThat(intake.relatedTargets()).extracting(target -> target.objectType())
                .contains(ScanObjectType.MOTHER_ORDER, ScanObjectType.EXPRESS_NO);
        assertThat(intake.relatedTargets()).extracting(target -> target.motherOrderCode())
                .contains("MD-20261011-008");

        ScanContextResponse shipping = context.query(
                new ScanContextRequest(courier, null, ScanIntent.SHIPPING),
                receiver());
        assertThat(shipping.target().objectType()).isEqualTo(ScanObjectType.EXPRESS_NO);
        assertThat(shipping.target().motherOrderCode()).isNull();
        assertThat(shipping.order().customerName()).isNull();
        assertThat(shipping.order().quantity()).isNull();
        assertThat(action(shipping, ScanContextService.FILL_LOGISTICS).allowed()).isTrue();
        assertThat(shipping.actions()).noneMatch(action -> ScanContextService.INTAKE.equals(action.code()));

        ScanContextResponse fresh = context.query(
                new ScanContextRequest("SFNEW12345678", null, ScanIntent.INTAKE),
                receiver());
        assertThat(fresh.target().motherOrderCode()).isNull();
        assertThat(fresh.order().customerName()).isNull();
        assertThat(fresh.order().goodsName()).isNull();
        assertThat(fresh.order().quantity()).isNull();
        assertThat(action(fresh, ScanContextService.INTAKE).allowed()).isTrue();
        assertThat(fresh.progress().progressRecords()).isEmpty();
    }

    @Test
    void hiddenCustomerIsRejectedWithTheSameMessageAsAMissingCode() {
        inbound("MD-SCAN-SECRET", "4", "8", "机密要求");

        assertThatThrownBy(() -> context.query(
                new ScanContextRequest("JG|mother|MD-SCAN-SECRET", null, ScanIntent.LOOKUP),
                outsider()))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessage("单号不在当前账号的业务数据范围内");
        assertThatThrownBy(() -> context.query(
                new ScanContextRequest("MD-NO-SUCH", ScanObjectType.MOTHER_ORDER, ScanIntent.LOOKUP),
                outsider()))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessage("单号不在当前账号的业务数据范围内");
        assertThatThrownBy(() -> context.query(
                new ScanContextRequest("JG|mother|MD-NO-SUCH", null, null),
                admin()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("未找到对应单据");
    }

    @Test
    void severalFinishedBalancesMustBeChosenBeforeShipping() {
        String childCode = finishedChild("MD-SCAN-BALANCES");
        StockBalance semi = stocks.balances().stream()
                .filter(balance -> childCode.equals(balance.dimension().workOrderCode()))
                .filter(balance -> balance.dimension().stockStatus() == StockStatus.SEMI_FINISHED)
                .findFirst()
                .orElseThrow();
        StockDimension from = semi.dimension();
        stocks.transfer(new StockTransferCommand(
                "manual_finish", childCode, from, finished(from, "WH-A"),
                new BigDecimal("2"), new BigDecimal("4"), "分到甲仓"));
        stocks.transfer(new StockTransferCommand(
                "manual_finish", childCode, from, finished(from, "WH-B"),
                new BigDecimal("2"), new BigDecimal("4"), "分到乙仓"));
        int ledgers = stocks.ledgers().size();

        ScanContextResponse response = context.query(
                new ScanContextRequest("JG|work_order|" + childCode, null, ScanIntent.SHIPPING),
                warehouse());

        assertThat(response.operationContext().balanceSelectionRequired()).isTrue();
        assertThat(response.operationContext().selectedBalance()).isNull();
        assertThat(response.operationContext().balanceCandidates()).hasSize(2);
        assertThat(action(response, ScanContextService.SHIP).allowed()).isFalse();
        assertThat(action(response, ScanContextService.SHIP).blockReason()).contains("请选择完整的物料、仓库、货权和库存状态");
        assertThat(stocks.ledgers()).hasSize(ledgers);
    }

    @Test
    void readerWithoutTheStationRoleStillSeesTheOrder() {
        String childCode = splitChild("MD-SCAN-READER", "母单要求");

        ScanContextResponse response = context.query(
                new ScanContextRequest("JG|work_order|" + childCode, null, ScanIntent.PRODUCTION),
                receiver());

        assertThat(response.order().customerName()).isEqualTo("恒信珠宝");
        assertThat(action(response, ScanContextService.REPORT_PROCESS).allowed()).isFalse();
        assertThat(action(response, ScanContextService.REPORT_PROCESS).blockKind()).isEqualTo(ScanContextService.BLOCK_PERMISSION);
        assertThat(action(response, ScanContextService.SUBMIT_QUALITY).blockKind()).isEqualTo(ScanContextService.BLOCK_PERMISSION);
    }

    private ScanContextAction action(ScanContextResponse response, String code) {
        return response.actions().stream().filter(action -> code.equals(action.code())).findFirst().orElseThrow();
    }

    private String finishedChild(String motherCode) {
        String childCode = splitChild(motherCode, THREE_LINES);
        String sticker = "JG|work_order|" + childCode;
        stations.report(sticker, "雕刻", worker("雕刻"));
        stations.report(sticker, "镶嵌", worker("镶嵌"));
        stations.report(sticker, "抛光", worker("抛光"));
        return childCode;
    }

    private String splitChild(String motherCode, String requirement) {
        inbound(motherCode, "10", "20", requirement);
        var view = splits.split(new SplitMotherOrderCommand(motherCode, "何琳", List.of(
                new SplitMotherOrderLine(new BigDecimal("4"), new BigDecimal("8"), null),
                new SplitMotherOrderLine(new BigDecimal("6"), new BigDecimal("12"), null)
        )));
        return view.children().get(0).code();
    }

    private void replaceRequirement(String childCode, String requirement) {
        ChildWorkOrderRecord current = repository.findChildWorkOrderByCode(childCode).orElseThrow();
        repository.saveChildWorkOrder(new ChildWorkOrderRecord(
                current.code(),
                current.motherOrderCode(),
                current.customerId(),
                current.customerName(),
                requirement,
                current.dueDate(),
                current.materialCode(),
                current.goodsName(),
                current.quantity(),
                current.quantityUnit(),
                current.weight(),
                current.warehouseCode(),
                current.processes(),
                current.verificationCode(),
                current.ledgerCode(),
                current.operator(),
                current.createdAt()
        ));
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

    private StockDimension finished(StockDimension from, String warehouse) {
        return new StockDimension(
                from.ownerType(),
                from.ownerCustomerId(),
                from.customerId(),
                from.motherOrderCode(),
                from.workOrderCode(),
                from.materialCode(),
                warehouse,
                StockStatus.FINISHED
        );
    }

    private AuthUserAccount admin() {
        return account("admin", "系统管理员", RoleCode.ADMIN, DataScope.ALL, List.of());
    }

    private AuthUserAccount inspector() {
        return account("qc", "质检员 周娜", RoleCode.QUALITY_INSPECTOR, DataScope.ALL, List.of());
    }

    private AuthUserAccount receiver() {
        return account("receiver", "收货员 何琳", RoleCode.RECEIVER, DataScope.ALL, List.of());
    }

    private AuthUserAccount warehouse() {
        return account("warehouse", "库管员 何琳", RoleCode.WAREHOUSE_KEEPER, DataScope.ALL, List.of());
    }

    private AuthUserAccount worker(String process) {
        return account(process, process + "工", RoleCode.OPERATOR, DataScope.ALL, List.of()).withAllowedProcesses(List.of(process));
    }

    private AuthUserAccount outsider() {
        return account("outsider", "外部收货", RoleCode.RECEIVER, DataScope.CUSTOMER_RANGE, List.of("其他客户"));
    }

    private AuthUserAccount account(String username, String operator, RoleCode role, DataScope scope, List<String> customers) {
        return new AuthUserAccount(username, "hash", operator, role, scope, true, false, customers);
    }
}
