package com.zhubao.erp.ops.app;

import com.zhubao.erp.ops.domain.MigrationRecord;
import com.zhubao.erp.ops.domain.MigrationScript;
import com.zhubao.erp.ops.domain.UpgradePlan;
import com.zhubao.erp.ops.domain.UpgradeStatus;
import com.zhubao.erp.ops.infra.JdbcMigrationStore;
import com.zhubao.erp.ops.infra.JdbcSchemaInspector;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

/**
 * 受控数据库升级。不会在应用启动时自动执行，也不会把已有库直接标成最新版本。
 */
public final class DatabaseUpgradeService {

    static final String RECOVERY = "MySQL 的结构变更不能整段回滚。请用升级前的完整备份恢复数据库。"
            + "失败或未完成的版本会留在 schema_migration，升级程序会停止而不是跳过。"
            + "只有在确认该版本没有留下半成品结构、并且脚本不需要重跑时，才可以手工删除对应记录；否则应恢复备份。";

    private final JdbcSchemaInspector inspector;
    private final JdbcMigrationStore store;
    private final List<MigrationScript> scripts;

    public DatabaseUpgradeService(DataSource dataSource, List<MigrationScript> scripts) {
        this.inspector = new JdbcSchemaInspector(dataSource);
        this.store = new JdbcMigrationStore(dataSource);
        this.scripts = List.copyOf(scripts);
    }

    public UpgradePlan plan() {
        List<MigrationRecord> applied = store.findAll();
        int detected = detectedBaseline();
        boolean gap = structureGap(detected);
        boolean empty = !inspector.hasBusinessTable();
        List<String> problems = problems(applied, detected, gap, empty);
        int completed = applied.isEmpty() ? detected : highestSuccess(applied);
        List<MigrationScript> pending = scripts.stream().filter(script -> script.version() > completed).toList();
        return new UpgradePlan(detected, gap, empty, applied, pending, problems);
    }

    /**
     * 只登记连续探针已经证明的版本，不重放脚本，也不登记探针未通过的更高版本。
     */
    public UpgradePlan adopt(boolean backupConfirmed) {
        requireBackup(backupConfirmed);
        UpgradePlan plan = plan();
        if (!plan.applied().isEmpty()) {
            throw new UpgradeRejectedException("版本表已有记录，不能再次做旧库基线登记");
        }
        if (plan.databaseEmpty()) {
            throw new UpgradeRejectedException("空库没有可登记的旧版本，请直接执行升级");
        }
        if (plan.structureGap()) {
            throw new UpgradeRejectedException("旧库结构不连续，拒绝登记基线，也不会把它标成最新版本");
        }
        if (plan.detectedBaseline() <= 0) {
            throw new UpgradeRejectedException("看不到可靠的旧库结构，拒绝登记基线");
        }
        store.ensureTable();
        for (MigrationScript script : scripts) {
            if (script.version() > plan.detectedBaseline()) {
                break;
            }
            store.insertBaseline(script);
        }
        return plan();
    }

    public UpgradePlan upgrade(boolean backupConfirmed) {
        requireBackup(backupConfirmed);
        UpgradePlan plan = plan();
        if (!plan.problems().isEmpty()) {
            throw new UpgradeRejectedException(String.join(" ", plan.problems()));
        }
        if (plan.applied().isEmpty() && !plan.databaseEmpty()) {
            throw new UpgradeRejectedException("已有库还没有版本记录。请先查看计划并显式登记基线，不能直接标成最新版本");
        }
        if (plan.pending().isEmpty()) {
            return plan;
        }
        store.ensureTable();
        for (MigrationScript script : plan.pending()) {
            store.insertApplying(script);
            try {
                store.executeScript(script.sql());
                store.markSuccess(script.version());
            } catch (RuntimeException ex) {
                store.markFailed(script.version(), ex.getMessage());
                throw new UpgradeRejectedException("迁移 V" + script.version() + " 失败，已停止后续版本。" + RECOVERY, ex);
            }
        }
        return plan();
    }

    private List<String> problems(List<MigrationRecord> applied, int detected, boolean gap, boolean empty) {
        List<String> problems = new ArrayList<>();
        if (gap) {
            problems.add("结构探针不连续，不能推断当前版本。");
        }
        for (MigrationRecord record : applied) {
            if (record.status() == UpgradeStatus.FAILED || record.status() == UpgradeStatus.APPLYING) {
                problems.add("V" + record.version() + " 状态为 " + record.status() + "。" + RECOVERY);
            }
            MigrationScript script = scripts.stream().filter(item -> item.version() == record.version()).findFirst().orElse(null);
            if (script == null) {
                problems.add("版本表中的 V" + record.version() + " 在当前脚本目录中不存在。");
                continue;
            }
            if (record.status() == UpgradeStatus.SUCCESS && !record.checksum().equalsIgnoreCase(script.checksum())) {
                problems.add("已执行迁移 V" + record.version() + " 的校验和已改变，拒绝继续。请恢复原来的历史脚本，不要改写已发布迁移。");
            }
        }
        if (!empty && detected == 0 && applied.isEmpty() && !gap) {
            problems.add("库内已有对象，但看不到 V1 结构，拒绝升级。");
        }
        if (applied.isEmpty() && !empty && detected > 0 && !gap) {
            problems.add("已有库连续结构只到 V" + detected + "。请先显式登记基线，不能直接标成最新或跳过未证明的版本。");
        }
        return List.copyOf(problems);
    }

    private int detectedBaseline() {
        int detected = 0;
        for (MigrationScript script : scripts) {
            if (script.version() == detected + 1 && inspector.matches(script.probe())) {
                detected = script.version();
            } else if (script.version() == detected + 1) {
                break;
            }
        }
        return detected;
    }

    private boolean structureGap(int detected) {
        for (MigrationScript script : scripts) {
            if (script.version() > detected + 1 && inspector.matches(script.probe())) {
                return true;
            }
        }
        return false;
    }

    private static int highestSuccess(List<MigrationRecord> applied) {
        return applied.stream()
                .filter(record -> record.status() == UpgradeStatus.SUCCESS)
                .mapToInt(MigrationRecord::version)
                .max()
                .orElse(0);
    }

    private static void requireBackup(boolean backupConfirmed) {
        if (!backupConfirmed) {
            throw new UpgradeRejectedException("升级前必须确认已经完成数据库备份");
        }
    }
}
