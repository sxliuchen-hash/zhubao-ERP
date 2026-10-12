package com.zhubao.erp.work.infra;

import com.zhubao.erp.work.domain.CommandRequest;
import com.zhubao.erp.work.domain.CommandRequestLookup;
import com.zhubao.erp.work.domain.DuplicateCommandRequestException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.command-request.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryCommandRequestRepository implements CommandRequestLookup {

    private final ConcurrentHashMap<String, CommandRequest> requests = new ConcurrentHashMap<>();

    @Override
    public Optional<CommandRequest> findByRequestId(String requestId) {
        return Optional.ofNullable(requests.get(requestId));
    }

    public void insert(CommandRequest request) {
        CommandRequest previous = requests.putIfAbsent(request.requestId(), request);
        if (previous != null) {
            throw new DuplicateCommandRequestException(null);
        }
    }
}
