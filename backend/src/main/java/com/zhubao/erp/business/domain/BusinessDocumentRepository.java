package com.zhubao.erp.business.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

public interface BusinessDocumentRepository {

    boolean motherOrderExists(String code);

    /** 在当前工作单元里判断母单号，能看见本事务尚未提交的写入。 */
    boolean motherOrderExists(String code, WorkSession session);

    MotherOrderRecord saveMotherOrder(MotherOrderRecord record);

    /** 在当前工作单元里保存母单，不单独提交。运单冲突时抛出重复运单异常。 */
    MotherOrderRecord saveMotherOrder(MotherOrderRecord record, WorkSession session);

    Optional<MotherOrderRecord> findMotherOrderByCode(String code);

    Optional<MotherOrderRecord> findMotherOrderByCode(String code, WorkSession session);

    Optional<MotherOrderRecord> findMotherOrderByCourierNo(String courierNo);

    Optional<MotherOrderRecord> findMotherOrderByCourierNo(String courierNo, WorkSession session);

    List<MotherOrderRecord> listMotherOrders();

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
}
