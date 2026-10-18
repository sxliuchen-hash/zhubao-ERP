package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ConfirmInboundCommand;
import com.zhubao.erp.business.domain.QualityCheckCommand;
import com.zhubao.erp.business.domain.QualityCheckLine;
import com.zhubao.erp.business.domain.QualityCheckLineCommand;
import com.zhubao.erp.business.domain.QualityCheckResult;
import com.zhubao.erp.business.domain.QualityFollowCommand;
import com.zhubao.erp.business.domain.QualityRecheckCommand;
import com.zhubao.erp.business.domain.QualityReleaseResult;
import com.zhubao.erp.business.domain.QualityRequirementCheck;
import com.zhubao.erp.business.domain.QualityReworkResult;
import com.zhubao.erp.business.domain.QualityRound;
import com.zhubao.erp.business.domain.QualityVerdicts;
import com.zhubao.erp.business.domain.ReworkReportResult;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.business.infra.JdbcBusinessDocumentRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.inventory.infra.JdbcStockLedgerRepository;
import com.zhubao.erp.inventory.support.StockRepositoryFixture;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.scan.app.ScanCodeParser;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.app.JdbcBusinessWorkUnit;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandResultProbe;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import com.zhubao.erp.work.infra.JdbcCommandRequestRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 初检、开返工、返工报工、复检和放行的回放与同一次提交。
 * 只覆盖带请求编号的质检命令。工序交付测试不能说明这里已经安全。
 */
class QualityDeliveryTest {

    @Test
    void memoryQualityCommandsShareOneCommit() {
        assertQuality(new MemoryWorld(CommandResultProbe.NONE, QualityFactSaveProbe.NONE));
    }

    @Test
    void jdbcQualityCommandsShareOneCommit() throws Exception {
        JdbcWorld world = new JdbcWorld(CommandResultProbe.NONE, QualityFactSaveProbe.NONE);
        assertQuality(world);
        world.close();
    }

    private void assertQuality(World world) {
        finish(world, "MD-QC-01", 1001L, "恒信珠宝");
        AuthUserAccount inspector = inspector("质检员 周娜");
        assertThatThrownBy(() -> world.quality().submit(check("MD-QC-01", QualityVerdicts.FAIL, ""), inspector, "req-qc-reason"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须写原因");
        assertThatThrownBy(() -> world.quality().submit(check("MD-QC-01", QualityVerdicts.FAIL, "石位松"), account("操作工 王强"), "req-qc-role"))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("不是质检岗位");
        assertThat(world.semi("MD-QC-01", 1001L)).isEqualByComparingTo("10");
        assertThat(world.rounds("MD-QC-01")).isZero();
        assertThat(world.hasRequest("req-qc-reason")).isFalse();
        assertThat(world.hasRequest("req-qc-role")).isFalse();

        QualityCheckResult created = world.quality().submit(check("MD-QC-01", QualityVerdicts.FAIL, "石位松"), inspector, "req-qc-0001");
        QualityCheckResult replay = world.quality().submit(check("MD-QC-01", QualityVerdicts.FAIL, "  石位松  "), inspector, "req-qc-0001");
        assertThat(replay.ledgerCode()).isEqualTo(created.ledgerCode());
        assertThat(replay.roundCode()).isEqualTo("QR-MD-QC-01-1");
        assertThat(replay.roundNo()).isEqualTo(1);
        assertThat(replay.roundType()).isEqualTo(QualityRound.INITIAL);
        assertThat(replay.outcome()).isEqualTo(QualityVerdicts.FAIL);
        assertThat(replay.frozen()).isTrue();
        assertThat(world.freezes("MD-QC-01")).isEqualTo(1);
        assertThat(world.semi("MD-QC-01", 1001L)).isEqualByComparingTo("0");
        assertThat(world.frozen("MD-QC-01", 1001L)).isEqualByComparingTo("10");
        assertThat(world.rounds("MD-QC-01")).isEqualTo(1);
        QualityRequirementCheck original = world.check("MD-QC-01");
        assertThat(original.outcome()).isEqualTo(QualityVerdicts.FAIL);
        assertThat(original.frozen()).isTrue();
        assertThat(original.lines()).extracting(QualityCheckLine::reason).containsExactly("石位松");

        assertThatThrownBy(() -> world.quality().submit(check("MD-QC-01", QualityVerdicts.PASS, ""), inspector, "req-qc-0001"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("同一请求的内容不一致");
        finish(world, "MD-QC-02", 1002L, "雅辰饰品");
        assertThatThrownBy(() -> world.quality().submit(check("MD-QC-02", QualityVerdicts.FAIL, "石位松"), inspector, "req-qc-0001"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("同一请求的内容不一致");
        assertThatThrownBy(() -> world.quality().submit(check("MD-QC-01", QualityVerdicts.FAIL, "石位松"), inspector("另一账号"), "req-qc-0001"))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessage("该请求不能跨账号重放");
        assertThatThrownBy(() -> world.quality().submit(check("MD-QC-01", QualityVerdicts.FAIL, "石位松"), inspector, "req-qc-again"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已经做过质检对照");
        assertThat(world.freezes("MD-QC-01")).isEqualTo(1);
        assertThat(world.semi("MD-QC-02", 1002L)).isEqualByComparingTo("10");
        assertThat(world.rounds("MD-QC-02")).isZero();
        assertThat(world.hasRequest("req-qc-again")).isFalse();

        assertThatThrownBy(() -> world.quality().recheck(recheck("MD-QC-01", QualityVerdicts.PASS, "", "req-qc-early"), inspector))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先开返工");
        assertThat(world.hasRequest("req-qc-early")).isFalse();

        QualityReworkResult opened = world.quality().openRework(sticker("MD-QC-01"), inspector, "req-qc-rework");
        QualityReworkResult openedAgain = world.quality().openRework(sticker("MD-QC-01"), inspector, "req-qc-rework");
        assertThat(openedAgain.ledgerCode()).isEqualTo(opened.ledgerCode());
        assertThat(opened.reworkOrderCode()).isEqualTo("FG-MD-QC-01");
        assertThat(world.check("MD-QC-01").reworkOrderCode()).isEqualTo("FG-MD-QC-01");
        assertThat(world.check("MD-QC-01").outcome()).isEqualTo(QualityVerdicts.FAIL);
        assertThat(world.check("MD-QC-01").lines()).extracting(QualityCheckLine::reason).containsExactly("石位松");
        assertThat(world.frozen("MD-QC-01", 1001L)).isEqualByComparingTo("0");
        assertThat(world.rework("MD-QC-01", 1001L)).isEqualByComparingTo("10");
        assertThat(world.dispatches("MD-QC-01")).isEqualTo(1);

        assertThatThrownBy(() -> world.quality().recheck(recheck("MD-QC-01", QualityVerdicts.PASS, "", "req-qc-noreport"), inspector))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("返工还没报工");
        assertThat(world.rework("MD-QC-01", 1001L)).isEqualByComparingTo("10");
        assertThat(world.hasRequest("req-qc-noreport")).isFalse();

        ReworkReportResult reported = world.quality().reportRework(follow("MD-QC-01", "req-qc-report"), inspector);
        ReworkReportResult reportedAgain = world.quality().reportRework(follow("MD-QC-01", "req-qc-report"), inspector);
        assertThat(reportedAgain.ledgerCode()).isEqualTo(reported.ledgerCode());
        assertThat(world.reports("MD-QC-01")).isEqualTo(reported.ledgerCode());
        assertThat(world.rework("MD-QC-01", 1001L)).isEqualByComparingTo("0");
        assertThat(world.semi("MD-QC-01", 1001L)).isEqualByComparingTo("10");
        assertThat(world.reworkReports("MD-QC-01")).isEqualTo(1);

        QualityCheckResult reviewed = world.quality().recheck(recheck("MD-QC-01", QualityVerdicts.CONCESSION, "客户接受", "req-qc-recheck"), inspector);
        QualityCheckResult reviewedAgain = world.quality().recheck(
                recheck("MD-QC-01", QualityVerdicts.CONCESSION, "  客户接受  ", "req-qc-recheck"), inspector);
        assertThat(reviewedAgain.roundCode()).isEqualTo(reviewed.roundCode());
        assertThat(reviewed.roundCode()).isEqualTo("QR-MD-QC-01-2");
        assertThat(reviewed.roundType()).isEqualTo(QualityRound.RECHECK);
        assertThat(reviewed.outcome()).isEqualTo(QualityVerdicts.CONCESSION);
        assertThat(reviewed.frozen()).isFalse();
        assertThat(world.rounds("MD-QC-01")).isEqualTo(2);
        assertThat(world.round(world, "MD-QC-01", 2).reworkReportLedger()).isEqualTo(reported.ledgerCode());
        assertThat(world.check("MD-QC-01").outcome()).isEqualTo(QualityVerdicts.FAIL);
        assertThat(world.check("MD-QC-01").frozen()).isTrue();
        assertThat(world.check("MD-QC-01").lines()).extracting(QualityCheckLine::reason).containsExactly("石位松");
        assertThat(world.freezes("MD-QC-01")).isEqualTo(1);
        assertThat(world.semi("MD-QC-01", 1001L)).isEqualByComparingTo("10");

        assertThatThrownBy(() -> world.quality().recheck(recheck("MD-QC-01", QualityVerdicts.PASS, "", "req-qc-recheck2"), inspector))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能覆盖");
        assertThat(world.rounds("MD-QC-01")).isEqualTo(2);
        assertThat(world.hasRequest("req-qc-recheck2")).isFalse();

        QualityReleaseResult released = world.quality().releaseRound(follow("MD-QC-01", "req-qc-release"), inspector);
        QualityReleaseResult releasedAgain = world.quality().releaseRound(follow("MD-QC-01", "req-qc-release"), inspector);
        assertThat(releasedAgain.ledgerCode()).isEqualTo(released.ledgerCode());
        assertThat(released.releaseResult()).isEqualTo(QualityVerdicts.CONCESSION);
        assertThat(world.semi("MD-QC-01", 1001L)).isEqualByComparingTo("0");
        assertThat(world.finished("MD-QC-01", 1001L)).isEqualByComparingTo("10");
        assertThat(world.releases("MD-QC-01")).isEqualTo(1);
        assertThat(world.check("MD-QC-01").frozen()).isTrue();

        finish(world, "MD-QC-FAIL", 1001L, "恒信珠宝");
        world.quality().submit(check("MD-QC-FAIL", QualityVerdicts.FAIL, "刻线断了"), inspector, "req-qc-failcheck");
        world.quality().openRework(sticker("MD-QC-FAIL"), inspector, "req-qc-failopen");
        world.quality().reportRework(follow("MD-QC-FAIL", "req-qc-failreport"), inspector);
        QualityCheckResult failedReview = world.quality().recheck(recheck("MD-QC-FAIL", QualityVerdicts.FAIL, "还是断", "req-qc-failreview"), inspector);
        assertThat(failedReview.frozen()).isTrue();
        assertThat(failedReview.roundType()).isEqualTo(QualityRound.RECHECK);
        assertThat(world.check("MD-QC-FAIL").lines()).extracting(QualityCheckLine::reason).containsExactly("刻线断了");
        assertThat(world.freezes("MD-QC-FAIL")).isEqualTo(2);
        assertThat(world.frozen("MD-QC-FAIL", 1001L)).isEqualByComparingTo("10");
        assertThatThrownBy(() -> world.quality().releaseRound(follow("MD-QC-FAIL", "req-qc-failrelease"), inspector))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有最新轮次合格或让步才能放行");
        assertThat(world.finished("MD-QC-FAIL", 1001L)).isEqualByComparingTo("0");
        assertThat(world.releases("MD-QC-FAIL")).isZero();
        assertThat(world.hasRequest("req-qc-failrelease")).isFalse();

        finish(world, "MD-QC-FACT", 1001L, "恒信珠宝");
        World failingFact = world.withProbes(CommandResultProbe.NONE, () -> {
            throw new IllegalStateException("质检事实保存失败");
        });
        assertThatThrownBy(() -> failingFact.quality().submit(check("MD-QC-FACT", QualityVerdicts.FAIL, "划伤"), inspector, "req-qc-fact"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("质检事实保存失败");
        assertThat(world.semi("MD-QC-FACT", 1001L)).isEqualByComparingTo("10");
        assertThat(world.frozen("MD-QC-FACT", 1001L)).isEqualByComparingTo("0");
        assertThat(world.freezes("MD-QC-FACT")).isZero();
        assertThat(world.rounds("MD-QC-FACT")).isZero();
        assertThat(world.hasCheck("MD-QC-FACT")).isFalse();
        assertThat(world.hasRequest("req-qc-fact")).isFalse();

        finish(world, "MD-QC-RESULT", 1001L, "恒信珠宝");
        World failingResult = world.withProbes(() -> {
            throw new IllegalStateException("请求结果保存失败");
        }, QualityFactSaveProbe.NONE);
        assertThatThrownBy(() -> failingResult.quality().submit(check("MD-QC-RESULT", QualityVerdicts.FAIL, "划伤"), inspector, "req-qc-result"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("请求结果保存失败");
        assertThat(world.semi("MD-QC-RESULT", 1001L)).isEqualByComparingTo("10");
        assertThat(world.freezes("MD-QC-RESULT")).isZero();
        assertThat(world.rounds("MD-QC-RESULT")).isZero();
        assertThat(world.hasRequest("req-qc-result")).isFalse();

        finish(world, "MD-QC-OLD", 1001L, "恒信珠宝");
        World oldPath = world.withProbes(CommandResultProbe.NONE, () -> {
            throw new IllegalStateException("质检事实保存失败");
        });
        assertThatThrownBy(() -> oldPath.quality().submit(check("MD-QC-OLD", QualityVerdicts.FAIL, "划伤"), inspector))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("质检事实保存失败");
        assertThat(world.semi("MD-QC-OLD", 1001L)).isEqualByComparingTo("0");
        assertThat(world.frozen("MD-QC-OLD", 1001L)).isEqualByComparingTo("10");
        assertThat(world.freezes("MD-QC-OLD")).isEqualTo(1);
        assertThat(world.hasCheck("MD-QC-OLD")).isFalse();
        assertThat(world.rounds("MD-QC-OLD")).isZero();

        finish(world, "MD-QC-RACE", 1001L, "恒信珠宝");
        raceSame(world, "MD-QC-RACE", "req-qc-race");
        finish(world, "MD-QC-RACE2", 1001L, "恒信珠宝");
        raceDifferent(world, "MD-QC-RACE2");
    }

    private void raceSame(World world, String mother, String requestId) {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            AuthUserAccount inspector = inspector("质检员 周娜");
            Future<QualityCheckResult> left = pool.submit(() -> {
                start.await();
                return world.quality().submit(check(mother, QualityVerdicts.FAIL, "石位松"), inspector, requestId);
            });
            Future<QualityCheckResult> right = pool.submit(() -> {
                start.await();
                return world.quality().submit(check(mother, QualityVerdicts.FAIL, "  石位松  "), inspector, requestId);
            });
            start.countDown();
            assertThat(left.get().ledgerCode()).isEqualTo(right.get().ledgerCode());
            assertThat(world.freezes(mother)).isEqualTo(1);
            assertThat(world.rounds(mother)).isEqualTo(1);
            assertThat(world.frozen(mother, 1001L)).isEqualByComparingTo("10");
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        } finally {
            pool.shutdownNow();
        }
    }

    private void raceDifferent(World world, String mother) {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            AuthUserAccount inspector = inspector("质检员 周娜");
            Future<QualityCheckResult> left = pool.submit(() -> {
                start.await();
                return world.quality().submit(check(mother, QualityVerdicts.FAIL, "石位松"), inspector, "req-qc-left1");
            });
            Future<QualityCheckResult> right = pool.submit(() -> {
                start.await();
                return world.quality().submit(check(mother, QualityVerdicts.FAIL, "石位松"), inspector, "req-qc-right");
            });
            start.countDown();
            int success = outcome(left) + outcome(right);
            assertThat(success).isEqualTo(1);
            assertThat(world.freezes(mother)).isEqualTo(1);
            assertThat(world.rounds(mother)).isEqualTo(1);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        } finally {
            pool.shutdownNow();
        }
    }

    private int outcome(Future<QualityCheckResult> future) throws Exception {
        try {
            future.get();
            return 1;
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof BusinessException) {
                return 0;
            }
            throw ex;
        }
    }

    private void finish(World world, String code, long customerId, String customerName) {
        world.operations().confirmInbound(inbound(code, customerId, customerName));
        world.splits().keep(code, "何琳");
        AuthUserAccount worker = account("操作工 王强", "雕刻", "镶嵌", "抛光");
        world.station().report(sticker(code), "雕刻", worker);
        world.station().report(sticker(code), "镶嵌", worker);
        world.station().report(sticker(code), "抛光", worker);
    }

    private String sticker(String code) {
        return "JG|mother|" + code;
    }

    private QualityCheckCommand check(String code, String verdict, String reason) {
        return new QualityCheckCommand(sticker(code), "别人", List.of(new QualityCheckLineCommand(1, verdict, reason)));
    }

    private QualityRecheckCommand recheck(String code, String verdict, String reason, String requestId) {
        return new QualityRecheckCommand(sticker(code), List.of(new QualityCheckLineCommand(1, verdict, reason)), requestId);
    }

    private QualityRecheckCommand recheck(String code, String verdict, String reason) {
        return recheck(code, verdict, reason, null);
    }

    private QualityFollowCommand follow(String code, String requestId) {
        return new QualityFollowCommand(sticker(code), requestId);
    }

    private ConfirmInboundCommand inbound(String code, long customerId, String customerName) {
        return new ConfirmInboundCommand(
                code, customerId, customerId, "AU9999", "2026-10-08", "顺丰", "SF" + code.replace("-", ""),
                "", "何琳", "何琳", "戒托", "", null, "", "RAW-CUSTOMER",
                new BigDecimal("10"), "件", new BigDecimal("20"), "何琳", "", "测试入库", "内壁刻字", "2026-10-20", customerName
        );
    }

    private AuthUserAccount inspector(String operator) {
        return new AuthUserAccount(operator, "hash", operator, RoleCode.WORKSHOP_LEADER, DataScope.SELF, true, false);
    }

    private AuthUserAccount account(String operator, String... processes) {
        return new AuthUserAccount(operator, "hash", operator, RoleCode.OPERATOR, DataScope.SELF, true, true)
                .withAllowedProcesses(List.of(processes));
    }

    private interface World {
        QualityRequirementService quality();

        ProcessStationService station();

        BusinessOperationService operations();

        MotherOrderSplitService splits();

        BigDecimal semi(String mother, long customerId);

        BigDecimal frozen(String mother, long customerId);

        BigDecimal rework(String mother, long customerId);

        BigDecimal finished(String mother, long customerId);

        int freezes(String mother);

        int dispatches(String mother);

        int reworkReports(String mother);

        int releases(String mother);

        int rounds(String mother);

        QualityRound round(World world, String mother, int roundNo);

        String reports(String mother);

        QualityRequirementCheck check(String mother);

        boolean hasCheck(String mother);

        boolean hasRequest(String requestId);

        World withProbes(CommandResultProbe resultProbe, QualityFactSaveProbe factProbe);
    }

    private static final class MemoryWorld implements World {
        private final InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
        private final InMemoryStockLedgerRepository stock = new InMemoryStockLedgerRepository();
        private final InMemoryCommandRequestRepository requests = new InMemoryCommandRequestRepository();
        private final StockLedgerService stocks;
        private final BusinessOperationService operations;
        private final MotherOrderSplitService splits;
        private final ProcessStationService station;
        private final QualityRequirementService quality;

        private MemoryWorld(CommandResultProbe resultProbe, QualityFactSaveProbe factProbe) {
            this.stocks = new StockLedgerService(stock);
            this.operations = new BusinessOperationService(stocks, null, documents);
            this.splits = new MotherOrderSplitService(documents, stocks);
            this.station = new ProcessStationService(documents, stocks, operations, new ScanCodeParser());
            BusinessWorkUnit unit = new MemoryBusinessWorkUnit(requests, resultProbe);
            this.quality = new QualityRequirementService(
                    documents, stocks, operations, new ScanCodeParser(), null, unit, factProbe);
        }

        @Override
        public QualityRequirementService quality() {
            return quality;
        }

        @Override
        public ProcessStationService station() {
            return station;
        }

        @Override
        public BusinessOperationService operations() {
            return operations;
        }

        @Override
        public MotherOrderSplitService splits() {
            return splits;
        }

        @Override
        public BigDecimal semi(String mother, long customerId) {
            return quantity(mother, customerId, StockStatus.SEMI_FINISHED);
        }

        @Override
        public BigDecimal frozen(String mother, long customerId) {
            return quantity(mother, customerId, StockStatus.FROZEN);
        }

        @Override
        public BigDecimal rework(String mother, long customerId) {
            return quantity(mother, customerId, StockStatus.REWORK);
        }

        @Override
        public BigDecimal finished(String mother, long customerId) {
            return quantity(mother, customerId, StockStatus.FINISHED);
        }

        @Override
        public int freezes(String mother) {
            return count(mother, "quality_freeze");
        }

        @Override
        public int dispatches(String mother) {
            return count(mother, "rework_dispatch");
        }

        @Override
        public int reworkReports(String mother) {
            return count(mother, "rework_report");
        }

        @Override
        public int releases(String mother) {
            return count(mother, "quality_release");
        }

        @Override
        public int rounds(String mother) {
            return documents.listQualityRounds(mother).size();
        }

        @Override
        public QualityRound round(World world, String mother, int roundNo) {
            return documents.listQualityRounds(mother).stream()
                    .filter(item -> item.roundNo() == roundNo)
                    .findFirst()
                    .orElseThrow();
        }

        @Override
        public String reports(String mother) {
            return documents.findQualityReworkReport("FG-" + mother).orElseThrow().ledgerCode();
        }

        @Override
        public QualityRequirementCheck check(String mother) {
            return documents.findQualityRequirementCheck(mother).orElseThrow();
        }

        @Override
        public boolean hasCheck(String mother) {
            return documents.findQualityRequirementCheck(mother).isPresent();
        }

        @Override
        public boolean hasRequest(String requestId) {
            return requests.findByRequestId(requestId).isPresent();
        }

        @Override
        public World withProbes(CommandResultProbe resultProbe, QualityFactSaveProbe factProbe) {
            BusinessWorkUnit unit = new MemoryBusinessWorkUnit(requests, resultProbe);
            QualityRequirementService next = new QualityRequirementService(
                    documents, stocks, operations, new ScanCodeParser(), null, unit, factProbe);
            return new WorldView(next, this);
        }

        private int count(String mother, String documentType) {
            return (int) stock.findAllLedgers().stream()
                    .filter(entry -> mother.equals(entry.to().motherOrderCode()) && documentType.equals(entry.documentType()))
                    .count();
        }

        private BigDecimal quantity(String mother, long customerId, StockStatus status) {
            return stock.findBalance(new StockDimension(
                    OwnerType.CUSTOMER, customerId, customerId, mother, mother, "AU9999", "RAW-CUSTOMER", status
            )).quantity();
        }
    }

    private static final class JdbcWorld implements World {
        private final JdbcDataSource dataSource;
        private final JdbcBusinessDocumentRepository documents;
        private final JdbcStockLedgerRepository stock;
        private final JdbcCommandRequestRepository requests;
        private final StockLedgerService stocks;
        private final BusinessOperationService operations;
        private final MotherOrderSplitService splits;
        private final ProcessStationService station;
        private final QualityRequirementService quality;

        private JdbcWorld(CommandResultProbe resultProbe, QualityFactSaveProbe factProbe) throws Exception {
            dataSource = StockRepositoryFixture.jdbcDataSource();
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("""
                        create table biz_mother_order (
                            id bigint generated by default as identity primary key,
                            code varchar(80) not null unique,
                            customer_id bigint not null,
                            owner_customer_id bigint,
                            material_code varchar(80) not null,
                            inbound_date varchar(20),
                            inbound_express_company varchar(80),
                            inbound_express_no varchar(80),
                            sender_name_snapshot varchar(120),
                            receiver_name_snapshot varchar(120),
                            unpack_signer_name varchar(120),
                            goods_name_snapshot varchar(160),
                            owner_type varchar(20) not null,
                            is_customer_owned boolean not null,
                            warehouse_code varchar(50) not null,
                            quantity decimal(18,3) not null,
                            quantity_unit varchar(20) not null,
                            weight decimal(18,3) not null,
                            stock_status varchar(40) not null,
                            ledger_code varchar(80) not null unique,
                            source_register_type varchar(64),
                            source_line_no int,
                            source_sequence_no varchar(40),
                            operator varchar(80) not null,
                            evidence_code varchar(120),
                            inbound_remark varchar(500),
                            order_status varchar(40) not null,
                            created_at varchar(40) not null,
                            processing_requirement varchar(500) not null,
                            due_date varchar(20) not null,
                            customer_name varchar(120) not null,
                            recycled_at varchar(40),
                            recycled_by varchar(80),
                            recycle_batch varchar(40),
                            recycle_reason varchar(200)
                        )
                        """);
                statement.execute("""
                        create table biz_mother_order_item (
                            id bigint generated by default as identity primary key,
                            mother_order_code varchar(80) not null,
                            material_code varchar(80) not null,
                            quantity decimal(18,3) not null,
                            quantity_unit varchar(20) not null,
                            weight decimal(18,3) not null,
                            weight_unit varchar(20) not null,
                            ledger_code varchar(80) not null
                        )
                        """);
                statement.execute("""
                        create table biz_mother_order_split (
                            mother_order_code varchar(80) primary key,
                            split_decision varchar(20) not null,
                            verification_code varchar(16) not null,
                            operator varchar(80) not null,
                            created_at varchar(40) not null
                        )
                        """);
                statement.execute("""
                        create table biz_work_order (
                            id bigint generated by default as identity primary key,
                            code varchar(90) not null unique,
                            mother_order_code varchar(80) not null,
                            customer_id bigint,
                            customer_name varchar(120) not null,
                            processing_requirement varchar(500) not null,
                            due_date varchar(20) not null,
                            material_code varchar(80) not null,
                            goods_name varchar(160) not null,
                            quantity decimal(18,3) not null,
                            quantity_unit varchar(20) not null,
                            weight decimal(18,3) not null,
                            warehouse_code varchar(50) not null,
                            process_names varchar(80) not null,
                            verification_code varchar(16) not null unique,
                            ledger_code varchar(80) not null,
                            operator varchar(80) not null,
                            created_at varchar(40) not null,
                            recycled_at varchar(40),
                            recycled_by varchar(80),
                            recycle_batch varchar(40),
                            recycle_reason varchar(200)
                        )
                        """);
                statement.execute("""
                        create table biz_process_station_handoff (
                            id bigint generated by default as identity primary key,
                            order_code varchar(90) not null,
                            process_name varchar(20) not null,
                            operator varchar(80) not null,
                            ledger_code varchar(80) not null,
                            created_at varchar(40) not null,
                            unique (order_code, process_name)
                        )
                        """);
                apply(statement, "src/main/resources/db/mysql/V35__m3_quality_requirement_check.sql");
                apply(statement, "src/main/resources/db/mysql/V44__m3_command_request.sql");
                apply(statement, "src/main/resources/db/mysql/V45__m3_mother_order_courier_identity.sql");
                apply(statement, "src/main/resources/db/mysql/V46__m3_quality_round.sql");
            }
            documents = new JdbcBusinessDocumentRepository(dataSource);
            stock = new JdbcStockLedgerRepository(dataSource);
            requests = new JdbcCommandRequestRepository(dataSource);
            stocks = new StockLedgerService(stock);
            operations = new BusinessOperationService(stocks, null, documents);
            splits = new MotherOrderSplitService(documents, stocks);
            station = new ProcessStationService(documents, stocks, operations, new ScanCodeParser());
            BusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, requests, resultProbe);
            quality = new QualityRequirementService(
                    documents, stocks, operations, new ScanCodeParser(), null, unit, factProbe);
        }

        @Override
        public QualityRequirementService quality() {
            return quality;
        }

        @Override
        public ProcessStationService station() {
            return station;
        }

        @Override
        public BusinessOperationService operations() {
            return operations;
        }

        @Override
        public MotherOrderSplitService splits() {
            return splits;
        }

        @Override
        public BigDecimal semi(String mother, long customerId) {
            return quantity(mother, customerId, StockStatus.SEMI_FINISHED);
        }

        @Override
        public BigDecimal frozen(String mother, long customerId) {
            return quantity(mother, customerId, StockStatus.FROZEN);
        }

        @Override
        public BigDecimal rework(String mother, long customerId) {
            return quantity(mother, customerId, StockStatus.REWORK);
        }

        @Override
        public BigDecimal finished(String mother, long customerId) {
            return quantity(mother, customerId, StockStatus.FINISHED);
        }

        @Override
        public int freezes(String mother) {
            return count(mother, "quality_freeze");
        }

        @Override
        public int dispatches(String mother) {
            return count(mother, "rework_dispatch");
        }

        @Override
        public int reworkReports(String mother) {
            return count(mother, "rework_report");
        }

        @Override
        public int releases(String mother) {
            return count(mother, "quality_release");
        }

        @Override
        public int rounds(String mother) {
            return documents.listQualityRounds(mother).size();
        }

        @Override
        public QualityRound round(World world, String mother, int roundNo) {
            return documents.listQualityRounds(mother).stream()
                    .filter(item -> item.roundNo() == roundNo)
                    .findFirst()
                    .orElseThrow();
        }

        @Override
        public String reports(String mother) {
            return documents.findQualityReworkReport("FG-" + mother).orElseThrow().ledgerCode();
        }

        @Override
        public QualityRequirementCheck check(String mother) {
            return documents.findQualityRequirementCheck(mother).orElseThrow();
        }

        @Override
        public boolean hasCheck(String mother) {
            return documents.findQualityRequirementCheck(mother).isPresent();
        }

        @Override
        public boolean hasRequest(String requestId) {
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(
                         "select count(*) from biz_command_request where request_id = '" + requestId + "'")) {
                rs.next();
                return rs.getInt(1) > 0;
            } catch (SQLException ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public World withProbes(CommandResultProbe resultProbe, QualityFactSaveProbe factProbe) {
            BusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, requests, resultProbe);
            QualityRequirementService next = new QualityRequirementService(
                    documents, stocks, operations, new ScanCodeParser(), null, unit, factProbe);
            return new WorldView(next, this);
        }

        private int count(String mother, String documentType) {
            return (int) stock.findAllLedgers().stream()
                    .filter(entry -> mother.equals(entry.to().motherOrderCode()) && documentType.equals(entry.documentType()))
                    .count();
        }

        private BigDecimal quantity(String mother, long customerId, StockStatus status) {
            return stock.findBalance(new StockDimension(
                    OwnerType.CUSTOMER, customerId, customerId, mother, mother, "AU9999", "RAW-CUSTOMER", status
            )).quantity();
        }

        private void close() {
        }

        private static void apply(Statement statement, String path) throws Exception {
            String sql = Files.readString(Path.of(path), StandardCharsets.UTF_8);
            for (String item : MigrationCatalog.statements(sql)) {
                statement.execute(item);
            }
        }
    }

    private record WorldView(QualityRequirementService quality, World delegate) implements World {
        @Override
        public ProcessStationService station() {
            return delegate.station();
        }

        @Override
        public BusinessOperationService operations() {
            return delegate.operations();
        }

        @Override
        public MotherOrderSplitService splits() {
            return delegate.splits();
        }

        @Override
        public BigDecimal semi(String mother, long customerId) {
            return delegate.semi(mother, customerId);
        }

        @Override
        public BigDecimal frozen(String mother, long customerId) {
            return delegate.frozen(mother, customerId);
        }

        @Override
        public BigDecimal rework(String mother, long customerId) {
            return delegate.rework(mother, customerId);
        }

        @Override
        public BigDecimal finished(String mother, long customerId) {
            return delegate.finished(mother, customerId);
        }

        @Override
        public int freezes(String mother) {
            return delegate.freezes(mother);
        }

        @Override
        public int dispatches(String mother) {
            return delegate.dispatches(mother);
        }

        @Override
        public int reworkReports(String mother) {
            return delegate.reworkReports(mother);
        }

        @Override
        public int releases(String mother) {
            return delegate.releases(mother);
        }

        @Override
        public int rounds(String mother) {
            return delegate.rounds(mother);
        }

        @Override
        public QualityRound round(World world, String mother, int roundNo) {
            return delegate.round(world, mother, roundNo);
        }

        @Override
        public String reports(String mother) {
            return delegate.reports(mother);
        }

        @Override
        public QualityRequirementCheck check(String mother) {
            return delegate.check(mother);
        }

        @Override
        public boolean hasCheck(String mother) {
            return delegate.hasCheck(mother);
        }

        @Override
        public boolean hasRequest(String requestId) {
            return delegate.hasRequest(requestId);
        }

        @Override
        public World withProbes(CommandResultProbe resultProbe, QualityFactSaveProbe factProbe) {
            return delegate.withProbes(resultProbe, factProbe);
        }
    }
}
