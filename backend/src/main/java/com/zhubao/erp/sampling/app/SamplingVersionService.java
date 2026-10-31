package com.zhubao.erp.sampling.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.sampling.domain.SamplingOrder;
import com.zhubao.erp.sampling.domain.SamplingOrderRepository;
import com.zhubao.erp.sampling.domain.SamplingVersion;
import com.zhubao.erp.sampling.domain.SamplingVersionDecision;
import com.zhubao.erp.sampling.domain.SamplingVersionDecisionKind;
import com.zhubao.erp.sampling.domain.SamplingVersionKind;
import com.zhubao.erp.sampling.domain.SamplingVersionRepository;
import com.zhubao.erp.sampling.domain.SamplingVersionView;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.app.RefactorPermissionContract;
import com.zhubao.erp.security.domain.CustomerScopeRules;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 保存打样图稿版本和客户确认。已确认版本的正文不改写。
 * 模型只记外部引用，不上传原件，也不把引用说成已经备份。
 */
@Service
public class SamplingVersionService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final SamplingOrderRepository orders;
    private final SamplingVersionRepository versions;
    private final BusinessWorkUnit unit;
    private final PermissionGuard permissions;

    public SamplingVersionService(SamplingOrderRepository orders, SamplingVersionRepository versions,
                                  BusinessWorkUnit unit, PermissionGuard permissions) {
        this.orders = orders;
        this.versions = versions;
        this.unit = unit;
        this.permissions = permissions;
    }

    public List<SamplingVersionView> list(OperatorContext actor, String samplingCode) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.SAMPLING_VIEW, actor);
        SamplingOrder order = requireVisible(actor, samplingCode);
        Map<String, SamplingVersionDecision> decisions = versions.listDecisions(order.samplingCode()).stream()
                .collect(Collectors.toMap(decision -> key(decision.samplingCode(), decision.kind(), decision.versionNo()), Function.identity()));
        return versions.list(order.samplingCode()).stream()
                .sorted(Comparator.comparing(SamplingVersion::kind).thenComparingInt(SamplingVersion::versionNo))
                .map(version -> view(version, decisions.get(key(version.samplingCode(), version.kind(), version.versionNo()))))
                .toList();
    }

    public SamplingVersionView add(String requestId, OperatorContext actor, String samplingCode, String kindText,
                                   String parameterText, String evidenceCode, String modelFileName, String modelFormat,
                                   String modelVersion, String modelLocationNote, String modelEvidenceCode, Integer versionNo) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.SAMPLING_MAINTAIN, actor);
        if (versionNo != null) {
            throw new BusinessException("版本号由系统分配");
        }
        SamplingOrder order = requireVisible(actor, samplingCode);
        SamplingVersionKind kind = SamplingVersionKind.parse(kindText);
        LocalDate day = LocalDate.now(ZONE);
        new SamplingVersion(order.samplingCode(), kind, 1, parameterText, evidenceCode, modelFileName, modelFormat,
                modelVersion, modelLocationNote, modelEvidenceCode, actor.operator(), day.toString());
        String payload = kind.name() + "|" + mark(parameterText) + "|" + mark(evidenceCode) + "|"
                + mark(modelFileName) + "|" + mark(modelFormat) + "|" + mark(modelVersion) + "|"
                + mark(modelLocationNote) + "|" + mark(modelEvidenceCode);
        String identity = unit.execute(new CommandCall(
                requestId, actor.operator(), "ADD_SAMPLING_VERSION", "SAMPLING_VERSION", order.samplingCode(), payload
        ), session -> insert(session, actor, order.samplingCode(), kind, parameterText, evidenceCode, modelFileName,
                modelFormat, modelVersion, modelLocationNote, modelEvidenceCode, day));
        return reload(identity);
    }

    public SamplingVersionView confirm(String requestId, OperatorContext actor, String samplingCode, String kindText,
                                       Integer versionNo, String note, String credentialCode) {
        return decide(requestId, actor, samplingCode, kindText, versionNo, note, credentialCode,
                SamplingVersionDecisionKind.CONFIRMED, "CONFIRM_SAMPLING_VERSION");
    }

    public SamplingVersionView reject(String requestId, OperatorContext actor, String samplingCode, String kindText,
                                      Integer versionNo, String reason, String credentialCode) {
        return decide(requestId, actor, samplingCode, kindText, versionNo, reason, credentialCode,
                SamplingVersionDecisionKind.REJECTED, "REJECT_SAMPLING_VERSION");
    }

    private SamplingVersionView decide(String requestId, OperatorContext actor, String samplingCode, String kindText,
                                       Integer versionNo, String reason, String credentialCode,
                                       SamplingVersionDecisionKind decision, String action) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.SAMPLING_MAINTAIN, actor);
        SamplingOrder order = requireVisible(actor, samplingCode);
        SamplingVersionKind kind = SamplingVersionKind.parse(kindText);
        if (versionNo == null || versionNo < 1) {
            throw new BusinessException("请选择版本");
        }
        LocalDate day = LocalDate.now(ZONE);
        new SamplingVersionDecision(order.samplingCode(), kind, versionNo, decision, reason, credentialCode,
                actor.operator(), day.toString());
        String payload = kind.name() + "|" + versionNo + "|" + decision.name() + "|" + mark(reason) + "|" + mark(credentialCode);
        String identity = unit.execute(new CommandCall(
                requestId, actor.operator(), action, "SAMPLING_VERSION", order.samplingCode(), payload
        ), session -> decide(session, actor, order.samplingCode(), kind, versionNo, decision, reason, credentialCode, day));
        return reload(identity);
    }

    private String insert(WorkSession session, OperatorContext actor, String samplingCode, SamplingVersionKind kind,
                          String parameterText, String evidenceCode, String modelFileName, String modelFormat,
                          String modelVersion, String modelLocationNote, String modelEvidenceCode, LocalDate day) {
        int next = versions.lockKind(samplingCode, kind, session).stream()
                .mapToInt(SamplingVersion::versionNo)
                .max()
                .orElse(0) + 1;
        SamplingVersion version = new SamplingVersion(
                samplingCode, kind, next, parameterText, evidenceCode, modelFileName, modelFormat,
                modelVersion, modelLocationNote, modelEvidenceCode, actor.operator(), day.toString());
        versions.insert(version, session);
        return version.identity();
    }

    private String decide(WorkSession session, OperatorContext actor, String samplingCode, SamplingVersionKind kind,
                          int versionNo, SamplingVersionDecisionKind decision, String reason, String credentialCode,
                          LocalDate day) {
        if (versions.lockOne(samplingCode, kind, versionNo, session).isEmpty()) {
            throw new BusinessException("版本不存在");
        }
        versions.lockDecision(samplingCode, kind, versionNo, session).ifPresent(existing -> {
            throw new BusinessException(existing.decision() == SamplingVersionDecisionKind.CONFIRMED
                    ? "该版本已经确认，不能覆盖"
                    : "该版本已经驳回，请另建版本");
        });
        SamplingVersionDecision saved = new SamplingVersionDecision(
                samplingCode, kind, versionNo, decision, reason, credentialCode, actor.operator(), day.toString());
        versions.insertDecision(saved, session);
        return samplingCode + "|" + kind.name() + "|" + versionNo;
    }

    private SamplingVersionView reload(String identity) {
        String[] parts = identity.split("\\|", -1);
        if (parts.length != 3) {
            throw new BusinessException("版本结果无效");
        }
        SamplingVersionKind kind = SamplingVersionKind.parse(parts[1]);
        int versionNo = Integer.parseInt(parts[2]);
        SamplingVersion version = versions.find(parts[0], kind, versionNo)
                .orElseThrow(() -> new BusinessException("版本不存在"));
        return view(version, versions.findDecision(parts[0], kind, versionNo).orElse(null));
    }

    private SamplingVersionView view(SamplingVersion version, SamplingVersionDecision decision) {
        boolean referenced = version.modelReferenced();
        return new SamplingVersionView(
                version.samplingCode(),
                version.kind().name(),
                version.kind().label(),
                version.versionNo(),
                version.parameterText(),
                version.evidenceCode(),
                version.modelFileName(),
                version.modelFormat(),
                version.modelVersion(),
                version.modelLocationNote(),
                version.modelEvidenceCode(),
                referenced,
                referenced ? "EXTERNAL_REFERENCE" : "NONE",
                referenced ? "NOT_BACKED_UP" : "NONE",
                decision == null ? "PENDING" : decision.decision().name(),
                decision == null ? "待确认" : decision.decision().label(),
                decision == null ? "" : decision.reason(),
                decision == null ? "" : decision.credentialCode(),
                decision == null ? "" : decision.decidedBy(),
                decision == null ? "" : decision.decidedOn()
        );
    }

    private SamplingOrder requireVisible(OperatorContext actor, String samplingCode) {
        assertScopeBound(actor);
        String code = samplingCode == null ? "" : samplingCode.trim();
        SamplingOrder order = orders.find(code).orElseThrow(() -> new BusinessException("打样单不存在"));
        if (!CustomerScopeRules.allowed(actor, order.customerId(), order.customerName())) {
            throw new PermissionDeniedException("客户不在当前账号范围内");
        }
        return order;
    }

    private static void assertScopeBound(OperatorContext actor) {
        if (actor.dataScope() == DataScope.CUSTOMER_RANGE
                && (actor.customerScope() == null || actor.customerScope().isEmpty())) {
            throw new PermissionDeniedException("客户范围账号没有绑定客户");
        }
    }

    private static String key(String samplingCode, SamplingVersionKind kind, int versionNo) {
        return samplingCode + "|" + kind.name() + "|" + versionNo;
    }

    private static String mark(String value) {
        return value == null ? "" : value.trim().replace('|', '/');
    }
}
