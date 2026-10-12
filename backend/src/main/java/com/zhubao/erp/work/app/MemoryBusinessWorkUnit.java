package com.zhubao.erp.work.app;

import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.CommandRequest;
import com.zhubao.erp.work.domain.CommandResultProbe;
import com.zhubao.erp.work.domain.DuplicateCommandRequestException;
import com.zhubao.erp.work.domain.WorkSession;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 内存工作单元。同一进程内用锁保证一次只提交一个请求。
 * 这只覆盖单进程，不能当成多实例安全。
 */
@Service
@ConditionalOnProperty(name = "zhubao.command-request.repository", havingValue = "memory", matchIfMissing = true)
public class MemoryBusinessWorkUnit implements BusinessWorkUnit {

    private final InMemoryCommandRequestRepository requests;
    private final CommandResultProbe probe;
    private final Object gate = new Object();

    @Autowired
    public MemoryBusinessWorkUnit(InMemoryCommandRequestRepository requests) {
        this(requests, CommandResultProbe.NONE);
    }

    public MemoryBusinessWorkUnit(InMemoryCommandRequestRepository requests, CommandResultProbe probe) {
        this.requests = requests;
        this.probe = probe == null ? CommandResultProbe.NONE : probe;
    }

    @Override
    public String execute(CommandCall call, BusinessWork work) {
        CommandCall normalized = CommandReplay.normalize(call);
        String hash = CommandReplay.hash(normalized);
        synchronized (gate) {
            CommandRequest existing = requests.findByRequestId(normalized.requestId()).orElse(null);
            if (existing != null) {
                CommandReplay.assertReplay(normalized, hash, existing);
                return existing.resultText();
            }
            List<Runnable> undos = new ArrayList<>();
            try {
                String result = CommandReplay.requireResult(work.run(new MemorySession(undos)));
                probe.beforeResultInsert();
                requests.insert(new CommandRequest(
                        normalized.requestId(),
                        normalized.actorId(),
                        normalized.action(),
                        normalized.targetType(),
                        normalized.targetCode(),
                        hash,
                        result
                ));
                return result;
            } catch (DuplicateCommandRequestException ex) {
                rollback(undos);
                CommandRequest winner = requests.findByRequestId(normalized.requestId()).orElse(null);
                if (winner == null) {
                    throw ex;
                }
                CommandReplay.assertReplay(normalized, hash, winner);
                return winner.resultText();
            } catch (RuntimeException ex) {
                rollback(undos);
                throw ex;
            }
        }
    }

    private void rollback(List<Runnable> undos) {
        for (int index = undos.size() - 1; index >= 0; index--) {
            undos.get(index).run();
        }
    }

    private record MemorySession(List<Runnable> undos) implements WorkSession {
        @Override
        public Optional<Connection> connection() {
            return Optional.empty();
        }

        @Override
        public void onRollback(Runnable undo) {
            if (undo != null) {
                undos.add(undo);
            }
        }
    }
}
