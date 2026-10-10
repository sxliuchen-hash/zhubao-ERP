package com.zhubao.erp.business.domain;

import java.util.List;
import java.util.Optional;

public interface BusinessDocumentRepository {

    boolean motherOrderExists(String code);

    MotherOrderRecord saveMotherOrder(MotherOrderRecord record);

    Optional<MotherOrderRecord> findMotherOrderByCode(String code);

    Optional<MotherOrderRecord> findMotherOrderByCourierNo(String courierNo);

    List<MotherOrderRecord> listMotherOrders();

    boolean outboundOrderExists(String code);

    OutboundOrderRecord saveOutboundOrder(OutboundOrderRecord record);

    Optional<OutboundOrderRecord> findOutboundOrderByCode(String code);

    void saveMotherSplitState(MotherSplitState state);

    Optional<MotherSplitState> findMotherSplitState(String motherOrderCode);

    boolean childWorkOrderExists(String code);

    ChildWorkOrderRecord saveChildWorkOrder(ChildWorkOrderRecord record);

    Optional<ChildWorkOrderRecord> findChildWorkOrderByCode(String code);

    List<ChildWorkOrderRecord> listChildWorkOrders(String motherOrderCode);

    List<ChildWorkOrderRecord> listAllChildWorkOrders();

    boolean verificationCodeExists(String verificationCode);

    LabelReprintLog saveLabelReprint(LabelReprintLog log);

    List<LabelReprintLog> listLabelReprints(String labelType, String businessCode);

    void saveProcessStationHandoff(ProcessStationHandoff handoff);

    Optional<ProcessStationHandoff> findProcessStationHandoff(String orderCode, String processName);

    List<ProcessStationHandoff> listProcessStationHandoffs(String orderCode);

    List<ProcessStationHandoff> listAllProcessStationHandoffs();

    void saveQualityRequirementCheck(QualityRequirementCheck check);

    Optional<QualityRequirementCheck> findQualityRequirementCheck(String orderCode);

    List<QualityRequirementCheck> listAllQualityRequirementChecks();

    void attachQualityRework(String orderCode, String reworkOrderCode);
}
