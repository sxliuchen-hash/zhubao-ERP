package com.zhubao.erp.machine.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.machine.domain.MachineOutputSummary;
import com.zhubao.erp.machine.domain.SubmitMachineOutputCommand;
import com.zhubao.erp.machine.infra.InMemoryMachineOutputRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MachineOutputServiceTest {

    private final InMemoryMachineOutputRepository repository = new InMemoryMachineOutputRepository();
    private final MachineOutputService service = new MachineOutputService(repository);

    @Test
    void submitsMachineOutputRecordWithDefaultsAndQueryIndexes() {
        var record = service.submit(new SubmitMachineOutputCommand(
                "MOR-REG-001",
                "2026-07-08",
                1001L,
                "恒信珠宝",
                "戒托",
                null,
                "ENG-03",
                "WO-REG-ENG-001",
                null,
                "WR-REG-ENG-001",
                new BigDecimal("12"),
                "",
                null,
                "周凯",
                null,
                "雕刻机统计单（日）",
                6,
                "3",
                "1",
                "IMP-REG-ENGRAVING-001",
                "STK-REG-001",
                "",
                "登记表导入",
                "车间组长 李华"
        ));

        assertThat(record.quantityUnit()).isEqualTo("件");
        assertThat(record.status()).isEqualTo("SUBMITTED");
        assertThat(record.createdBy()).isEqualTo("车间组长 李华");
        assertThat(service.recent(10)).containsExactly(record);
        assertThat(service.byImportBatchNo("IMP-REG-ENGRAVING-001")).containsExactly(record);
    }

    @Test
    void rejectsInvalidMachineOutputRecord() {
        assertThatThrownBy(() -> service.submit(new SubmitMachineOutputCommand(
                "MOR-REG-002",
                "2026-07-08",
                1001L,
                "恒信珠宝",
                "",
                null,
                "ENG-03",
                "",
                null,
                "",
                BigDecimal.ZERO,
                "件",
                null,
                "周凯",
                null,
                "雕刻机统计单（日）",
                6,
                "3",
                "1",
                "IMP-REG-ENGRAVING-002",
                "",
                "SUBMITTED",
                "",
                "车间组长 李华"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("productCategory");
    }

    @Test
    void summarizesRecentMachineOutputsForProductionStats() {
        service.submit(command("MOR-SUM-001", "2026-07-08", 9001L, "ENG-03", "戒托", "12", "1"));
        service.submit(command("MOR-SUM-002", "2026-07-08", null, "ENG-03", "戒托", "8", "2"));
        service.submit(command("MOR-SUM-003", "2026-07-07", null, "ENG-05", "耳饰", "5", "3"));

        MachineOutputSummary summary = service.summary(10);

        assertThat(summary.totalRecords()).isEqualTo(3);
        assertThat(summary.totalQuantity()).isEqualByComparingTo("25");
        assertThat(summary.quantityUnit()).isEqualTo("件");
        assertThat(summary.unmatchedMachineRecords()).isEqualTo(2);
        assertThat(summary.signatureMissingRecords()).isEqualTo(3);
        assertThat(summary.byMachine()).extracting("machineNoSnapshot").containsExactly("ENG-03", "ENG-05");
        assertThat(summary.byMachine().get(0).quantity()).isEqualByComparingTo("20");
        assertThat(summary.byWorkDate()).extracting("workDate").containsExactly("2026-07-08", "2026-07-07");
        assertThat(summary.byWorkDate().get(0).records()).isEqualTo(2);
    }

    @Test
    void suggestsMachineMasterMatchesForUnmatchedOutputs() {
        service.submit(command("MOR-MATCH-001", "2026-07-08", 9001L, "ENG-03", "X", "12", "1"));
        service.submit(command("MOR-MATCH-002", "2026-07-08", null, "ENG-03", "X", "8", "2"));
        service.submit(command("MOR-MATCH-003", "2026-07-07", null, "ENG-09", "Y", "5", "3"));

        var suggestions = service.machineMatchSuggestions(10);

        assertThat(suggestions).hasSize(2);
        var readyToBind = suggestions.stream()
                .filter(suggestion -> suggestion.machineNoSnapshot().equals("ENG-03"))
                .findFirst()
                .orElseThrow();
        assertThat(readyToBind.candidateMachineIds()).containsExactly(9001L);
        assertThat(readyToBind.readiness()).isEqualTo("READY_TO_BIND");
        assertThat(readyToBind.matchedRecords()).isEqualTo(1);
        assertThat(readyToBind.unmatchedRecords()).isEqualTo(1);
        assertThat(readyToBind.totalRecords()).isEqualTo(2);
        assertThat(readyToBind.latestWorkDate()).isEqualTo("2026-07-08");

        var needsMasterData = suggestions.stream()
                .filter(suggestion -> suggestion.machineNoSnapshot().equals("ENG-09"))
                .findFirst()
                .orElseThrow();
        assertThat(needsMasterData.candidateMachineIds()).isEmpty();
        assertThat(needsMasterData.readiness()).isEqualTo("NEEDS_MASTER_DATA");
        assertThat(needsMasterData.unmatchedRecords()).isEqualTo(1);
    }

    private SubmitMachineOutputCommand command(
            String code,
            String workDate,
            Long machineId,
            String machineNo,
            String productCategory,
            String quantity,
            String sequence
    ) {
        return new SubmitMachineOutputCommand(
                code,
                workDate,
                1001L,
                "恒信珠宝",
                productCategory,
                machineId,
                machineNo,
                "WO-" + code,
                null,
                "WR-" + code,
                new BigDecimal(quantity),
                "件",
                null,
                "周凯",
                null,
                "雕刻机统计单（日）",
                Integer.parseInt(sequence) + 5,
                sequence,
                sequence,
                "IMP-REG-ENGRAVING-SUM",
                "STK-" + code,
                "SUBMITTED",
                "汇总测试",
                "车间组长 李华"
        );
    }
}
