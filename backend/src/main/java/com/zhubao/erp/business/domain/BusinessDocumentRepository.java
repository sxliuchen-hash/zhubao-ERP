package com.zhubao.erp.business.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

public interface BusinessDocumentRepository {

    /** 基础信息更正同时保存客户归属、当前收货流水及审计。 */
    default void correctMotherBasicInfo(MotherOrderRecord record, OrderAdjustment adjustment, WorkSession session) {
        correctMotherOrder(record, adjustment, session);
    }

    boolean motherOrderExists(String code);

    /** 在当前工作单元里判断母单号，能看见本事务尚未提交的写入。 */
    boolean motherOrderExists(String code, WorkSession session);

    MotherOrderRecord saveMotherOrder(MotherOrderRecord record);

    /** 在当前工作单元里保存母单，不单独提交。运单冲突时抛出重复运单异常。 */
    MotherOrderRecord saveMotherOrder(MotherOrderRecord record, WorkSession session);

    Optional<MotherOrderRecord> findMotherOrderByCode(String code);

    Optional<MotherOrderRecord> findMotherOrderByCode(String code, WorkSession session);

    List<MotherOrderRecord> listMotherOrdersByCourierNo(String courierNo);

    List<MotherOrderRecord> listMotherOrdersByCourierNo(String courierNo, WorkSession session);

    List<MotherOrderRecord> listMotherOrders();

    /** 新单逐行读取；旧单由仓储按单材质快照合成一条只读明细。 */
    List<ReceiptLine> listReceiptLines(String motherOrderCode);

    /** 只返回已经保存的收货明细，不把母单汇总行伪装成明细。 */
    List<ReceiptLine> listStoredReceiptLines(String motherOrderCode, WorkSession session);

    void replaceReceiptLine(String motherOrderCode, ReceiptLine line, WorkSession session);

    /** 母单是否已有子单、来源分配、工序交付或发货。 */
    boolean motherHasDownstream(String motherOrderCode, WorkSession session);

    void saveReceiptLine(String motherOrderCode, ReceiptLine line, WorkSession session);

    void updateMotherOrderSummary(String motherOrderCode, java.math.BigDecimal quantity,
                                  java.math.BigDecimal weight, WorkSession session);

    List<ChildLineAllocation> listChildAllocations(String motherOrderCode, WorkSession session);

    void saveChildAllocation(ChildLineAllocation allocation, WorkSession session);

    boolean outboundOrderExists(String code);

    /** 在当前工作单元里判断出货单号，能看见本事务尚未提交的写入。 */
    boolean outboundOrderExists(String code, WorkSession session);

    OutboundOrderRecord saveOutboundOrder(OutboundOrderRecord record);

    /** 在当前工作单元里保存出货单，不单独提交。重复出货单号抛出业务异常。 */
    OutboundOrderRecord saveOutboundOrder(OutboundOrderRecord record, WorkSession session);

    Optional<OutboundOrderRecord> findOutboundOrderByCode(String code);

    List<OutboundOrderRecord> listOutboundOrders();

    void saveMotherSplitState(MotherSplitState state);

    /** 在当前工作单元里保存拆分决定，不单独提交。 */
    void saveMotherSplitState(MotherSplitState state, WorkSession session);

    /** 在当前工作单元里删除整单生产决定，不单独提交。决定已不是整单生产时拒绝。 */
    void deleteMotherSplitState(String motherOrderCode, WorkSession session);

    Optional<MotherSplitState> findMotherSplitState(String motherOrderCode);

    /** 在当前工作单元连接上读取拆分决定，能看见本事务尚未提交的写入。 */
    Optional<MotherSplitState> findMotherSplitState(String motherOrderCode, WorkSession session);

    /**
     * 锁定母单，避免两笔拆单同时通过剩余量校验。
     * JDBC 对母单行加锁；内存由工作单元串行，这里只确认母单存在。
     */
    void lockMotherOrder(String motherOrderCode, WorkSession session);

    /** 一次读取全部母单拆分状态，供进度和工作台在单次请求内建索引。 */
    List<MotherSplitState> listAllMotherSplitStates();

    boolean childWorkOrderExists(String code);

    /** 在当前工作单元连接上判断子单号。 */
    boolean childWorkOrderExists(String code, WorkSession session);

    ChildWorkOrderRecord saveChildWorkOrder(ChildWorkOrderRecord record);

    /** 在当前工作单元里保存子单，不单独提交。 */
    ChildWorkOrderRecord saveChildWorkOrder(ChildWorkOrderRecord record, WorkSession session);

    Optional<ChildWorkOrderRecord> findChildWorkOrderByCode(String code);

    List<ChildWorkOrderRecord> listChildWorkOrders(String motherOrderCode);

    /** 在当前工作单元连接上读取子单，能看见本事务尚未提交的写入。 */
    List<ChildWorkOrderRecord> listChildWorkOrders(String motherOrderCode, WorkSession session);

    List<ChildWorkOrderRecord> listAllChildWorkOrders();

    boolean verificationCodeExists(String verificationCode);

    /** 在当前工作单元连接上判断验证码。 */
    boolean verificationCodeExists(String verificationCode, WorkSession session);

    LabelReprintLog saveLabelReprint(LabelReprintLog log);

    List<LabelReprintLog> listLabelReprints(String labelType, String businessCode);

    void saveProcessStationHandoff(ProcessStationHandoff handoff);

    /** 在当前工作单元里保存工序交付，不单独提交。重复交付抛出「这道工序已经交过」。 */
    void saveProcessStationHandoff(ProcessStationHandoff handoff, WorkSession session);

    Optional<ProcessStationHandoff> findProcessStationHandoff(String orderCode, String processName);

    /** 在当前工作单元连接上读取交付，能看见本事务尚未提交的写入。 */
    Optional<ProcessStationHandoff> findProcessStationHandoff(String orderCode, String processName, WorkSession session);

    List<ProcessStationHandoff> listProcessStationHandoffs(String orderCode);

    List<ProcessStationHandoff> listAllProcessStationHandoffs();

    /** 旧库可继续整单交工；分批写入必须先完成新增迁移。 */
    default boolean supportsProcessBatches() { return false; }

    /** 在工作单元内锁定整张工单，防止不同批次和不同工序并发超交。 */
    default void lockProcessBatchOrder(String orderCode, WorkSession session) {
        throw new IllegalStateException("分批交工尚未装配");
    }

    default List<ProcessBatchHandoff> listProcessBatches(String orderCode, WorkSession session) { return List.of(); }

    default List<ProcessBatchHandoff> listAllProcessBatches() { return List.of(); }

    default void saveProcessBatch(ProcessBatchHandoff batch, WorkSession session) {
        throw new IllegalStateException("分批交工尚未装配");
    }

    void saveQualityRequirementCheck(QualityRequirementCheck check);

    /** 在当前工作单元里保存质检对照，不单独提交。 */
    void saveQualityRequirementCheck(QualityRequirementCheck check, WorkSession session);

    Optional<QualityRequirementCheck> findQualityRequirementCheck(String orderCode);

    Optional<QualityRequirementCheck> findQualityRequirementCheck(String orderCode, WorkSession session);

    List<QualityRequirementCheck> listAllQualityRequirementChecks();

    void attachQualityRework(String orderCode, String reworkOrderCode);

    /** 在当前工作单元里关联返工单，不单独提交。 */
    void attachQualityRework(String orderCode, String reworkOrderCode, WorkSession session);

    void saveQualityRound(QualityRound round);

    /** 在当前工作单元里保存质检轮次，不单独提交。重复轮次抛出「这一轮质检已经提交」。 */
    void saveQualityRound(QualityRound round, WorkSession session);

    List<QualityRound> listQualityRounds(String orderCode);

    List<QualityRound> listQualityRounds(String orderCode, WorkSession session);

    void saveQualityReworkReport(QualityReworkReport report);

    /** 在当前工作单元里保存返工报工，不单独提交。 */
    void saveQualityReworkReport(QualityReworkReport report, WorkSession session);

    Optional<QualityReworkReport> findQualityReworkReport(String reworkOrderCode);

    Optional<QualityReworkReport> findQualityReworkReport(String reworkOrderCode, WorkSession session);

    /** 管理员维护时读取母单，包括已在回收站的。 */
    Optional<MotherOrderRecord> findMotherOrderAny(String code);

    /** 在当前工作单元里读取母单，包括回收站记录。 */
    Optional<MotherOrderRecord> findMotherOrderAny(String code, WorkSession session);

    Optional<ChildWorkOrderRecord> findChildWorkOrderAny(String code);

    /** 在当前工作单元里读取子单，包括回收站记录。 */
    Optional<ChildWorkOrderRecord> findChildWorkOrderAny(String code, WorkSession session);

    /** 锁定待改正的子单，串行核对页面快照与写入。 */
    void lockChildWorkOrder(String code, WorkSession session);

    /**
     * 母单存在时锁定该行。回收和恢复先锁母单，再锁子单和打印任务。
     */
    boolean lockMotherOrderIfPresent(String motherOrderCode, WorkSession session);

    /** 不加锁读取子单所属母单，只用于决定后续加锁顺序。 */
    Optional<String> childMotherCode(String childCode);

    List<ChildWorkOrderRecord> listChildWorkOrdersAny(String motherOrderCode);

    /** 在当前工作单元里按子单号顺序锁定读取，包含回收站中的子单。 */
    List<ChildWorkOrderRecord> listChildWorkOrdersAny(String motherOrderCode, WorkSession session);

    void replaceMotherOrder(MotherOrderRecord record);

    void replaceChildWorkOrder(ChildWorkOrderRecord record);

    /** 同次保存改单字段与调整记录；任一步失败不得留下半次改单。 */
    void correctMotherOrder(MotherOrderRecord record, OrderAdjustment adjustment);

    /** 在当前工作单元里保存母单字段与调整记录，不单独提交。 */
    void correctMotherOrder(MotherOrderRecord record, OrderAdjustment adjustment, WorkSession session);

    /** 同次保存改单字段与调整记录；任一步失败不得留下半次改单。 */
    void correctChildWorkOrder(ChildWorkOrderRecord record, OrderAdjustment adjustment);

    /** 在当前工作单元里保存子单字段与调整记录，不单独提交。 */
    void correctChildWorkOrder(ChildWorkOrderRecord record, OrderAdjustment adjustment, WorkSession session);

    void recycleMother(String code, String operator, String at, String batch, String reason);
    void recycleMother(String code, String operator, String at, String batch, String reason, WorkSession session);

    void recycleChild(String code, String operator, String at, String batch, String reason);
    void recycleChild(String code, String operator, String at, String batch, String reason, WorkSession session);

    Optional<OrderRecycleMark> motherRecycle(String code);

    /** 锁定读取母单回收标记，避免沿用命令事务开始时的旧快照。 */
    Optional<OrderRecycleMark> motherRecycle(String code, WorkSession session);

    Optional<OrderRecycleMark> childRecycle(String code);

    /** 锁定读取子单回收标记，避免沿用命令事务开始时的旧快照。 */
    Optional<OrderRecycleMark> childRecycle(String code, WorkSession session);

    List<OrderRecycleMark> listMotherRecycles();

    List<OrderRecycleMark> listChildRecycles();

    void clearChildRecycle(String code);
    void clearChildRecycle(String code, WorkSession session);

    /** 恢复同一次放入回收站的母单和子单。 */
    void clearRecycleBatch(String batch);
    void clearRecycleBatch(String batch, WorkSession session);

    void saveOrderAdjustment(OrderAdjustment adjustment);

    /** 调整记录与本次业务变更共用工作单元。 */
    void saveOrderAdjustment(OrderAdjustment adjustment, WorkSession session);

    List<OrderAdjustment> listOrderAdjustments(String targetType, String code);
}
