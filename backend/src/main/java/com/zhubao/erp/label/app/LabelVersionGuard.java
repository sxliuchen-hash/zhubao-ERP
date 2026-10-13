package com.zhubao.erp.label.app;

import com.zhubao.erp.business.domain.OrderChangeRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.label.domain.LabelTrace;
import com.zhubao.erp.label.domain.LabelTraceRepository;
import com.zhubao.erp.scan.app.ScanCodeParser;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 正式报工、质检和出库核验标签版本。旧版必须例外放行，三段旧码只表示版本未知。
 */
@Component
public class LabelVersionGuard {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final OrderChangeRepository changes;
    private final LabelTraceRepository traces;
    private final ScanCodeParser parser = new ScanCodeParser();

    public LabelVersionGuard(OrderChangeRepository changes, LabelTraceRepository traces) {
        this.changes = changes;
        this.traces = traces;
    }

    public void check(String rawCode, String motherOrderCode, String businessCode, String reason, AuthUserAccount account) {
        if (rawCode == null || !rawCode.startsWith("JG|")) {
            return;
        }
        String version = parser.labelVersion(rawCode);
        int current = changes == null ? 0 : changes.latestAppliedVersion(businessCode);
        if (version.isBlank()) {
            requireException(reason, account, "这张标签没有版本，版本未知，关键作业必须填写放行原因");
            record(motherOrderCode, businessCode, "VERSION_UNKNOWN", "", rawCode, reason, account);
            return;
        }
        if (!version.equals(String.valueOf(current))) {
            requireException(reason, account, "这张标签版本已过期，当前版本是 " + current);
            record(motherOrderCode, businessCode, "EXPIRED_EXCEPTION", version, rawCode, reason, account);
        }
    }

    public void recordEvent(String motherOrderCode, String businessCode, String eventType, String version, String instance, String jobId, String reason, String operator) {
        if (traces == null) {
            return;
        }
        traces.save(new LabelTrace(
                "LT-" + UUID.randomUUID().toString().substring(0, 8),
                motherOrderCode == null ? "" : motherOrderCode,
                businessCode == null ? "" : businessCode,
                eventType,
                version == null ? "" : version,
                instance == null ? "" : instance,
                jobId == null ? "" : jobId,
                reason == null ? "" : reason,
                operator == null ? "" : operator,
                TIME.format(LocalDateTime.now())
        ));
    }

    private void record(String mother, String business, String event, String version, String raw, String reason, AuthUserAccount account) {
        recordEvent(mother, business, event, version, "", "", reason == null ? raw : reason, account == null ? "" : account.operator());
    }

    private void requireException(String reason, AuthUserAccount account, String message) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(message);
        }
        if (account == null || (account.role() != RoleCode.ADMIN && account.role() != RoleCode.GENERAL_MANAGER && account.role() != RoleCode.WORKSHOP_LEADER)) {
            throw new PermissionDeniedException("旧标签例外放行需要组长或管理员权限");
        }
    }
}