package com.zhubao.erp.finance.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

/**
 * 结单凭据仓储。写入必须和财务账在同一个工作会话里，不能另开连接提前提交。
 */
public interface OrderSettlementRepository {

    /** 交货后结单是否已经启用。未启用时不得把它当成第二条计费通道。 */
    boolean policyEnabled();

    /** 启用交货后结单。只记录启用事实，不补历史金额。 */
    void enablePolicy(String enabledAt, String operator, WorkSession session);

    Optional<OrderSettlement> find(String code, WorkSession session);

    Optional<OrderSettlement> latest(String targetType, String targetCode, WorkSession session);

    List<OrderSettlementItem> items(String settlementCode, WorkSession session);

    List<OrderSettlementSource> sources(String settlementCode, WorkSession session);

    List<OrderSettlementPosting> postings(String settlementCode, WorkSession session);

    List<OrderSettlement> list(String status, int offset, int limit);

    int count(String status);

    /**
     * 新建或按版本更新草稿。已有首份确认时拒绝再写草稿。
     * expectedVersion 为 0 表示新建。
     */
    OrderSettlement saveDraft(
            OrderSettlement header,
            List<OrderSettlementItem> items,
            List<OrderSettlementSource> sources,
            int expectedVersion,
            WorkSession session
    );

    /** 把草稿标记为已确认。调用方须已在同一会话写入账务。 */
    OrderSettlement markConfirmed(String code, int expectedVersion, String confirmedBy, String postingMonth, String confirmedAt, WorkSession session);

    void savePostings(String settlementCode, List<OrderSettlementPosting> postings, WorkSession session);

    /** 锁定该目标已有结单行，供确认前重读。 */
    void lockTarget(String targetType, String targetCode, WorkSession session);
}
