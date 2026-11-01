package com.zhubao.erp.sampling.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.sampling.domain.SamplingOrder;
import com.zhubao.erp.sampling.domain.SamplingOrderRepository;
import com.zhubao.erp.sampling.domain.SamplingProductionLink;
import com.zhubao.erp.sampling.domain.SamplingProductionLinkRepository;
import com.zhubao.erp.sampling.domain.SamplingProductionLinkView;
import com.zhubao.erp.sampling.domain.SamplingVersion;
import com.zhubao.erp.sampling.domain.SamplingVersionDecisionKind;
import com.zhubao.erp.sampling.domain.SamplingVersionKind;
import com.zhubao.erp.sampling.domain.SamplingVersionRepository;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.app.RefactorPermissionContract;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.domain.CustomerScopeRules;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 把已确认样品版本抄进普通加工的引用快照。
 * 不改母单要求原文，不建单，不领料，不收费。
 */
@Service
public class SamplingProductionLinkService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final BusinessDocumentRepository documents;
    private final SamplingOrderRepository orders;
    private final SamplingVersionRepository versions;
    private final SamplingProductionLinkRepository links;
    private final BusinessWorkUnit unit;
    private final PermissionGuard permissions;

    public SamplingProductionLinkService(BusinessDocumentRepository documents, SamplingOrderRepository orders,
                                         SamplingVersionRepository versions, SamplingProductionLinkRepository links,
                                         BusinessWorkUnit unit, PermissionGuard permissions) {
        this.documents = documents;
        this.orders = orders;
        this.versions = versions;
        this.links = links;
        this.unit = unit;
        this.permissions = permissions;
    }

    public SamplingProductionLinkView find(OperatorContext actor, String motherCode) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.SAMPLING_VIEW, actor);
        String code = mother(motherCode);
        MotherOrderRecord mother = requireMother(actor, code);
        return links.find(code).map(link -> {
            assertSameCustomer(mother, requireOrder(actor, link.samplingCode()));
            return view(link);
        }).orElseGet(() -> SamplingProductionLinkView.absent(code));
    }

    public SamplingProductionLinkView link(String requestId, OperatorContext actor, String motherCode,
                                           String samplingCode, String versionKind, Integer versionNo) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.SAMPLING_MAINTAIN, actor);
        String mother = mother(motherCode);
        SamplingOrder order = requireOrder(actor, samplingCode);
        assertSameCustomer(requireMother(actor, mother), order);
        SamplingVersionKind kind = SamplingVersionKind.parse(versionKind);
        if (versionNo == null || versionNo < 1) {
            throw new BusinessException("请选择已确认版本");
        }
        String payload = mother + "|" + order.samplingCode() + "|" + kind.name() + "|" + versionNo;
        unit.execute(new CommandCall(
                requestId, actor.operator(), "LINK_SAMPLING_PRODUCTION", "SAMPLING_LINK", mother, payload
        ), session -> write(session, actor, mother, order, kind, versionNo));
        return view(links.find(mother).orElseThrow(() -> new BusinessException("样品引用不存在")));
    }

    public SamplingProductionLinkView unlink(String requestId, OperatorContext actor, String motherCode) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.SAMPLING_MAINTAIN, actor);
        String mother = mother(motherCode);
        MotherOrderRecord source = requireMother(actor, mother);
        links.find(mother).ifPresent(link -> assertSameCustomer(source, requireOrder(actor, link.samplingCode())));
        unit.execute(new CommandCall(
                requestId, actor.operator(), "UNLINK_SAMPLING_PRODUCTION", "SAMPLING_LINK", mother, mother
        ), session -> {
            SamplingProductionLink link = links.lock(mother, session).orElseThrow(() -> new BusinessException("这张母单没有样品引用"));
            assertSameCustomer(requireMother(actor, mother), requireOrder(actor, link.samplingCode()));
            links.delete(mother, session);
            return mother;
        });
        return SamplingProductionLinkView.absent(mother);
    }

    private String write(WorkSession session, OperatorContext actor, String motherCode, SamplingOrder order, SamplingVersionKind kind, int versionNo) {
        MotherOrderRecord mother = requireMother(actor, motherCode);
        assertSameCustomer(mother, requireOrder(actor, order.samplingCode()));
        if (links.lock(mother.code(), session).isPresent()) {
            throw new BusinessException("这张母单已经引用样品版本");
        }
        SamplingVersion version = versions.find(order.samplingCode(), kind, versionNo)
                .orElseThrow(() -> new BusinessException("版本不存在"));
        boolean confirmed = versions.findDecision(order.samplingCode(), kind, versionNo)
                .filter(decision -> decision.decision() == SamplingVersionDecisionKind.CONFIRMED)
                .isPresent();
        if (!confirmed) {
            throw new BusinessException("版本与已确认版本不符");
        }
        String snapshot = snapshot(version);
        links.insert(new SamplingProductionLink(
                mother.code(), order.samplingCode(), kind, versionNo, snapshot, LocalDate.now(ZONE).toString()
        ), session);
        return mother.code();
    }

    private SamplingOrder requireOrder(OperatorContext actor, String samplingCode) {
        assertBound(actor);
        String code = samplingCode == null ? "" : samplingCode.trim();
        SamplingOrder order = orders.find(code).orElseThrow(() -> new BusinessException("打样单不存在"));
        if (!CustomerScopeRules.allowed(actor, order.customerId(), order.customerName())) {
            throw new PermissionDeniedException("客户不在当前账号范围内");
        }
        return order;
    }

    private MotherOrderRecord requireMother(OperatorContext actor, String code) {
        assertBound(actor);
        MotherOrderRecord mother = documents.findMotherOrderByCode(code).orElseThrow(() -> new BusinessException("母单不存在"));
        if (!CustomerScopeRules.allowed(actor, mother.customerId(), mother.customerName())) {
            throw new PermissionDeniedException("客户不在当前账号范围内");
        }
        return mother;
    }

    private static void assertBound(OperatorContext actor) {
        if (actor.dataScope() == DataScope.CUSTOMER_RANGE && actor.customerScope().isEmpty()) {
            throw new PermissionDeniedException("客户范围账号没有绑定客户");
        }
    }

    private static void assertSameCustomer(MotherOrderRecord mother, SamplingOrder order) {
        if (!java.util.Objects.equals(mother.customerId(), order.customerId())) {
            throw new BusinessException("母单与样品必须属于同一客户");
        }
    }

    private static String mother(String motherCode) {
        try {
            BusinessTargetReference reference = BusinessTargetReference.fromCode(motherCode);
            if (!reference.processingMother()) {
                throw new BusinessException("只能引用到普通加工母单");
            }
            return reference.code();
        } catch (BusinessException ex) {
            if ("只能引用到普通加工母单".equals(ex.getMessage())) {
                throw ex;
            }
            throw new BusinessException("只能引用到普通加工母单");
        }
    }

    private static String snapshot(SamplingVersion version) {
        String text = version.parameterText();
        if (version.modelReferenced()) {
            text = (text.isEmpty() ? "" : text + "；") + version.modelFileName() + " " + version.modelFormat()
                    + " " + version.modelVersion();
        }
        return text;
    }

    private SamplingProductionLinkView view(SamplingProductionLink link) {
        return new SamplingProductionLinkView(
                true, link.motherCode(), link.samplingCode(), link.versionKind().name(), link.versionNo(),
                link.snapshotText(), link.linkedOn()
        );
    }
}
