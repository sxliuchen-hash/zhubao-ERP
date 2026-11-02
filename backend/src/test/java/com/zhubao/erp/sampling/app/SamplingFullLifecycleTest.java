package com.zhubao.erp.sampling.app;

import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.business.infra.InMemoryMasterDataRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.document.infra.InMemoryDocumentManagementRepository;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.inventory.app.CompanyMaterialIssueService;
import com.zhubao.erp.inventory.app.CompanyMaterialReceiptService;
import com.zhubao.erp.inventory.app.FactoryItemService;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.CompanyMaterialSourceKind;
import com.zhubao.erp.inventory.domain.FactoryItemCategory;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockDocument;
import com.zhubao.erp.inventory.domain.StockDocumentKinds;
import com.zhubao.erp.inventory.domain.StockDocumentLine;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import com.zhubao.erp.inventory.infra.InMemoryCompanyMaterialIssueRepository;
import com.zhubao.erp.inventory.infra.InMemoryCompanyMaterialReceiptRepository;
import com.zhubao.erp.inventory.infra.InMemoryFactoryItemRepository;
import com.zhubao.erp.inventory.infra.InMemoryStockDocumentRepository;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.register.app.RegisterImportTemplateService;
import com.zhubao.erp.sampling.domain.SamplingOrderView;
import com.zhubao.erp.sampling.infra.InMemorySamplingDeliveryRepository;
import com.zhubao.erp.sampling.infra.InMemorySamplingFeeRepository;
import com.zhubao.erp.sampling.infra.InMemorySamplingMaterialRepository;
import com.zhubao.erp.sampling.infra.InMemorySamplingOperationRepository;
import com.zhubao.erp.sampling.infra.InMemorySamplingOrderRepository;
import com.zhubao.erp.sampling.infra.InMemorySamplingProductionLinkRepository;
import com.zhubao.erp.sampling.infra.InMemorySamplingVersionRepository;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.domain.WorkSession;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 打样独立于母单完成版本、来料、自备、两次样费和大货轻引用。员工计酬仍拒绝。 */
class SamplingFullLifecycleTest {

    @Test
    void 驳回后再确认并可分别出账或免收且不改大货与登记导入() {
        OperatorContext keeper = new OperatorContext("打样员", RoleCode.ADMIN, DataScope.ALL);
        PermissionGuard guard = new PermissionGuard();
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        InMemoryMasterDataRepository customers = new InMemoryMasterDataRepository();
        CustomerProfile customer = customers.saveCustomer(new CustomerProfile(0, "", "甲客户", "", "", "", "", true));
        InMemorySamplingOrderRepository orders = new InMemorySamplingOrderRepository();
        SamplingOrderService orderService = new SamplingOrderService(customers, orders, unit, guard);
        assertThatThrownBy(() -> orderService.open(
                "z02-mother-no", keeper, customer.customerId(), "CUSTOMER_SUPPLIED", "样品", "张三", "MD-20261102-9201", ""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("打样单不使用普通加工母单");
        SamplingOrderView opened = orderService.open(
                "z02-order-01", keeper, customer.customerId(), "CUSTOMER_SUPPLIED", "客户来料样品", "张三", "", "");
        SamplingOrderView replay = orderService.open(
                "z02-order-01", keeper, customer.customerId(), "CUSTOMER_SUPPLIED", "客户来料样品", "张三", "", "");
        assertThat(replay.samplingCode()).isEqualTo(opened.samplingCode());
        assertThat(opened.samplingCode()).doesNotStartWith("MD-");
        assertThat(opened.materialIntentLabel()).isEqualTo("客户来料");

        InMemorySamplingVersionRepository versions = new InMemorySamplingVersionRepository();
        SamplingVersionService versionService = new SamplingVersionService(orders, versions, unit, guard);
        versionService.add("z02-ver-0001", keeper, opened.samplingCode(), "MODEL", "直径 18", "", "", "", "", "", "", null);
        var rejected = versionService.reject("z02-no-00001", keeper, opened.samplingCode(), "MODEL", 1, "孔径偏大", "");
        var rejectedReplay = versionService.reject("z02-no-00001", keeper, opened.samplingCode(), "MODEL", 1, "孔径偏大", "");
        assertThat(rejectedReplay.decision()).isEqualTo(rejected.decision()).isEqualTo("REJECTED");
        assertThat(rejected.parameterText()).isEqualTo("直径 18");
        var confirmed = versionService.add("z02-ver-0002", keeper, opened.samplingCode(), "MODEL", "直径 19", "", "", "", "", "", "", null);
        assertThat(confirmed.versionNo()).isEqualTo(2);
        var decided = versionService.confirm("z02-yes-0002", keeper, opened.samplingCode(), "MODEL", 2, "客户口头确认", "");
        assertThat(versionService.confirm("z02-yes-0002", keeper, opened.samplingCode(), "MODEL", 2, "客户口头确认", "").decision())
                .isEqualTo("CONFIRMED");
        assertThat(decided.parameterText()).isEqualTo("直径 19");
        assertThat(versions.list(opened.samplingCode()).get(0).parameterText()).isEqualTo("直径 18");

        InMemoryDocumentManagementRepository photos = new InMemoryDocumentManagementRepository();
        InMemorySamplingOperationRepository operations = new InMemorySamplingOperationRepository();
        SamplingOperationService reports = new SamplingOperationService(orders, versions, operations, photos, unit, guard);
        photo(photos, "Z02-MAKE", opened.samplingCode());
        assertThatThrownBy(() -> reports.report(
                "z02-rpt-old1", keeper, opened.samplingCode(), "SAMPLE_MAKE", "MODEL", 1, List.of(), new BigDecimal("5"), "颗", "Z02-MAKE"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("版本与已确认版本不符");
        assertThatThrownBy(() -> reports.report(
                "z02-rpt-cut1", keeper, opened.samplingCode(), "CUT", "MODEL", 2, List.of(), new BigDecimal("5"), "颗", "Z02-MAKE"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("打样不使用普通加工工序");
        var made = reports.report(
                "z02-rpt-0001", keeper, opened.samplingCode(), "SAMPLE_MAKE", "MODEL", 2, List.of(), new BigDecimal("5"), "颗", "Z02-MAKE");
        assertThat(reports.report(
                "z02-rpt-0001", keeper, opened.samplingCode(), "SAMPLE_MAKE", "MODEL", 2, List.of(), new BigDecimal("5"), "颗", "Z02-MAKE").reportId())
                .isEqualTo(made.reportId());
        assertThat(operations.list(opened.samplingCode())).hasSize(1);

        StockLedgerService ledger = new StockLedgerService(new InMemoryStockLedgerRepository());
        InMemoryStockDocumentRepository stockDocuments = new InMemoryStockDocumentRepository();
        StockDimension source = new StockDimension(OwnerType.CUSTOMER, customer.customerId(), customer.customerId(), "", null,
                "XY", "WH-01", StockStatus.RAW_WAITING, "PC-Z02");
        stockDocuments.save(new StockDocument(
                "JC-Z02", StockDocumentKinds.PREP_RECEIPT, StockDocumentKinds.POSTED, OwnerType.CUSTOMER.name(),
                customer.customerId(), "甲客户", "WH-01", "", "", "", "", "", "", "库管", "z02-doc-01",
                "2026-11-02", "2026-11-02T09:00:00", "", StockDocumentKinds.IN,
                List.of(new StockDocumentLine(1, "XY", "颗", new BigDecimal("10"), new BigDecimal("3.0"), "PC-Z02", "", null, true,
                        new BigDecimal("10"), new BigDecimal("3.0"), "STK-Z02"))
        ), idle());
        ledger.transfer(new StockTransferCommand("prep_receipt", "JC-Z02", null, source, new BigDecimal("10"), new BigDecimal("3.0"), "收货"));
        InMemorySamplingMaterialRepository uses = new InMemorySamplingMaterialRepository();
        SamplingMaterialService materials = new SamplingMaterialService(
                orders, uses, new InMemoryCompanyMaterialReceiptRepository(),
                new CompanyMaterialIssueService(new InMemoryFactoryItemRepository(), new InMemoryCompanyMaterialReceiptRepository(),
                        new InMemoryCompanyMaterialIssueRepository(), unit, guard),
                stockDocuments, ledger, unit, guard);
        var picked = materials.pick("z02-cus-0001", keeper, opened.samplingCode(), "", "JC-Z02", 1, "", new BigDecimal("5"), new BigDecimal("1.5"), "颗");
        assertThat(materials.pick("z02-cus-0001", keeper, opened.samplingCode(), "", "JC-Z02", 1, "", new BigDecimal("5"), new BigDecimal("1.5"), "颗").useId())
                .isEqualTo(picked.useId());
        assertThat(picked.owner()).isEqualTo("CUSTOMER");
        assertThat(quantityOf(ledger, source)).isEqualByComparingTo("5");

        InMemorySamplingDeliveryRepository deliveries = new InMemorySamplingDeliveryRepository();
        SamplingDeliveryService deliveryService = new SamplingDeliveryService(orders, versions, operations, uses, deliveries, unit, guard);
        var first = deliveryService.deliver("z02-dlv-0001", keeper, opened.samplingCode(), "MODEL", 2, picked.useId(), new BigDecimal("2"), "颗", "Z02-CRED-1");
        assertThat(deliveryService.deliver("z02-dlv-0001", keeper, opened.samplingCode(), "MODEL", 2, picked.useId(), new BigDecimal("2"), "颗", "Z02-CRED-1").deliveryId())
                .isEqualTo(first.deliveryId());
        assertThat(first.remainingQuantity()).isEqualByComparingTo("3");
        assertThat(first.orderClosed()).isFalse();
        var second = deliveryService.deliver("z02-dlv-0002", keeper, opened.samplingCode(), "MODEL", 2, "", new BigDecimal("3"), "颗", "Z02-CRED-2");
        assertThat(second.remainingQuantity()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> deliveryService.deliver(
                "z02-dlv-0003", keeper, opened.samplingCode(), "MODEL", 2, "", new BigDecimal("1"), "颗", "Z02-CRED-3"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("交付数量超过已报工份额");
        assertThat(quantityOf(ledger, source)).isEqualByComparingTo("5");
        assertThat(deliveryService.list(keeper, opened.samplingCode())).hasSize(2);

        InMemoryFinanceRepository finance = new InMemoryFinanceRepository();
        SamplingFeeService fees = new SamplingFeeService(orders, deliveries, new InMemorySamplingFeeRepository(), finance, unit, guard);
        assertThatThrownBy(() -> fees.settle("z02-wage-001", keeper, opened.samplingCode(), first.deliveryId(), new BigDecimal("80.00"), false, "", "", true))
                .isInstanceOf(BusinessException.class)
                .hasMessage("打样员工计酬尚未核定");
        assertThatThrownBy(() -> fees.settle("z02-zero-001", keeper, opened.samplingCode(), first.deliveryId(), BigDecimal.ZERO, false, "", "", false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("未知金额不能写成 0");
        assertThatThrownBy(() -> fees.settle("z02-off-0001", keeper, opened.samplingCode(), first.deliveryId(), new BigDecimal("80.00"), false, "", "MD-20261102-9201", false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("样费不能抵大货");
        assertThat(finance.listReceivables()).isEmpty();
        assertThat(finance.listPayrollLines()).isEmpty();
        var charged = fees.settle("z02-fee-0001", keeper, opened.samplingCode(), first.deliveryId(), new BigDecimal("80.00"), false, "", "", false);
        assertThat(fees.settle("z02-fee-0001", keeper, opened.samplingCode(), first.deliveryId(), new BigDecimal("80.00"), false, "", "", false).feeId())
                .isEqualTo(charged.feeId());
        assertThatThrownBy(() -> fees.settle("z02-fee-0002", keeper, opened.samplingCode(), first.deliveryId(), new BigDecimal("90.00"), false, "", "", false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("这次交付的样费已经确认");
        var waived = fees.settle("z02-free001", keeper, opened.samplingCode(), second.deliveryId(), null, true, "本次样品明确免收", "", false);
        assertThat(waived.decision()).isEqualTo("WAIVE");
        assertThat(waived.amount()).isNull();
        assertThat(finance.listReceivables()).hasSize(1);
        assertThat(finance.listReceivables().get(0).amount()).isEqualByComparingTo("80.00");
        assertThat(finance.listReceivables().get(0).sourceType()).isEqualTo("SAMPLING_FEE");
        assertThat(finance.listReceivables().get(0).motherOrderCode()).isEmpty();
        assertThat(finance.listPayrollLines()).isEmpty();

        InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
        documents.saveMotherOrder(mother("MD-20261102-9201", customer.customerId(), "原加工要求"));
        SamplingProductionLinkService links = new SamplingProductionLinkService(
                documents, orders, versions, new InMemorySamplingProductionLinkRepository(), unit, guard);
        var linked = links.link("z02-link-001", keeper, "MD-20261102-9201", opened.samplingCode(), "MODEL", 2);
        assertThat(links.link("z02-link-001", keeper, "MD-20261102-9201", opened.samplingCode(), "MODEL", 2).samplingCode())
                .isEqualTo(linked.samplingCode());
        assertThat(linked.snapshotText()).isEqualTo("直径 19");
        versionService.add("z02-ver-0003", keeper, opened.samplingCode(), "MODEL", "直径 20", "", "", "", "", "", "", null);
        assertThat(links.find(keeper, "MD-20261102-9201").versionNo()).isEqualTo(2);
        assertThat(links.find(keeper, "MD-20261102-9201").snapshotText()).isEqualTo("直径 19");
        assertThat(documents.findMotherOrderByCode("MD-20261102-9201").orElseThrow().processingRequirement()).isEqualTo("原加工要求");
        assertThat(documents.findMotherOrderByCode("MD-20261102-9201").orElseThrow().quantity()).isEqualByComparingTo("10");
        assertThat(documents.findMotherOrderByCode(opened.samplingCode())).isEmpty();

        SamplingOrderView company = orderService.open(
                "z02-order-02", keeper, customer.customerId(), "COMPANY_PREPARED", "公司自备样品", "张三", "", "");
        assertThat(company.materialIntentLabel()).isEqualTo("公司自备");
        InMemoryFactoryItemRepository items = new InMemoryFactoryItemRepository();
        InMemoryCompanyMaterialReceiptRepository companyStocks = new InMemoryCompanyMaterialReceiptRepository();
        InMemoryCompanyMaterialIssueRepository issues = new InMemoryCompanyMaterialIssueRepository();
        var bead = new FactoryItemService(items, unit, guard).register(
                "z02-item-01", keeper, FactoryItemCategory.BEAD, "南红", "8mm", "颗", null, null);
        var received = new CompanyMaterialReceiptService(items, companyStocks, finance, unit, guard).receive(
                "z02-recv-01", keeper, bead.itemId(), new BigDecimal("100"), null, "颗", "WH-01", "2026-11-02",
                CompanyMaterialSourceKind.OPENING, "", null, null);
        SamplingMaterialService companyMaterials = new SamplingMaterialService(
                orders, uses, companyStocks, new CompanyMaterialIssueService(items, companyStocks, issues, unit, guard),
                stockDocuments, ledger, unit, guard);
        var companyPick = companyMaterials.pick(
                "z02-co-00001", keeper, company.samplingCode(), received.receiptId(), "", null, "WS-DY",
                new BigDecimal("40"), null, "颗");
        assertThat(companyMaterials.pick(
                "z02-co-00001", keeper, company.samplingCode(), received.receiptId(), "", null, "WS-DY",
                new BigDecimal("40"), null, "颗").useId()).isEqualTo(companyPick.useId());
        assertThat(companyPick.owner()).isEqualTo("COMPANY");
        assertThat(companyStocks.balance(bead.itemId(), received.batchCode(), "WH-01").orElseThrow().quantity()).isEqualByComparingTo("60");
        assertThat(companyStocks.balance(bead.itemId(), received.batchCode(), "WS-DY").orElseThrow().quantity()).isEqualByComparingTo("40");
        assertThat(quantityOf(ledger, source)).isEqualByComparingTo("5");
        assertThat(finance.listReceivables()).hasSize(1);
        assertThat(finance.listPayables()).isEmpty();

        var templates = new RegisterImportTemplateService().findTemplates();
        assertThat(templates).extracting(template -> template.code())
                .containsExactly("REG-INBOUND", "REG-OUTBOUND", "REG-POLISHING", "REG-ENGRAVING");
        assertThat(new RegisterImportTemplateService().summary().templates()).isEqualTo(4);
        assertThat(new RegisterImportTemplateService().summary().commandReadyTemplates()).isEqualTo(4);
        assertThat(orders.find(opened.samplingCode()).orElseThrow().status().name()).isEqualTo("OPEN");
        assertThat(orders.list()).hasSize(2);
    }

    private static BigDecimal quantityOf(StockLedgerService ledger, StockDimension dimension) {
        return ledger.balances().stream()
                .filter(balance -> balance.dimension().equals(dimension))
                .map(balance -> balance.quantity())
                .findFirst().orElseThrow();
    }

    private static WorkSession idle() {
        return new WorkSession() {
            @Override
            public Optional<Connection> connection() {
                return Optional.empty();
            }

            @Override
            public void onRollback(Runnable undo) {
            }
        };
    }

    private static void photo(InMemoryDocumentManagementRepository documents, String code, String businessCode) {
        documents.saveAttachment(new EvidenceAttachment(
                code, "DOC-" + code, "SAMPLING", businessCode, code + ".jpg", "image/jpeg", "mem", "LOCAL", "UPLOAD",
                "可用", "打样员", "sum", "2026-11-02T10:00:00", "", "", "SAMPLING"));
    }

    private static MotherOrderRecord mother(String code, long customerId, String requirement) {
        return new MotherOrderRecord(
                code, customerId, customerId, "XY", "2026-11-02",
                "", "", "", "", "",
                "珠", "CUSTOMER", true, "WH",
                new BigDecimal("10"), "颗", new BigDecimal("1.0"), "RAW_WAITING", "",
                "", null, "", "", "",
                "备注", "OPEN", "2026-11-02T00:00:00", requirement, "", "甲客户");
    }
}
