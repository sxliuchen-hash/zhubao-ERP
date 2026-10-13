package com.zhubao.erp.label.infra;

import com.zhubao.erp.label.domain.LabelTrace;
import com.zhubao.erp.label.domain.LabelTraceRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
@ConditionalOnProperty(name = "zhubao.label-template.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryLabelTraceRepository implements LabelTraceRepository {

    private final CopyOnWriteArrayList<LabelTrace> traces = new CopyOnWriteArrayList<>();

    @Override
    public void save(LabelTrace trace) {
        traces.add(trace);
    }

    @Override
    public List<LabelTrace> listByMother(String motherOrderCode) {
        return traces.stream().filter(trace -> motherOrderCode.equals(trace.motherOrderCode())).toList();
    }
}