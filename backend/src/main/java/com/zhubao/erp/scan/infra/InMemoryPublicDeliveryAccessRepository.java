package com.zhubao.erp.scan.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.scan.domain.PublicDeliveryAccess;
import com.zhubao.erp.scan.domain.PublicDeliveryAccessRepository;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.public-delivery.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryPublicDeliveryAccessRepository implements PublicDeliveryAccessRepository {

    private final ConcurrentHashMap<String, PublicDeliveryAccess> rows = new ConcurrentHashMap<>();

    @Override
    public void save(PublicDeliveryAccess access, WorkSession session) {
        PublicDeliveryAccess previous = rows.putIfAbsent(access.accessId(), access);
        if (previous != null) {
            throw new BusinessException("公开访问标识无效");
        }
        if (session != null) {
            session.onRollback(() -> rows.remove(access.accessId(), access));
        }
    }

    @Override
    public Optional<PublicDeliveryAccess> find(String accessId) {
        return Optional.ofNullable(rows.get(accessId));
    }

    @Override
    public void disable(String accessId, int expectedVersion, WorkSession session) {
        PublicDeliveryAccess current = rows.get(accessId);
        if (current == null) {
            throw new BusinessException("找不到公开交付");
        }
        if (current.version() != expectedVersion) {
            throw new BusinessException("公开访问已被其他人修改，请刷新后重试");
        }
        PublicDeliveryAccess disabled = new PublicDeliveryAccess(
                current.accessId(), current.deliveryId(), current.customerId(), current.bagCodes(),
                current.publicPhotoIds(), false, current.version() + 1);
        rows.put(accessId, disabled);
        if (session != null) {
            session.onRollback(() -> rows.put(accessId, current));
        }
    }
}
