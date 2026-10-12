package com.zhubao.erp.work.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.CommandRequest;
import com.zhubao.erp.work.domain.CommandResultProbe;
import com.zhubao.erp.work.domain.DuplicateCommandRequestException;
import com.zhubao.erp.work.domain.WorkSession;
import com.zhubao.erp.work.infra.JdbcCommandRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/**
 * JDBC 工作单元。一次命令只使用这里打开的连接，成功时连同请求结果一起提交，失败时整单回滚。
 * 来料建单、带请求编号的入库确认和带请求编号的工序交付必须改用 {@link WorkSession#connection()}。
 * 未带请求编号的岗位交付、通用报工、质检、发货和登记导入若仍调用各自新建连接的保存方法，不会进入本事务。
 */
@Service
@ConditionalOnProperty(name = "zhubao.command-request.repository", havingValue = "jdbc")
public class JdbcBusinessWorkUnit implements BusinessWorkUnit {

    private static final int ATTEMPTS = 40;

    private final DataSource dataSource;
    private final JdbcCommandRequestRepository requests;
    private final CommandResultProbe probe;

    @Autowired
    public JdbcBusinessWorkUnit(DataSource dataSource, JdbcCommandRequestRepository requests) {
        this(dataSource, requests, CommandResultProbe.NONE);
    }

    public JdbcBusinessWorkUnit(DataSource dataSource, JdbcCommandRequestRepository requests, CommandResultProbe probe) {
        this.dataSource = dataSource;
        this.requests = requests;
        this.probe = probe == null ? CommandResultProbe.NONE : probe;
    }

    @Override
    public String execute(CommandCall call, BusinessWork work) {
        CommandCall normalized = CommandReplay.normalize(call);
        String hash = CommandReplay.hash(normalized);
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            String result = attempt(normalized, hash, work);
            if (result != null) {
                return result;
            }
            pause();
        }
        throw new BusinessException("请求正在处理，请稍后重试");
    }

    private String attempt(CommandCall call, String hash, BusinessWork work) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previous = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                CommandRequest existing = requests.findByRequestId(connection, call.requestId()).orElse(null);
                if (existing != null) {
                    if (existing.resultText().isBlank()) {
                        connection.rollback();
                        return null;
                    }
                    CommandReplay.assertReplay(call, hash, existing);
                    connection.rollback();
                    return existing.resultText();
                }
                requests.insert(connection, reserved(call, hash));
                String result = CommandReplay.requireResult(work.run(new JdbcSession(connection)));
                probe.beforeResultInsert();
                requests.updateResult(connection, call.requestId(), result);
                connection.commit();
                return result;
            } catch (DuplicateCommandRequestException ex) {
                rollback(connection);
                return replayCommitted(call, hash);
            } catch (RuntimeException ex) {
                rollback(connection);
                throw ex;
            } finally {
                restoreAutoCommit(connection, previous);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法开启命令事务", ex);
        }
    }

    private String replayCommitted(CommandCall call, String hash) {
        CommandRequest winner = requests.findByRequestId(call.requestId()).orElse(null);
        if (winner == null || winner.resultText().isBlank()) {
            return null;
        }
        CommandReplay.assertReplay(call, hash, winner);
        return winner.resultText();
    }

    private CommandRequest reserved(CommandCall call, String hash) {
        return new CommandRequest(
                call.requestId(),
                call.actorId(),
                call.action(),
                call.targetType(),
                call.targetCode(),
                hash,
                ""
        );
    }

    private void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法回滚命令事务", ex);
        }
    }

    private void restoreAutoCommit(Connection connection, boolean previous) {
        try {
            connection.setAutoCommit(previous);
        } catch (SQLException ex) {
            throw new IllegalStateException("无法恢复连接提交状态", ex);
        }
    }

    private void pause() {
        try {
            Thread.sleep(25);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("请求正在处理，请稍后重试");
        }
    }

    private record JdbcSession(Connection jdbcConnection) implements WorkSession {
        @Override
        public Optional<Connection> connection() {
            return Optional.of(jdbcConnection);
        }

        @Override
        public void onRollback(Runnable undo) {
            // JDBC 由连接回滚撤销本次写入。
        }
    }
}
