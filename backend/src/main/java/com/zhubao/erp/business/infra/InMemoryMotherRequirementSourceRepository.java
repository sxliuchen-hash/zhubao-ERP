package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.MotherRequirementSource;
import com.zhubao.erp.business.domain.MotherRequirementSourceRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.business-document.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryMotherRequirementSourceRepository implements MotherRequirementSourceRepository {

    private final ConcurrentHashMap<String, MotherRequirementSource> sources = new ConcurrentHashMap<>();

    @Override
    public synchronized void save(MotherRequirementSource source, WorkSession session) {
        if (session == null) {
            throw new BusinessException("加工要求来源必须处于业务工作单元");
        }
        MotherRequirementSource previous = sources.get(source.motherOrderCode());
        sources.put(source.motherOrderCode(), source);
        session.onRollback(() -> {
            synchronized (this) {
                if (previous == null) {
                    sources.remove(source.motherOrderCode(), source);
                } else {
                    sources.put(source.motherOrderCode(), previous);
                }
            }
        });
    }

    @Override
    public Optional<MotherRequirementSource> find(String motherOrderCode) {
        return Optional.ofNullable(sources.get(motherOrderCode));
    }
}
