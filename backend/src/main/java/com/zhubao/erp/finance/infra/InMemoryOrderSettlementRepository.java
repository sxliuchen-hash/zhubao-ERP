package com.zhubao.erp.finance.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.OrderSettlement;
import com.zhubao.erp.finance.domain.OrderSettlementItem;
import com.zhubao.erp.finance.domain.OrderSettlementPosting;
import com.zhubao.erp.finance.domain.OrderSettlementRepository;
import com.zhubao.erp.finance.domain.OrderSettlementSource;
import com.zhubao.erp.finance.domain.SettlementStatus;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 内存结单仓储。本地默认使用；若会话带着 JDBC 连接则拒绝写入，避免和数据库账各记一半。
 */
@Repository
@ConditionalOnProperty(name = "zhubao.finance.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryOrderSettlementRepository implements OrderSettlementRepository {

    private final Map<String, OrderSettlement> headers = new LinkedHashMap<>();
    private final Map<String, List<OrderSettlementItem>> items = new LinkedHashMap<>();
    private final Map<String, List<OrderSettlementSource>> sources = new LinkedHashMap<>();
    private final Map<String, List<OrderSettlementPosting>> postings = new LinkedHashMap<>();
    private boolean enabled;

    public InMemoryOrderSettlementRepository() {
        this(true);
    }

    /** 测试可以显式关闭，用来证明未启用时不能确认结单。 */
    public InMemoryOrderSettlementRepository(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public synchronized boolean policyEnabled() {
        return enabled;
    }

    @Override
    public synchronized void enablePolicy(String enabledAt, String operator, WorkSession session) {
        assertMemory(session);
        boolean previous = enabled;
        enabled = true;
        if (session != null) {
            session.onRollback(() -> {
                synchronized (this) {
                    enabled = previous;
                }
            });
        }
    }

    @Override
    public synchronized Optional<OrderSettlement> find(String code, WorkSession session) {
        assertMemory(session);
        return Optional.ofNullable(headers.get(code));
    }

    @Override
    public synchronized Optional<OrderSettlement> latest(String targetType, String targetCode, WorkSession session) {
        assertMemory(session);
        return headers.values().stream()
                .filter(row -> row.targetType().equals(targetType) && row.targetCode().equals(targetCode))
                .max(Comparator.comparingInt(OrderSettlement::versionNo));
    }

    @Override
    public synchronized List<OrderSettlementItem> items(String settlementCode, WorkSession session) {
        assertMemory(session);
        return List.copyOf(items.getOrDefault(settlementCode, List.of()));
    }

    @Override
    public synchronized List<OrderSettlementSource> sources(String settlementCode, WorkSession session) {
        assertMemory(session);
        return List.copyOf(sources.getOrDefault(settlementCode, List.of()));
    }

    @Override
    public synchronized List<OrderSettlementPosting> postings(String settlementCode, WorkSession session) {
        assertMemory(session);
        return List.copyOf(postings.getOrDefault(settlementCode, List.of()));
    }

    @Override
    public synchronized List<OrderSettlement> list(String status, int offset, int limit) {
        return page(filtered(status), offset, limit);
    }

    @Override
    public synchronized int count(String status) {
        return filtered(status).size();
    }

    @Override
    public synchronized OrderSettlement saveDraft(
            OrderSettlement header,
            List<OrderSettlementItem> itemRows,
            List<OrderSettlementSource> sourceRows,
            int expectedVersion,
            WorkSession session
    ) {
        assertMemory(session);
        if (session == null) {
            throw new IllegalStateException("结单写入必须处于业务工作单元");
        }
        Optional<OrderSettlement> current = latest(header.targetType(), header.targetCode(), session);
        if (current.isEmpty()) {
            if (expectedVersion != 0) {
                throw new BusinessException("结单草稿不存在，请刷新后重试");
            }
            OrderSettlement created = copy(header, header.code(), header.code(), 1, SettlementStatus.DRAFT, 1, "", "", header.createdAt());
            put(created, itemRows, sourceRows, List.of());
            session.onRollback(() -> {
                synchronized (InMemoryOrderSettlementRepository.this) {
                    remove(created.code());
                }
            });
            return created;
        }
        OrderSettlement existing = current.get();
        if (existing.status() == SettlementStatus.CONFIRMED) {
            throw new BusinessException("该单已确认结单，不能再覆盖原版本");
        }
        if (expectedVersion == 0) {
            throw new BusinessException("已有结单草稿，请按当前版本保存");
        }
        if (existing.rowVersion() != expectedVersion) {
            throw new BusinessException("结单草稿已变化，请刷新后重新保存");
        }
        OrderSettlement previous = existing;
        List<OrderSettlementItem> previousItems = items.get(existing.code());
        List<OrderSettlementSource> previousSources = sources.get(existing.code());
        List<OrderSettlementPosting> previousPostings = postings.getOrDefault(existing.code(), List.of());
        OrderSettlement updated = copy(header, existing.code(), existing.rootCode(), existing.versionNo(),
                SettlementStatus.DRAFT, existing.rowVersion() + 1, existing.confirmedBy(), existing.confirmedAt(), existing.createdAt());
        put(updated, itemRows, sourceRows, previousPostings);
        session.onRollback(() -> {
            synchronized (InMemoryOrderSettlementRepository.this) {
                put(previous, previousItems, previousSources, previousPostings);
            }
        });
        return updated;
    }

    @Override
    public synchronized OrderSettlement markConfirmed(
            String code, int expectedVersion, String confirmedBy, String postingMonth, String confirmedAt, WorkSession session
    ) {
        assertMemory(session);
        if (session == null) {
            throw new IllegalStateException("结单写入必须处于业务工作单元");
        }
        OrderSettlement existing = headers.get(code);
        if (existing == null || existing.status() != SettlementStatus.DRAFT) {
            throw new BusinessException("只能确认尚未入账的结单草稿");
        }
        if (existing.rowVersion() != expectedVersion) {
            throw new BusinessException("结单草稿已变化，请刷新后重新确认");
        }
        if (openingExists(existing.targetType(), existing.targetCode(), existing.code())) {
            throw new BusinessException("该单已经有确认结单，不能再次确认首份费用");
        }
        OrderSettlement confirmed = new OrderSettlement(
                existing.code(), existing.rootCode(), existing.versionNo(), existing.previousCode(),
                existing.targetType(), existing.targetCode(), existing.motherOrderCode(), existing.customerId(),
                existing.customerName(), SettlementStatus.CONFIRMED, existing.rowVersion() + 1, existing.deliveryRevision(),
                existing.customerNet(), existing.employeeNet(), existing.customerWaived(), existing.waiveReason(),
                existing.noInternalLabor(), existing.noLaborReason(), existing.preparer(),
                confirmedBy == null ? "" : confirmedBy, existing.createdAt(),
                confirmedAt == null ? "" : confirmedAt, postingMonth == null ? "" : postingMonth, existing.legacyState()
        );
        OrderSettlement previous = existing;
        headers.put(code, confirmed);
        session.onRollback(() -> headers.put(code, previous));
        return confirmed;
    }

    @Override
    public synchronized void savePostings(String settlementCode, List<OrderSettlementPosting> rows, WorkSession session) {
        assertMemory(session);
        if (session == null) {
            throw new IllegalStateException("结单写入必须处于业务工作单元");
        }
        List<OrderSettlementPosting> previous = postings.get(settlementCode);
        postings.put(settlementCode, List.copyOf(rows));
        session.onRollback(() -> {
            if (previous == null) {
                postings.remove(settlementCode);
            } else {
                postings.put(settlementCode, previous);
            }
        });
    }

    @Override
    public synchronized void lockTarget(String targetType, String targetCode, WorkSession session) {
        assertMemory(session);
        if (session == null) {
            throw new IllegalStateException("结单锁定必须处于业务工作单元");
        }
    }

    private boolean openingExists(String targetType, String targetCode, String exceptCode) {
        return headers.values().stream().anyMatch(row -> row.status() == SettlementStatus.CONFIRMED
                && row.previousCode().isBlank()
                && row.targetType().equals(targetType)
                && row.targetCode().equals(targetCode)
                && !row.code().equals(exceptCode));
    }

    private void put(OrderSettlement header, List<OrderSettlementItem> itemRows, List<OrderSettlementSource> sourceRows,
                     List<OrderSettlementPosting> postingRows) {
        headers.put(header.code(), header);
        items.put(header.code(), List.copyOf(itemRows));
        sources.put(header.code(), List.copyOf(sourceRows));
        if (postingRows != null) {
            postings.put(header.code(), List.copyOf(postingRows));
        }
    }

    private void remove(String code) {
        headers.remove(code);
        items.remove(code);
        sources.remove(code);
        postings.remove(code);
    }

    private List<OrderSettlement> filtered(String status) {
        String expected = status == null ? "" : status.trim();
        return headers.values().stream()
                .filter(row -> expected.isEmpty() || row.status().name().equals(expected))
                .sorted(Comparator.comparing(OrderSettlement::code))
                .toList();
    }

    private static List<OrderSettlement> page(List<OrderSettlement> rows, int offset, int limit) {
        int from = Math.max(0, offset);
        if (from >= rows.size() || limit <= 0) {
            return List.of();
        }
        return List.copyOf(rows.subList(from, Math.min(rows.size(), from + limit)));
    }

    private static OrderSettlement copy(
            OrderSettlement header, String code, String root, int versionNo, SettlementStatus status, int rowVersion,
            String confirmedBy, String confirmedAt, String createdAt
    ) {
        return new OrderSettlement(
                code, root, versionNo, header.previousCode(), header.targetType(), header.targetCode(),
                header.motherOrderCode(), header.customerId(), header.customerName(), status, rowVersion,
                header.deliveryRevision(), header.customerNet(), header.employeeNet(), header.customerWaived(),
                header.waiveReason(), header.noInternalLabor(), header.noLaborReason(), header.preparer(),
                confirmedBy, createdAt, confirmedAt, header.postingMonth(), header.legacyState()
        );
    }

    private static void assertMemory(WorkSession session) {
        if (session != null && session.connection().isPresent()) {
            throw new IllegalStateException("结单仓储与工作单元持久化不一致，拒绝写入");
        }
    }
}
