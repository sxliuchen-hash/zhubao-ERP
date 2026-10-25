package com.zhubao.erp.business.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhubao.erp.business.domain.*;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ProcessingIntakePendingService {
    private final ProcessingIntakePendingRepository repository;
    private final BusinessWorkUnit unit;
    private final BusinessDataAccess access;
    private final ObjectMapper mapper = new ObjectMapper();
    public ProcessingIntakePendingService(ProcessingIntakePendingRepository repository, BusinessWorkUnit unit, BusinessDataAccess access) { this.repository = repository; this.unit = unit; this.access = access; }
    public List<ProcessingIntakePending> list(String account, OperatorContext operator) {
        return repository.list(account).stream().filter(row -> {
            try { var draft = mapper.readValue(row.draftJson(), ProcessingIntakeDraft.class); if (draft.customerId() != null && draft.customerId() > 0) access.assertCustomer(operator, draft.customerId(), ""); return true; }
            catch (Exception ex) { return false; }
        }).toList();
    }
    public ProcessingIntakePending save(String code, int expectedVersion, String requestId, ProcessingIntakeDraft draft, String account, OperatorContext operator) {
        if (draft == null || draft.lines().size() > 30) throw new BusinessException("待核资料不能为空，最多 30 条明细");
        if (draft.customerId() != null && draft.customerId() > 0) access.assertCustomer(operator, draft.customerId(), "");
        if (draft.processingRequirement() != null && draft.processingRequirement().length() > 500) throw new BusinessException("加工要求不能超过 500 字");
        String json;
        try { json = mapper.writeValueAsString(draft); } catch (Exception ex) { throw new BusinessException("待核资料格式无效"); }
        if (json.length() > 20000) throw new BusinessException("待核资料过长");
        String target = code == null || code.isBlank() ? "PD-" + UUID.nameUUIDFromBytes((account + "|" + requestId).getBytes(java.nio.charset.StandardCharsets.UTF_8)) : code.trim();
        String fingerprint;
        try { fingerprint = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException("待核资料摘要生成失败", ex); }
        String result = unit.execute(new CommandCall(requestId, account, "PROCESSING_INTAKE_PENDING", "INTAKE_PENDING", target, expectedVersion + "|" + fingerprint), session -> {
            var previous = repository.find(target, session).orElse(null);
            if (previous != null && !account.equals(previous.account())) throw new PermissionDeniedException("加工待核资料不属于当前账号");
            if (previous != null && !"PENDING".equals(previous.status())) throw new BusinessException("此待核记录已转正式母单：" + previous.motherOrderCode());
            if ((previous == null ? 0 : previous.version()) != expectedVersion) throw new BusinessException("待核资料已被修改，请重新读取");
            String now = LocalDateTime.now().toString();
            var saved = new ProcessingIntakePending(target, expectedVersion + 1, account, "PENDING", "", json, previous == null ? now : previous.createdAt(), now);
            repository.save(saved, session);
            // 请求结果只冻结元信息，资料按本次已校验的原请求重建，避免超出通用回放结果长度。
            try { return mapper.writeValueAsString(new ProcessingIntakePending(saved.code(), saved.version(), saved.account(), saved.status(), saved.motherOrderCode(), "", saved.createdAt(), saved.updatedAt())); } catch (Exception ex) { throw new IllegalStateException("待核保存结果编码失败", ex); }
        });
        try { var saved = mapper.readValue(result, ProcessingIntakePending.class); return new ProcessingIntakePending(saved.code(), saved.version(), saved.account(), saved.status(), saved.motherOrderCode(), json, saved.createdAt(), saved.updatedAt()); } catch (Exception ex) { throw new IllegalStateException("待核保存结果读取失败", ex); }
    }
}
