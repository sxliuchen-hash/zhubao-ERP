package com.zhubao.erp.ops.app;

import com.zhubao.erp.ops.domain.MigrationScript;
import com.zhubao.erp.ops.domain.UpgradePlan;
import com.zhubao.erp.ops.infra.MigrationCatalog;

import javax.sql.DataSource;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

/**
 * 显式升级入口。普通启动不会调用它，也不会重跑 mysql-init。
 */
public final class DatabaseUpgradeMain {

    private DatabaseUpgradeMain() {
    }

    public static void main(String[] args) {
        int code = run(args, System.out, System.err);
        if (code != 0) {
            System.exit(code);
        }
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            Arguments arguments = Arguments.parse(args);
            List<MigrationScript> scripts = arguments.scriptsDir == null
                    ? MigrationCatalog.loadClasspath()
                    : MigrationCatalog.loadDirectory(arguments.scriptsDir);
            DatabaseUpgradeService service = new DatabaseUpgradeService(
                    new DriverDataSource(arguments.url, arguments.username, arguments.password),
                    scripts
            );
            UpgradePlan plan = switch (arguments.command) {
                case "plan" -> service.plan();
                case "adopt" -> service.adopt(arguments.backupConfirmed);
                case "upgrade" -> service.upgrade(arguments.backupConfirmed);
                default -> throw new UpgradeRejectedException("未知命令：" + arguments.command);
            };
            print(out, arguments.command, plan);
            return plan.problems().isEmpty() ? 0 : 2;
        } catch (RuntimeException ex) {
            err.println(ex.getMessage() == null ? ex.toString() : ex.getMessage());
            return 2;
        }
    }

    private static void print(PrintStream out, String command, UpgradePlan plan) {
        out.println("命令：" + command);
        out.println("结构探针连续版本：" + plan.detectedBaseline());
        out.println("空库：" + (plan.databaseEmpty() ? "是" : "否"));
        out.println("已登记：" + plan.applied().size());
        out.println("待执行：" + plan.pending().stream().map(script -> "V" + script.version()).toList());
        for (String problem : plan.problems()) {
            out.println("问题：" + problem);
        }
        if (plan.problems().isEmpty() && plan.pending().isEmpty()) {
            out.println("没有需要执行的新迁移。");
        }
    }

    private record Arguments(String command, String url, String username, String password, Path scriptsDir,
                             boolean backupConfirmed) {
        private static Arguments parse(String[] args) {
            if (args.length == 0) {
                throw new UpgradeRejectedException("用法：plan|adopt|upgrade --url <jdbc> --username <用户> --password <密码> [--scripts <目录>] [--backup-confirmed]");
            }
            String command = args[0];
            String url = null;
            String username = null;
            String password = "";
            Path scriptsDir = null;
            boolean backupConfirmed = false;
            for (int i = 1; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--url" -> url = next(args, ++i, arg);
                    case "--username" -> username = next(args, ++i, arg);
                    case "--password" -> password = next(args, ++i, arg);
                    case "--scripts" -> scriptsDir = Path.of(next(args, ++i, arg));
                    case "--backup-confirmed" -> backupConfirmed = true;
                    default -> throw new UpgradeRejectedException("无法识别参数：" + arg);
                }
            }
            if (url == null || username == null) {
                throw new UpgradeRejectedException("必须提供 --url 和 --username");
            }
            if (("adopt".equals(command) || "upgrade".equals(command)) && !backupConfirmed) {
                throw new UpgradeRejectedException("升级前必须确认已经完成数据库备份");
            }
            return new Arguments(command, url, username, password, scriptsDir, backupConfirmed);
        }

        private static String next(String[] args, int index, String name) {
            if (index >= args.length) {
                throw new UpgradeRejectedException("参数缺少取值：" + name);
            }
            return args[index];
        }
    }

    private static final class DriverDataSource implements DataSource {
        private final String url;
        private final String username;
        private final String password;

        private DriverDataSource(String url, String username, String password) {
            this.url = url;
            this.username = username;
            this.password = password;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public PrintWriter getLogWriter() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setLoginTimeout(int seconds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
