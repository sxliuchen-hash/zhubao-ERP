package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.business.domain.ProcessCatalog;
import com.zhubao.erp.business.domain.ProcessStep;
import com.zhubao.erp.business.domain.RouteSnapshot;
import com.zhubao.erp.business.domain.RouteSnapshotRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.UUID;

/**
 * 保存三类业务各自的路线快照。打样和金工不要求先有对方或普通母单。
 */
public class RouteSnapshotService {

    private final RouteSnapshotRepository repository;

    public RouteSnapshotService(RouteSnapshotRepository repository) {
        this.repository = repository;
    }

    public RouteSnapshot capture(BusinessKind kind, String targetCode, String material, WorkSession session) {
        if (kind == BusinessKind.PROCESSING) {
            BusinessTargetReference reference = BusinessTargetReference.parse(targetCode);
            if (!reference.processingMother() && reference.role() != com.zhubao.erp.business.domain.BusinessTargetRole.WORK_ORDER) {
                throw new BusinessException("普通加工路线只能挂在母单或子单上");
            }
        }
        List<String> steps = ProcessCatalog.defaults(kind, material).stream().map(ProcessStep::id).toList();
        int version = repository.list(kind, targetCode).stream().mapToInt(RouteSnapshot::version).max().orElse(0) + 1;
        RouteSnapshot snapshot = new RouteSnapshot(newId(), kind, targetCode.trim(), version, steps, false);
        repository.insert(snapshot, session);
        return snapshot;
    }

    public RouteSnapshot confirm(String snapshotId, WorkSession session) {
        RouteSnapshot current = repository.find(snapshotId).orElseThrow(() -> new BusinessException("路线快照不存在"));
        if (current.confirmed()) {
            return current;
        }
        RouteSnapshot confirmed = new RouteSnapshot(
                current.snapshotId(), current.kind(), current.targetCode(), current.version(), current.stepIds(), true);
        repository.insert(confirmed, session);
        return confirmed;
    }

    private static String newId() {
        return "RS-" + UUID.randomUUID();
    }
}
