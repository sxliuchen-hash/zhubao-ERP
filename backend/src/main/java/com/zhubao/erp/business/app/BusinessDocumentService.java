package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.MotherSplitState;
import com.zhubao.erp.business.domain.OutboundOrderRecord;
import com.zhubao.erp.business.domain.ProcessStationHandoff;
import com.zhubao.erp.business.domain.QualityRequirementCheck;
import com.zhubao.erp.business.domain.QualityRound;
import com.zhubao.erp.common.error.BusinessException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class BusinessDocumentService {

    private final BusinessDocumentRepository repository;

    public BusinessDocumentService(BusinessDocumentRepository repository) {
        this.repository = repository;
    }

    public MotherOrderRecord motherOrder(String code) {
        return repository.findMotherOrderByCode(code)
                .orElseThrow(() -> new BusinessException("未找到结构化母单：" + code));
    }

    public OutboundOrderRecord outboundOrder(String code) {
        return repository.findOutboundOrderByCode(code)
                .orElseThrow(() -> new BusinessException("未找到结构化出库单：" + code));
    }

    /** 扫码上下文只读母单，不存在时返回空，不写库存。 */
    public Optional<MotherOrderRecord> findMotherOrder(String code) {
        return repository.findMotherOrderByCode(code);
    }

    /** 按来料快递号读母单。没有母单是可新建来料的结果，不是失败。 */
    public Optional<MotherOrderRecord> findMotherOrderByCourierNo(String courierNo) {
        return repository.findMotherOrderByCourierNo(courierNo);
    }

    public Optional<ChildWorkOrderRecord> findChildWorkOrder(String code) {
        return repository.findChildWorkOrderByCode(code);
    }

    public List<ChildWorkOrderRecord> listChildWorkOrders(String motherOrderCode) {
        return repository.listChildWorkOrders(motherOrderCode);
    }

    public Optional<OutboundOrderRecord> findOutboundOrder(String code) {
        return repository.findOutboundOrderByCode(code);
    }

    public Optional<MotherSplitState> findMotherSplitState(String motherOrderCode) {
        return repository.findMotherSplitState(motherOrderCode);
    }

    public Optional<QualityRequirementCheck> findQualityRequirementCheck(String orderCode) {
        return repository.findQualityRequirementCheck(orderCode);
    }

    public List<QualityRound> listQualityRounds(String orderCode) {
        return repository.listQualityRounds(orderCode);
    }

    /** 返工没有独立单据，按质检记录上的返工号反查原单。 */
    public Optional<QualityRequirementCheck> findQualityByReworkOrderCode(String reworkOrderCode) {
        if (reworkOrderCode == null || reworkOrderCode.isBlank()) {
            return Optional.empty();
        }
        return repository.listAllQualityRequirementChecks().stream()
                .filter(check -> reworkOrderCode.equals(check.reworkOrderCode()))
                .findFirst();
    }

    public List<ProcessStationHandoff> listProcessStationHandoffs(String orderCode) {
        return repository.listProcessStationHandoffs(orderCode);
    }
}
