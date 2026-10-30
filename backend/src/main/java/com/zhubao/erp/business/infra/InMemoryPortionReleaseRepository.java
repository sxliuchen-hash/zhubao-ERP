package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.PortionRelease;
import com.zhubao.erp.business.domain.PortionReleaseRepository;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.portion-release.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryPortionReleaseRepository implements PortionReleaseRepository {

    private final ConcurrentHashMap<String, PortionRelease> rows = new ConcurrentHashMap<>();

    @Override
    public Optional<PortionRelease> find(String bagId) {
        return Optional.ofNullable(rows.get(bagId));
    }

    @Override
    public void save(PortionRelease release, WorkSession session) {
        PortionRelease previous = rows.get(release.bagId());
        rows.put(release.bagId(), release);
        if (session != null) {
            session.onRollback(() -> {
                if (previous == null) {
                    rows.remove(release.bagId(), release);
                } else {
                    rows.put(release.bagId(), previous);
                }
            });
        }
    }
}
