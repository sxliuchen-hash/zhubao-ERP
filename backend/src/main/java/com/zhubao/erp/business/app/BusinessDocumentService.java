package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.OutboundOrderRecord;
import com.zhubao.erp.common.error.BusinessException;
import org.springframework.stereotype.Service;

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
}
