package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.RouteSnapshot;
import com.zhubao.erp.business.domain.RouteSnapshotRepository;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
@ConditionalOnProperty(name = "zhubao.route-snapshot.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryRouteSnapshotRepository implements RouteSnapshotRepository {

    private final List<RouteSnapshot> rows = new CopyOnWriteArrayList<>();

    @Override
    public void insert(RouteSnapshot snapshot, WorkSession session) {
        RouteSnapshot previous = find(snapshot.snapshotId()).orElse(null);
        rows.removeIf(row -> row.snapshotId().equals(snapshot.snapshotId()));
        rows.add(snapshot);
        if (session != null) {
            session.onRollback(() -> {
                rows.removeIf(row -> row.snapshotId().equals(snapshot.snapshotId()));
                if (previous != null) {
                    rows.add(previous);
                }
            });
        }
    }

    @Override
    public Optional<RouteSnapshot> find(String snapshotId) {
        return rows.stream().filter(row -> row.snapshotId().equals(snapshotId)).findFirst();
    }

    @Override
    public List<RouteSnapshot> list(BusinessKind kind, String targetCode) {
        return rows.stream()
                .filter(row -> row.kind() == kind && row.targetCode().equals(targetCode))
                .toList();
    }

    public List<RouteSnapshot> all() {
        return new ArrayList<>(rows);
    }
}
