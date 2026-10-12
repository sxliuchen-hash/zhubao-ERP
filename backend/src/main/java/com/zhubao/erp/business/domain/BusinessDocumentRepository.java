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

    Optional<MotherSplitState> findMotherSplitState(String motherOrderCode);

    /** 一次读取全部母单拆分状态，供进度和工作台在单次请求内建索引。 */
    List<MotherSplitState> listAllMotherSplitStates();

    boolean childWorkOrderExists(String code);

    ChildWorkOrderRecord saveChildWorkOrder(ChildWorkOrderRecord record);

    Optional<ChildWorkOrderRecord> findChildWorkOrderByCode(String code);

    List<ChildWorkOrderRecord> listChildWorkOrders(String motherOrderCode);

    List<ChildWorkOrderRecord> listAllChildWorkOrders();

    boolean verificationCodeExists(String verificationCode);

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
