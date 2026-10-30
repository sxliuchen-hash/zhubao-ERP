package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.ActivityBagRepository;
import com.zhubao.erp.business.domain.OutsourcingDispatch;
import com.zhubao.erp.business.domain.OutsourcingDispatchRepository;
import com.zhubao.erp.business.domain.OutsourcingPositionView;
import com.zhubao.erp.business.domain.OutsourcingReturn;
import com.zhubao.erp.business.domain.OutsourcingReturnBalance;
import com.zhubao.erp.business.domain.OutsourcingReturnRepository;
import com.zhubao.erp.business.domain.OutsourcingSourceRepository;
import com.zhubao.erp.business.domain.OutsourcingTask;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.domain.DocumentManagementRepository;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.app.RefactorPermissionContract;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 外协位置查询。只读本厂、在外、待核和分次收回，不写发外、收回、库存、客户账或工资。
 */
@Service
public class OutsourcingQueryService {

    private final OutsourcingSourceRepository sources;
    private final OutsourcingDispatchRepository dispatches;
    private final OutsourcingReturnRepository returns;
    private final ActivityBagRepository bags;
    private final OutsourcingDispatchService custody;
    private final OutsourcingReturnService returnView;
    private final DocumentManagementRepository documents;
    private final PermissionGuard permissions;

    public OutsourcingQueryService(OutsourcingSourceRepository sources, OutsourcingDispatchRepository dispatches,
                                   OutsourcingReturnRepository returns, ActivityBagRepository bags,
                                   OutsourcingDispatchService custody, OutsourcingReturnService returnView,
                                   DocumentManagementRepository documents, PermissionGuard permissions) {
        this.sources = sources;
        this.dispatches = dispatches;
        this.returns = returns;
        this.bags = bags;
        this.custody = custody;
        this.returnView = returnView;
        this.documents = documents;
        this.permissions = permissions;
    }

    public OutsourcingPositionView position(String taskId, OperatorContext actor, String attachmentCode) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.OUTSOURCING_VIEW, actor);
        OutsourcingTask task = sources.findTask(taskId).orElseThrow(() -> new BusinessException("外协任务不存在"));
        assertOwner(actor, task);
        assertAttachment(task, attachmentCode);
        BigDecimal inHouse = BigDecimal.ZERO;
        List<OutsourcingPositionView.SourceLine> sources = new ArrayList<>();
        for (ActivityBag bag : bags.listByTarget(task.targetCode())) {
            if (!belongs(bag, task)) {
                continue;
            }
            String holder = custody.holderOf(bag.id());
            boolean inHouseStock = bag.activeLeaf() && OutsourcingDispatchService.IN_HOUSE.equals(holder);
            if (inHouseStock) {
                inHouse = inHouse.add(bag.quantity());
            }
            sources.add(new OutsourcingPositionView.SourceLine(
                    bag.id(), bag.quantity(), holder, inHouseStock, bag.sourceKey()));
        }
        BigDecimal out = custody.entrustedQuantity(task.taskId());
        List<OutsourcingPositionView.ReturnLine> lines = new ArrayList<>();
        for (OutsourcingDispatch dispatch : dispatches.listByTask(task.taskId())) {
            OutsourcingReturnBalance balance = returnView.balance(dispatch.dispatchId());
            for (OutsourcingReturn row : returns.listByDispatch(dispatch.dispatchId())) {
                lines.add(new OutsourcingPositionView.ReturnLine(
                        dispatch.dispatchId(), row.receivedQty(), row.unprocessedQty(), row.lossQty(), balance.stillOut()));
            }
        }
        String partyName = this.sources.findParty(task.partyId()).map(party -> party.name()).orElse("");
        return new OutsourcingPositionView(
                task.taskId(), task.targetCode(), task.nodeId(), task.partyId(), partyName,
                task.ownerType().name(), task.ownerRef(), inHouse, out, out, false, lines, sources);
    }

    public OutsourcingDispatch dispatch(String dispatchId, OperatorContext actor) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.OUTSOURCING_VIEW, actor);
        OutsourcingDispatch dispatch = dispatches.find(dispatchId).orElseThrow(() -> new BusinessException("外协发外不存在"));
        OutsourcingTask task = sources.findTask(dispatch.taskId()).orElseThrow(() -> new BusinessException("外协任务不存在"));
        assertOwner(actor, task);
        return dispatch;
    }

    private void assertOwner(OperatorContext actor, OutsourcingTask task) {
        Long customerId = null;
        if (task.ownerType() == OwnerType.CUSTOMER) {
            try {
                customerId = Long.valueOf(task.ownerRef());
            } catch (NumberFormatException ex) {
                throw new BusinessException("缺少物料权属");
            }
        }
        RefactorPermissionContract.assertCustomerRead(actor, customerId);
    }

    private void assertAttachment(OutsourcingTask task, String attachmentCode) {
        if (attachmentCode == null || attachmentCode.isBlank()) {
            return;
        }
        EvidenceAttachment attachment = documents.findAttachmentByCode(attachmentCode.trim())
                .orElseThrow(() -> new BusinessException("未授权附件"));
        if (!task.targetCode().equals(attachment.businessCode())) {
            throw new BusinessException("未授权附件");
        }
    }

    private boolean belongs(ActivityBag bag, OutsourcingTask task) {
        String cursor = bag.id();
        while (cursor != null && !cursor.isBlank()) {
            if (cursor.equals(task.bagId())) {
                return true;
            }
            ActivityBag current = bags.find(cursor).orElse(null);
            if (current == null || current.parentId() == null || current.parentId().isBlank()) {
                return false;
            }
            cursor = current.parentId();
        }
        return false;
    }
}
