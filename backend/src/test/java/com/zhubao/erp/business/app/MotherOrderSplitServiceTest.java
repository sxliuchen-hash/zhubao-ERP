package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ChildWorkOrderView;
import com.zhubao.erp.business.domain.ConfirmInboundCommand;
import com.zhubao.erp.business.domain.MotherOrderSplitView;
import com.zhubao.erp.business.domain.SplitMotherOrderCommand;
import com.zhubao.erp.business.domain.SplitMotherOrderLine;
import com.zhubao.erp.business.domain.VerificationCodes;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.label.app.LabelService;
import com.zhubao.erp.label.domain.LabelField;
import com.zhubao.erp.label.domain.LabelPayload;
import com.zhubao.erp.label.domain.ReprintLabelCommand;
import com.zhubao.erp.label.infra.InMemoryLabelTemplateRepository;
import com.zhubao.erp.scan.app.ScanCodeParser;
import com.zhubao.erp.scan.domain.ScanObjectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 母单不拆、拆成子单，以及贴纸验证码。
 */
class MotherOrderSplitServiceTest {

    private InMemoryStockLedgerRepository stockRepository;
    private StockLedgerService stockLedgerService;
    private InMemoryBusinessDocumentRepository documents;
    private BusinessOperationService operations;
    private MotherOrderSplitService splitService;
    private LabelService labelService;
    private final ScanCodeParser scanCodeParser = new ScanCodeParser();

    @BeforeEach
    void setUp() {
        stockRepository = new InMemoryStockLedgerRepository();
        stockLedgerService = new StockLedgerService(stockRepository);
        documents = new InMemoryBusinessDocumentRepository();
        operations = new BusinessOperationService(stockLedgerService, null, documents);
        splitService = new MotherOrderSplitService(documents, stockLedgerService);
        labelService = new LabelService(documents, new InMemoryLabelTemplateRepository());
    }

    @Test
    void keepPrintsOneMotherStickerWithoutMovingStock() {
        inbound("MD-KEEP-001", "10", "20");

        MotherOrderSplitView view = splitService.keep("MD-KEEP-001", "何琳");
        LabelPayload sticker = labelService.generateMotherLabel("MD-KEEP-001");

        assertThat(view.decision()).isEqualTo("KEEP");
        assertThat(view.children()).isEmpty();
        assertThat(view.verificationCode()).isNotBlank();
        assertThat(sticker.qrContent()).isEqualTo("JG|mother|MD-KEEP-001");
        assertThat(sticker.fields()).extracting(LabelField::name)
                .contains("验证码", "客户", "加工要求", "期限", "单号");
        assertThat(value(sticker, "验证码")).isEqualTo(view.verificationCode());
        assertThat(value(sticker, "客户")).isEqualTo("恒信珠宝");
        assertThat(value(sticker, "加工要求")).isEqualTo("内壁刻字");
        assertThat(value(sticker, "期限")).isEqualTo("2026-10-20");
        assertThat(value(sticker, "单号")).isEqualTo("MD-KEEP-001");
        assertThat(scanCodeParser.resolve(sticker.qrContent(), "lookup").objectType())
                .isEqualTo(ScanObjectType.MOTHER_ORDER);
        assertThat(scanCodeParser.resolve(sticker.qrContent(), "lookup").code()).isEqualTo("MD-KEEP-001");
        assertThat(stockLedgerService.ledgers()).extracting(StockLedgerEntry::documentType)
                .containsExactly("inbound_confirm");
        assertThat(rawWaiting("MD-KEEP-001").quantity()).isEqualByComparingTo("10");
        assertThat(wipBalances()).isEmpty();

        LabelPayload reprinted = labelService.reprint(new ReprintLabelCommand(
                "MOTHER", "MD-KEEP-001", "贴纸丢失", "何琳"));
        assertThat(value(reprinted, "验证码")).isEqualTo(view.verificationCode());
        assertThat(documents.listLabelReprints("MOTHER", "MD-KEEP-001")).singleElement()
                .satisfies(log -> {
                    assertThat(log.reason()).isEqualTo("贴纸丢失");
                    assertThat(log.verificationCode()).isEqualTo(view.verificationCode());
                });
        assertThatThrownBy(() -> labelService.reprint(new ReprintLabelCommand("MOTHER", "MD-KEEP-001", "  ", "何琳")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("补打必须填写原因");
    }

    @Test
    void splitTwoChildrenMovesStockAndPrintsDifferentVerificationCodes() {
        inbound("MD-SPLIT-001", "10", "20");

        assertThatThrownBy(() -> splitService.split(command("MD-SPLIT-001",
                line("6", "12", null),
                line("5", "8", null))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数量合计不得超过母单剩余");
        assertThat(documents.listChildWorkOrders("MD-SPLIT-001")).isEmpty();
        assertThat(wipBalances()).isEmpty();

        MotherOrderSplitView view = splitService.split(command("MD-SPLIT-001",
                line("4", "8", List.of("抛光", "雕刻")),
                line("6", "12", null)));

        assertThat(view.decision()).isEqualTo("SPLIT");
        assertThat(view.remainingQuantity()).isEqualByComparingTo("0");
        assertThat(view.remainingWeight()).isEqualByComparingTo("0");
        assertThat(view.children()).hasSize(2);
        assertThat(view.children()).extracting(ChildWorkOrderView::verificationCode)
                .doesNotHaveDuplicates()
                .allMatch(code -> code != null && !code.isBlank());
        assertThat(view.children().get(0).processes()).containsExactly("雕刻", "抛光");
        assertThat(view.children().get(1).processes()).containsExactly("雕刻", "镶嵌", "抛光");
        assertThat(view.children()).allSatisfy(child -> {
            assertThat(child.customerName()).isEqualTo("恒信珠宝");
            assertThat(child.processingRequirement()).isEqualTo("内壁刻字");
            assertThat(child.dueDate()).isEqualTo("2026-10-20");
            LabelPayload sticker = labelService.generateWorkOrderLabel(child.code());
            assertThat(sticker.qrContent()).isEqualTo("JG|work_order|" + child.code());
            assertThat(value(sticker, "验证码")).isEqualTo(child.verificationCode());
            assertThat(value(sticker, "客户")).isEqualTo("恒信珠宝");
            assertThat(value(sticker, "加工要求")).isEqualTo("内壁刻字");
            assertThat(value(sticker, "期限")).isEqualTo("2026-10-20");
            assertThat(value(sticker, "单号")).isEqualTo(child.code());
            assertThat(scanCodeParser.resolve(sticker.qrContent(), "lookup").objectType())
                    .isEqualTo(ScanObjectType.WORK_ORDER);
            assertThat(scanCodeParser.resolve(sticker.qrContent(), "lookup").code()).isEqualTo(child.code());
        });
        assertThat(rawWaiting("MD-SPLIT-001").quantity()).isEqualByComparingTo("0");
        assertThat(wipBalances()).hasSize(2);
        assertThat(stockLedgerService.ledgers())
                .filteredOn(entry -> "work_order_split".equals(entry.documentType()))
                .hasSize(2)
                .allSatisfy(entry -> {
                    assertThat(entry.from().stockStatus()).isEqualTo(StockStatus.RAW_WAITING);
                    assertThat(entry.to().stockStatus()).isEqualTo(StockStatus.WIP);
                });
        assertThatThrownBy(() -> splitService.keep("MD-SPLIT-001", "何琳"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能再选择不拆");
        assertThatThrownBy(() -> labelService.generateMotherLabel("MD-SPLIT-001"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请打印子单贴纸");
    }

    @Test
    void rejectsSplitAfterKeepAndWeightBeyondRemaining() {
        inbound("MD-KEEP-002", "10", "20");
        splitService.keep("MD-KEEP-002", "何琳");

        assertThatThrownBy(() -> splitService.split(command("MD-KEEP-002",
                line("4", "8", null),
                line("6", "12", null))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已选择不拆");

        inbound("MD-SPLIT-002", "10", "20");
        assertThatThrownBy(() -> splitService.split(command("MD-SPLIT-002",
                line("4", "12", null),
                line("6", "12", null))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重量合计不得超过母单剩余");
        assertThatThrownBy(() -> splitService.split(command("MD-SPLIT-002", line("10", "20", List.of()))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少需要两张子单");
        assertThatThrownBy(() -> splitService.split(command("MD-SPLIT-002",
                line("4", "8", null),
                line("6", "12", List.of("焊接")))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未知工序");
    }

    @Test
    void lengthensVerificationCodeWhenTheShortCodeIsAlreadyTaken() {
        String shortCode = VerificationCodes.derive("ZD-SAME", candidate -> false);
        String longer = VerificationCodes.derive("ZD-SAME", candidate -> candidate.length() == 4);

        assertThat(shortCode).hasSize(4);
        assertThat(longer).hasSize(5).startsWith(shortCode);
    }

    private void inbound(String code, String quantity, String weight) {
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
                "内壁刻字",
                "2026-10-20",
                "恒信珠宝"
        ));
    }

    private SplitMotherOrderCommand command(String motherOrderCode, SplitMotherOrderLine... lines) {
        return new SplitMotherOrderCommand(motherOrderCode, "何琳", List.of(lines));
    }

    private SplitMotherOrderLine line(String quantity, String weight, List<String> processes) {
        return new SplitMotherOrderLine(new BigDecimal(quantity), new BigDecimal(weight), processes);
    }

    private StockBalance rawWaiting(String motherOrderCode) {
        return stockLedgerService.balances().stream()
                .filter(balance -> motherOrderCode.equals(balance.dimension().motherOrderCode()))
                .filter(balance -> balance.dimension().stockStatus() == StockStatus.RAW_WAITING)
                .findFirst()
                .orElseThrow();
    }

    private List<StockBalance> wipBalances() {
        return stockLedgerService.balances().stream()
                .filter(balance -> balance.dimension().stockStatus() == StockStatus.WIP)
                .filter(balance -> balance.quantity().compareTo(BigDecimal.ZERO) > 0)
                .toList();
    }

    private String value(LabelPayload payload, String name) {
        return payload.fields().stream()
                .filter(field -> name.equals(field.name()))
                .map(LabelField::value)
                .findFirst()
                .orElse("");
    }
}
