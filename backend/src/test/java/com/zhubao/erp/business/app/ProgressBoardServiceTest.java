package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.MotherSplitState;
import com.zhubao.erp.business.domain.ProcessStationHandoff;
import com.zhubao.erp.business.domain.QualityCheckLine;
import com.zhubao.erp.business.domain.QualityRequirementCheck;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressBoardServiceTest {

    private InMemoryBusinessDocumentRepository documents;
    private ProgressBoardService service;
    private final OperatorContext office = new OperatorContext("办公室", RoleCode.WORKSHOP_LEADER, DataScope.ALL);

    @BeforeEach
    void setUp() {
        documents = new InMemoryBusinessDocumentRepository();
        service = new ProgressBoardService(documents);
    }

    @Test
    void listsLiveOrdersAndHidesOtherCustomers() {
        documents.saveMotherOrder(mother("MD-1", 1001L, "恒信珠宝", "2099-01-01"));
        documents.saveMotherOrder(mother("MD-2", 1002L, "雅辰", "2020-01-01"));
        documents.saveMotherSplitState(new MotherSplitState("MD-1", MotherSplitState.SPLIT, "V1", "收货", "2026-10-09T08:00:00"));
        documents.saveChildWorkOrder(child("ZD-1", "MD-1", 1001L, "2099-01-01", "雕刻、镶嵌、抛光"));
        documents.saveProcessStationHandoff(new ProcessStationHandoff("ZD-1", "雕刻", "王强", "STK-1", "2026-10-09T09:00:00"));
        documents.saveQualityRequirementCheck(new QualityRequirementCheck(
                "QC-2",
                "MD-2",
                "MD-2",
                "周娜",
                "合格",
                false,
                "",
                "",
                "2026-10-09T10:00:00",
                List.of(new QualityCheckLine(1, "抛光", "合格", ""))
        ));

        var rows = service.list(office, "", "", "", "");
        assertThat(rows).anySatisfy(row -> {
            assertThat(row.workOrderCode()).isEqualTo("ZD-1");
            assertThat(row.currentProcess()).isEqualTo("镶嵌");
            assertThat(row.status()).isEqualTo("在制");
            assertThat(row.qualityOutcome()).isEqualTo("未质检");
            assertThat(row.overdue()).isFalse();
            assertThat(row.openTab()).isEqualTo("production");
        });
        assertThat(rows).anySatisfy(row -> {
            assertThat(row.motherOrderCode()).isEqualTo("MD-2");
            assertThat(row.status()).isEqualTo("待拆分");
            assertThat(row.overdue()).isTrue();
        });

        OperatorContext scoped = new OperatorContext(
                "业务员",
                RoleCode.SALES,
                DataScope.CUSTOMER_RANGE,
                true,
                "",
                List.of("1001")
        );
        assertThat(service.list(scoped, "", "", "", ""))
                .allSatisfy(row -> assertThat(row.customerId()).isEqualTo(1001L))
                .noneMatch(row -> "MD-2".equals(row.motherOrderCode()));
        assertThat(service.list(office, "雅辰", "", "", "")).singleElement()
                .satisfies(row -> assertThat(row.motherOrderCode()).isEqualTo("MD-2"));
        assertThat(service.list(office, "", "镶嵌", "", "")).singleElement()
                .satisfies(row -> assertThat(row.workOrderCode()).isEqualTo("ZD-1"));
    }

    @Test
    void returnsEmptyWhenNoDocumentsExist() {
        assertThat(service.list(office, "", "", "", "")).isEmpty();
    }

    private MotherOrderRecord mother(String code, Long customerId, String name, String dueDate) {
        return new MotherOrderRecord(
                code,
                customerId,
                customerId,
                "MAT",
                "2026-10-09",
                "",
                "",
                "",
                "",
                "",
                "货",
                "CUSTOMER",
                true,
                "RAW",
                new BigDecimal("1"),
                "件",
                new BigDecimal("1"),
                "RAW_WAITING",
                "",
                "",
                null,
                "",
                "收货",
                "",
                "",
                "CONFIRMED",
                "2026-10-09T08:00:00",
                "抛光",
                dueDate,
                name
        );
    }

    private ChildWorkOrderRecord child(String code, String motherCode, Long customerId, String dueDate, String processes) {
        return new ChildWorkOrderRecord(
                code,
                motherCode,
                customerId,
                "恒信珠宝",
                "抛光",
                dueDate,
                "MAT",
                "货",
                new BigDecimal("1"),
                "件",
                new BigDecimal("1"),
                "RAW",
                processes,
                "V1",
                "",
                "收货",
                "2026-10-09T08:30:00"
        );
    }
}
