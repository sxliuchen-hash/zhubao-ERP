package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.ActivityBagRepository;
import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.business.domain.OutsourcingCounterpart;
import com.zhubao.erp.business.domain.OutsourcingParty;
import com.zhubao.erp.business.domain.OutsourcingSourceRepository;
import com.zhubao.erp.business.domain.OutsourcingTask;
import com.zhubao.erp.business.domain.ProcessCatalog;
import com.zhubao.erp.business.domain.ProcessStep;
import com.zhubao.erp.business.domain.RouteSnapshot;
import com.zhubao.erp.business.domain.RouteSnapshotRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.UUID;

/**
 * 登记外协方，并按原目标、原节点和所发袋建立任务。
 * 外协方不是客户或员工，也没有登录账号。任务留下当时名称，权属仍是原来的客户或公司。
 * 本命令不发外、不改库存、不写客户账或工资。发外改袋时须先锁执行目标和袋，再锁外协方。
 */
@Service
public class OutsourcingSourceService {

    private final OutsourcingSourceRepository sources;
    private final ActivityBagRepository bags;
    private final RouteSnapshotRepository routes;
    private final BusinessWorkUnit workUnit;

    public OutsourcingSourceService(OutsourcingSourceRepository sources, ActivityBagRepository bags,
                                    RouteSnapshotRepository routes, BusinessWorkUnit workUnit) {
        this.sources = sources;
        this.bags = bags;
        this.routes = routes;
        this.workUnit = workUnit;
    }

    public OutsourcingParty register(String requestId, OutsourcingCounterpart counterpart, String partyId,
                                     String name, String actorId) {
        if (counterpart == null) {
            throw new BusinessException("外协方不能登记成客户或员工");
        }
        counterpart.assertParty();
        OutsourcingParty party = OutsourcingParty.create(counterpart, partyId, name);
        String actor = handler(actorId, party.partyId());
        String result = workUnit.execute(new CommandCall(
                requestId, actor, "REGISTER_OUTSOURCE_PARTY", "OUTSOURCE_PARTY", party.partyId(),
                counterpart.name() + "|" + party.partyId() + "|" + party.name()
        ), session -> insertParty(party, session));
        return sources.findParty(result).orElseThrow(() -> new IllegalStateException("外协方保存后无法读取"));
    }

    public OutsourcingParty rename(String requestId, String partyId, int expectedVersion, String name, String actorId) {
        String id = OutsourcingParty.requireId(partyId);
        String actor = handler(actorId, id);
        String nextName = OutsourcingParty.requireName(name);
        String result = workUnit.execute(new CommandCall(
                requestId, actor, "RENAME_OUTSOURCE_PARTY", "OUTSOURCE_PARTY", id,
                id + "|" + expectedVersion + "|" + nextName
        ), session -> renameParty(id, expectedVersion, nextName, session));
        return sources.findParty(result).orElseThrow(() -> new IllegalStateException("外协方保存后无法读取"));
    }

    public OutsourcingTask openTask(String requestId, String partyId, String targetCode, String nodeId, String bagId,
                                    OwnerType ownerType, String ownerRef, String actorId) {
        String id = OutsourcingParty.requireId(partyId);
        String actor = handler(actorId, id);
        BusinessTargetReference target = BusinessTargetReference.fromCode(targetCode);
        ProcessStep step = requireStep(target.kind(), nodeId);
        String ref = ownerRef == null ? "" : ownerRef.trim();
        OwnerType owner = StockMeasurement.ownerAfterOutsourcing(ownerType);
        if (ref.equals(id)) {
            throw new BusinessException("外协不改变物料权属");
        }
        String result = workUnit.execute(new CommandCall(
                requestId, actor, "OPEN_OUTSOURCE_TASK", "OUTSOURCE_TASK", target.code(),
                id + "|" + target.code() + "|" + step.id() + "|" + (bagId == null ? "" : bagId.trim())
                        + "|" + owner.name() + "|" + ref
        ), session -> createTask(requestId, id, target, step, bagId, owner, ref, session));
        return sources.findTask(result).orElseThrow(() -> new IllegalStateException("外协任务保存后无法读取"));
    }

    private String insertParty(OutsourcingParty party, WorkSession session) {
        if (sources.lockParty(party.partyId(), session).isPresent()) {
            throw new BusinessException("外协方已存在");
        }
        sources.saveParty(party, 0, session);
        return party.partyId();
    }

    private String renameParty(String partyId, int expectedVersion, String name, WorkSession session) {
        OutsourcingParty current = sources.lockParty(partyId, session)
                .orElseThrow(() -> new BusinessException("外协方不存在"));
        if (current.version() != expectedVersion) {
            throw new BusinessException("外协方已被其他人修改，请刷新后重试");
        }
        OutsourcingParty next = current.rename(name);
        sources.saveParty(next, expectedVersion, session);
        return next.partyId();
    }

    private String createTask(String requestId, String partyId, BusinessTargetReference target, ProcessStep step,
                              String bagId, OwnerType ownerType, String ownerRef, WorkSession session) {
        ActivityBag bag = bags.find(bagId == null ? "" : bagId.trim())
                .orElseThrow(() -> new BusinessException("袋不存在"));
        if (!bag.activeLeaf()) {
            throw new BusinessException("已关闭的袋不能作为外协任务来源");
        }
        if (!bag.targetCode().equals(target.code())) {
            throw new BusinessException("外协任务的原目标与所发袋不一致");
        }
        RouteSnapshot route = routes.list(target.kind(), target.code()).stream()
                .filter(RouteSnapshot::confirmed)
                .max(Comparator.comparingInt(RouteSnapshot::version))
                .orElseThrow(() -> new BusinessException("原目标还没有已确认路线"));
        if (!route.stepIds().contains(step.id())) {
            throw new BusinessException("外协节点不在已确认路线上");
        }
        OutsourcingParty party = sources.lockParty(partyId, session)
                .orElseThrow(() -> new BusinessException("外协方不存在"));
        String taskId = "OT" + UUID.randomUUID().toString().replace("-", "");
        OutsourcingTask task = new OutsourcingTask(
                taskId, requestId, party.partyId(), party.name(), target.kind(), target.code(),
                step.id(), bag.id(), ownerType, ownerRef, 1);
        sources.saveTask(task, session);
        return task.taskId();
    }

    private static ProcessStep requireStep(BusinessKind kind, String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            throw new BusinessException("外协任务缺少原节点");
        }
        ProcessStep step = ProcessCatalog.require(nodeId.trim());
        if (step.kind() != kind) {
            throw new BusinessException("工序不属于该业务");
        }
        return step;
    }

    private static String handler(String actorId, String partyId) {
        String actor = actorId == null ? "" : actorId.trim();
        if (actor.isEmpty()) {
            throw new BusinessException("操作人不能为空");
        }
        if (actor.equals(partyId)) {
            throw new BusinessException("经办人不是外协方");
        }
        return actor;
    }
}
