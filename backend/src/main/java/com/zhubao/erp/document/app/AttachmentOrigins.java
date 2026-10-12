package com.zhubao.erp.document.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.document.domain.EvidenceCodes;

import java.util.ArrayList;
import java.util.List;

/**
 * 按服务端已有单据判断附件属于哪一种真实单据。编码撞上多种单据时不猜测。
 */
public final class AttachmentOrigins {

    private AttachmentOrigins() {
    }

    public static List<String> hits(BusinessDocumentRepository documents, String businessCode) {
        String code = businessCode == null ? "" : businessCode.trim();
        List<String> hits = new ArrayList<>();
        if (code.isEmpty() || documents == null) {
            return hits;
        }
        if (documents.findMotherOrderByCode(code).isPresent()) {
            hits.add(EvidenceCodes.MOTHER_ORDER);
        }
        if (documents.findChildWorkOrderByCode(code).isPresent()) {
            hits.add(EvidenceCodes.WORK_ORDER);
        }
        if (documents.findOutboundOrderByCode(code).isPresent()) {
            hits.add(EvidenceCodes.OUTBOUND_ORDER);
        }
        boolean rework = documents.listAllQualityRequirementChecks().stream()
                .anyMatch(row -> code.equals(row.reworkOrderCode()));
        if (rework) {
            hits.add(EvidenceCodes.REWORK);
        }
        return hits;
    }

    /**
     * 只有一种真实单据，并且和请求类型一致时才返回稳定代码。
     */
    public static String uniqueMatch(BusinessDocumentRepository documents, String requestedType, String businessCode) {
        String wanted = EvidenceCodes.canonicalObjectType(requestedType);
        if (wanted.isEmpty()) {
            return "";
        }
        List<String> hits = hits(documents, businessCode);
        if (hits.size() == 1 && wanted.equals(hits.get(0))) {
            return wanted;
        }
        return "";
    }

    /**
     * 不带请求类型时，仅在服务端恰好有一种单据时返回来源。
     */
    public static String onlyHit(BusinessDocumentRepository documents, String businessCode) {
        List<String> hits = hits(documents, businessCode);
        return hits.size() == 1 ? hits.get(0) : "";
    }
}
