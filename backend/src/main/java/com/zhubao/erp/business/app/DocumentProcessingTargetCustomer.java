package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.business.domain.BusinessTargetRole;
import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.ProcessingTargetCustomer;
import com.zhubao.erp.business.domain.ProcessingTargetFacts;
import com.zhubao.erp.business.domain.BusinessDocumentRepository;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 从现有母单、子单读取客户和数量单位。打样号、金工号不查母单，也不创建单据。
 */
@Component
public class DocumentProcessingTargetCustomer implements ProcessingTargetCustomer {

    private final BusinessDocumentRepository documents;

    public DocumentProcessingTargetCustomer(BusinessDocumentRepository documents) {
        this.documents = documents;
    }

    @Override
    public Optional<ProcessingTargetFacts> facts(BusinessTargetReference target) {
        if (target == null || target.kind() != BusinessKind.PROCESSING) {
            return Optional.empty();
        }
        if (target.role() == BusinessTargetRole.MOTHER) {
            return documents.findMotherOrderByCode(target.code()).map(DocumentProcessingTargetCustomer::fromMother);
        }
        if (target.role() == BusinessTargetRole.WORK_ORDER) {
            return documents.findChildWorkOrderByCode(target.code()).map(DocumentProcessingTargetCustomer::fromChild);
        }
        return Optional.empty();
    }

    private static ProcessingTargetFacts fromMother(MotherOrderRecord order) {
        return new ProcessingTargetFacts(order.customerId(), order.quantityUnit());
    }

    private static ProcessingTargetFacts fromChild(ChildWorkOrderRecord order) {
        return new ProcessingTargetFacts(order.customerId(), order.quantityUnit());
    }
}
