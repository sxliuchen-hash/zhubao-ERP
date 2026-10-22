package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ChildWorkOrderView;
import com.zhubao.erp.business.domain.MobileIntakeCommand;
import com.zhubao.erp.business.domain.MobileIntakeResult;
import com.zhubao.erp.business.domain.MotherRequirementSource;
import com.zhubao.erp.business.domain.ProcessingRequirementClauses;
import com.zhubao.erp.business.domain.QualityCheckLine;
import com.zhubao.erp.business.domain.RequirementTemplate;
import com.zhubao.erp.business.domain.RequirementText;
import com.zhubao.erp.business.domain.SplitMotherOrderCommand;
import com.zhubao.erp.business.domain.SplitMotherOrderLine;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.business.infra.InMemoryMasterDataRepository;
import com.zhubao.erp.business.infra.InMemoryMotherRequirementSourceRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.label.app.LabelService;
import com.zhubao.erp.label.domain.LabelField;
import com.zhubao.erp.label.infra.InMemoryLabelTemplateRepository;
import com.zhubao.erp.scan.app.ScanCodeParser;
import com.zhubao.erp.scan.app.ScanContextService;
import com.zhubao.erp.scan.domain.ScanContextRequest;
import com.zhubao.erp.scan.domain.ScanIntent;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 模板只提供建单时的文字。改模板、停用模板不回写已保存的母单、子单、标签、扫码和质检条款。
 */
class RequirementOrderSnapshotTest {

    @Test
    void templateChangeDoesNotRewriteSavedRequirement() {
        InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
        InMemoryStockLedgerRepository stock = new InMemoryStockLedgerRepository();
        StockLedgerService stocks = new StockLedgerService(stock);
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        InMemoryMotherRequirementSourceRepository sources = new InMemoryMotherRequirementSourceRepository();
        InMemoryMasterDataRepository catalog = new InMemoryMasterDataRepository();
        MasterDataService master = new MasterDataService(catalog);
        MobileIntakeService intake = new MobileIntakeService(
                new BusinessOperationService(stocks, null, documents, unit),
                documents, unit, null, stocks, sources);
        OperatorContext receiver = new OperatorContext("收货员 何琴", RoleCode.RECEIVER, DataScope.ALL);
        RequirementTemplate basic = master.templates(false).stream()
                .filter(template -> "TPL-BEAD-BASIC".equals(template.templateCode()))
                .findFirst()
                .orElseThrow();
        String saved = RequirementText.compose(basic.steps(), "孔径 2 毫米");

        MobileIntakeResult created = intake.intake(new MobileIntakeCommand(
                "SF1048329001", "恒信珠宝", null, saved, "2026-10-20", "戒托",
                new BigDecimal("1"), new BigDecimal("2.500"), "", "AU9999", "件",
                "snap-req-1", "COURIER", List.of(), basic.templateCode(), basic.version()), receiver);

        RequirementTemplate updated = master.saveTemplate(basic.templateCode(), basic.name(),
                List.of("抛光"), basic.sortOrder(), basic.version(), "");
        master.disableTemplate(updated.templateCode(), updated.version());

        assertThat(documents.findMotherOrderByCode(created.motherOrderCode()).orElseThrow().processingRequirement())
                .isEqualTo(saved);
        MotherRequirementSource source = sources.find(created.motherOrderCode()).orElseThrow();
        assertThat(source.templateCode()).isEqualTo("TPL-BEAD-BASIC");
        assertThat(source.templateVersion()).isEqualTo(basic.version());
        assertThat(source.savedRequirement()).isEqualTo(saved);
        assertThat(intake.view(created.motherOrderCode()).processingRequirement()).isEqualTo(saved);
        assertThat(master.templates(false)).extracting(RequirementTemplate::templateCode).doesNotContain("TPL-BEAD-BASIC");

        MotherOrderSplitService splits = new MotherOrderSplitService(documents, stocks, unit);
        var split = splits.split(new SplitMotherOrderCommand(created.motherOrderCode(), "收货员 何琴",
                List.of(
                        new SplitMotherOrderLine(new BigDecimal("0.400"), new BigDecimal("1.000"), List.of("抛光")),
                        new SplitMotherOrderLine(new BigDecimal("0.600"), new BigDecimal("1.500"), List.of("抛光")))),
                "snap-split-1");
        assertThat(split.processingRequirement()).isEqualTo(saved);
        assertThat(split.children()).extracting(ChildWorkOrderView::processingRequirement).containsOnly(saved);
        assertThat(split.children()).allSatisfy(child -> assertThat(child.processes()).containsExactly("抛光"));
        ChildWorkOrderView child = split.children().get(0);

        LabelService labels = new LabelService(documents, new InMemoryLabelTemplateRepository());
        assertThat(labels.generateMotherLabel(created.motherOrderCode()).fields())
                .filteredOn(field -> "加工要求".equals(field.name()))
                .extracting(LabelField::value)
                .containsExactly(saved);
        assertThat(labels.generateWorkOrderLabel(child.code()).fields())
                .filteredOn(field -> "加工要求".equals(field.name()))
                .extracting(LabelField::value)
                .containsExactly(saved);

        ScanCodeParser parser = new ScanCodeParser();
        BusinessDataAccess access = new BusinessDataAccess(documents, stocks);
        BusinessOperationService operations = new BusinessOperationService(stocks, null, documents, unit);
        ProcessStationService stations = new ProcessStationService(documents, stocks, operations, parser, access);
        QualityRequirementService quality = new QualityRequirementService(documents, stocks, operations, parser, access);
        ScanContextService context = new ScanContextService(
                new BusinessDocumentService(documents), stocks, access, stations, quality, new PermissionGuard(), parser);
        AuthUserAccount inspector = new AuthUserAccount(
                "qc", "hash", "质检员 周娜", RoleCode.QUALITY_INSPECTOR, DataScope.ALL, true, false, List.of());
        AuthUserAccount admin = new AuthUserAccount(
                "admin", "hash", "系统管理员", RoleCode.ADMIN, DataScope.ALL, true, false, List.of());

        assertThat(context.query(new ScanContextRequest("JG|work_order|" + child.code(), null, ScanIntent.LOOKUP), admin)
                .order().requirement()).isEqualTo(saved);
        assertThat(context.query(new ScanContextRequest("JG|mother|" + created.motherOrderCode(), null, ScanIntent.LOOKUP), admin)
                .requirements().motherRequirement()).isEqualTo(saved);
        assertThat(quality.lookup(child.code(), inspector).processingRequirement()).isEqualTo(saved);
        assertThat(quality.lookup(child.code(), inspector).lines()).extracting(QualityCheckLine::clause)
                .containsExactly(ProcessingRequirementClauses.split(saved).toArray(String[]::new));
        assertThat(stations.lookup(child.code(), admin).processingRequirement()).isEqualTo(saved);
        assertThat(stations.lookup(child.code(), admin).stations())
                .extracting(card -> card.processName())
                .doesNotContain("开粗", "车形（车成珠型）");
        assertThat(stocks.ledgers()).extracting(StockLedgerEntry::documentType)
                .containsExactly("inbound_confirm", "work_order_split", "work_order_split");
    }

    @Test
    void rejectsRequirementLongerThanFiveHundredCharacters() {
        InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
        InMemoryStockLedgerRepository stock = new InMemoryStockLedgerRepository();
        StockLedgerService stocks = new StockLedgerService(stock);
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        InMemoryMotherRequirementSourceRepository sources = new InMemoryMotherRequirementSourceRepository();
        MobileIntakeService intake = new MobileIntakeService(
                new BusinessOperationService(stocks, null, documents, unit),
                documents, unit, null, stocks, sources);
        OperatorContext receiver = new OperatorContext("收货员 何琴", RoleCode.RECEIVER, DataScope.ALL);

        assertThatThrownBy(() -> intake.intake(new MobileIntakeCommand(
                "SF1048329001", "恒信珠宝", null, "长".repeat(501), "2026-10-20", "戒托",
                new BigDecimal("1"), new BigDecimal("2.500"), "", "AU9999", "件", "snap-long-1"), receiver))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("500");
        assertThat(documents.listMotherOrders()).isEmpty();
        assertThat(stock.findAllLedgers()).isEmpty();
        assertThat(sources.find("MD-1")).isEmpty();
    }
}
