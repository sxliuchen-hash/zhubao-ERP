package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.OrderAdjustment;
import com.zhubao.erp.business.domain.OrderRequirementRepository;
import com.zhubao.erp.business.domain.OrderRequirementVersion;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 子单加工要求直接生效。不创建改单，也不送财务确认。
 * 期限、路线和费用仍走改单。
 */
@Service
public class ChildRequirementService {

    public static final String REASON = "编辑加工要求";
    private static final int MAX_REQUIREMENT = 500;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final BusinessDocumentRepository documents;
    private final BusinessWorkUnit workUnit;
    private final BusinessDataAccess dataAccess;
    private final OrderRequirementRepository requirements;

    public ChildRequirementService(BusinessDocumentRepository documents, BusinessWorkUnit workUnit) {
        this(documents, workUnit, null, null);
    }

    @Autowired
    public ChildRequirementService(
            BusinessDocumentRepository documents,
            BusinessWorkUnit workUnit,
            BusinessDataAccess dataAccess,
            OrderRequirementRepository requirements
    ) {
        this.documents = documents;
        this.workUnit = workUnit;
        this.dataAccess = dataAccess;
        this.requirements = requirements;
    }

    public ChildWorkOrderRecord edit(String code, String requirement, String requestId, AuthUserAccount account) {
        if (account == null || account.operator() == null || account.operator().isBlank()) {
            throw new BusinessException("请先登录后再修改加工要求");
        }
        if (workUnit == null) {
            throw new BusinessException("加工要求保存尚未装配");
        }
        String key = required(code, "请填写子单号");
        String nextText = required(requirement, "请填写加工要求");
        if (nextText.length() > MAX_REQUIREMENT) {
            throw new BusinessException("加工要求不能超过 500 字");
        }
        ChildWorkOrderRecord preview = documents.findChildWorkOrderByCode(key)
                .orElseThrow(() -> new BusinessException("子单不存在"));
        if (dataAccess != null) {
            dataAccess.assertDocumentCustomer(BusinessDataAccess.contextOf(account), preview.motherOrderCode());
        }
        workUnit.execute(new CommandCall(
                required(requestId, "请保留原请求编号"),
                account.operator().trim(),
                "EDIT_CHILD_REQUIREMENT",
                "WORK_ORDER",
                key,
                nextText
        ), session -> {
            documents.lockMotherOrder(preview.motherOrderCode(), session);
            documents.lockChildWorkOrder(key, session);
            if (documents.childRecycle(key, session).isPresent()) {
                throw new BusinessException("子单已在回收站，不能改加工要求");
            }
            ChildWorkOrderRecord current = documents.findChildWorkOrderAny(key, session)
                    .orElseThrow(() -> new BusinessException("子单不存在"));
            if (nextText.equals(text(current.processingRequirement()))) {
                throw new BusinessException("加工要求没有变化");
            }
            String now = LocalDateTime.now().format(TIME);
            documents.correctChildWorkOrder(copy(current, nextText), new OrderAdjustment(
                    "WORK_ORDER", current.code(), clip(text(current.processingRequirement())), clip(nextText),
                    REASON, account.operator().trim(), now
            ), session);
            if (requirements != null) {
                OrderRequirementVersion effective = OrderRequirementLookup.current(
                        requirements, current.motherOrderCode(), current.code()).orElse(null);
                int ownVersion = requirements.current(current.motherOrderCode(), current.code())
                        .map(OrderRequirementVersion::versionNo).orElse(0);
                String due = clip(OrderRequirementLookup.dueDate(effective, text(current.dueDate())), 40);
                String route = effective != null && effective.routeText() != null && !effective.routeText().isBlank()
                        ? effective.routeText() : text(current.processes());
                String id = editId(requestId);
                requirements.saveVersion(new OrderRequirementVersion(
                        id,
                        current.motherOrderCode(),
                        current.code(),
                        ownVersion + 1,
                        nextText,
                        due,
                        clip(route, 200),
                        id,
                        now
                ), session);
            }
            return current.code();
        });
        return documents.findChildWorkOrderByCode(key).orElseThrow(() -> new BusinessException("子单不存在"));
    }

    private static ChildWorkOrderRecord copy(ChildWorkOrderRecord current, String requirement) {
        return new ChildWorkOrderRecord(
                current.code(), current.motherOrderCode(), current.customerId(), current.customerName(),
                requirement, current.dueDate(), current.materialCode(), current.goodsName(),
                current.quantity(), current.quantityUnit(), current.weight(), current.warehouseCode(),
                current.processes(), current.verificationCode(), current.ledgerCode(),
                current.operator(), current.createdAt()
        );
    }

    static String editId(String requestId) {
        String compact = requestId.replaceAll("[^A-Za-z0-9]", "");
        if (compact.length() > 36) {
            compact = compact.substring(compact.length() - 36);
        }
        return "RE" + compact;
    }

    private static String required(String value, String message) {
        String text = text(value);
        if (text.isBlank()) {
            throw new BusinessException(message);
        }
        return text;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String clip(String value, int max) {
        String text = text(value);
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static String clip(String value) {
        return clip(value, 1000);
    }
}
