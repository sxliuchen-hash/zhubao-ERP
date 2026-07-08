package com.zhubao.erp.register.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.register.domain.ConfirmRegisterImportCommand;
import com.zhubao.erp.register.domain.CorrectRegisterImportRowCommand;
import com.zhubao.erp.register.domain.RegisterImportBatch;
import com.zhubao.erp.register.domain.RegisterImportBatchRepository;
import com.zhubao.erp.register.domain.RegisterImportBatchRow;
import com.zhubao.erp.register.domain.RegisterImportPreview;
import com.zhubao.erp.register.domain.RegisterImportTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RegisterImportConfirmationService {

    private static final DateTimeFormatter CONFIRM_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final Set<String> LOCKED_AUDIT_FIELDS = Set.of(
            "importBatchNo",
            "sourceRegisterLineNo",
            "sourceRegisterSequence"
    );

    private final RegisterImportTemplateService templateService;
    private final RegisterImportBatchRepository repository;

    public RegisterImportConfirmationService(
            RegisterImportTemplateService templateService,
            RegisterImportBatchRepository repository
    ) {
        this.templateService = templateService;
        this.repository = repository;
    }

    public RegisterImportBatch confirm(ConfirmRegisterImportCommand command) {
        RegisterImportPreview preview = command == null ? null : command.preview();
        validatePreview(preview);
        RegisterImportTemplate template = findTemplate(preview.templateCode());
        repository.findByBatchNo(preview.importBatchNo()).ifPresent(existing -> {
            throw new BusinessException("导入批次已确认：" + existing.importBatchNo());
        });

        String operator = normalizeOperator(command.operator());
        String confirmedAt = CONFIRM_TIME_FORMATTER.format(LocalDateTime.now());
        List<RegisterImportBatchRow> rows = preview.rows().stream()
                .map(row -> new RegisterImportBatchRow(
                        preview.importBatchNo(),
                        row.sourceLineNo(),
                        row.sourceRegisterSequence(),
                        row.status(),
                        row.normalizedFields(),
                        row.messages()
                ))
                .toList();

        RegisterImportBatch batch = new RegisterImportBatch(
                preview.importBatchNo(),
                preview.templateCode(),
                preview.templateName(),
                preview.sourceFileName(),
                template.targetCommand(),
                preview.totalRows(),
                preview.readyRows(),
                preview.warningRows(),
                preview.blockedRows(),
                preview.executable(),
                "CONFIRMED",
                operator,
                confirmedAt,
                normalizeRemark(command.confirmRemark()),
                "已落结构化导入批次，待业务单据入账映射",
                rows
        );
        return repository.save(batch);
    }

    public List<RegisterImportBatch> recentBatches(int limit) {
        return repository.findRecent(Math.max(1, Math.min(limit, 50)));
    }

    public RegisterImportBatch batch(String importBatchNo) {
        return repository.findByBatchNo(importBatchNo)
                .orElseThrow(() -> new BusinessException("未找到导入批次：" + importBatchNo));
    }

    public RegisterImportBatch correctRowFields(
            String importBatchNo,
            int sourceLineNo,
            CorrectRegisterImportRowCommand command
    ) {
        RegisterImportBatch batch = batch(importBatchNo);
        if ("POSTED".equals(batch.status())) {
            throw new BusinessException("导入批次已入账，不能再补录字段：" + importBatchNo);
        }
        RegisterImportBatchRow row = batch.rows().stream()
                .filter(item -> item.sourceLineNo() == sourceLineNo)
                .findFirst()
                .orElseThrow(() -> new BusinessException("未找到导入批次行：" + importBatchNo + " / " + sourceLineNo));
        if ("POSTED".equals(row.status())) {
            throw new BusinessException("导入批次行已入账，不能再补录字段：" + importBatchNo + " / " + sourceLineNo);
        }
        Map<String, String> corrections = normalizeCorrectionFields(command);
        Map<String, String> merged = new LinkedHashMap<>();
        if (row.normalizedFields() != null) {
            merged.putAll(row.normalizedFields());
        }
        merged.putAll(corrections);
        String operator = normalizeOperator(command == null ? "" : command.operator());
        String remark = command == null || command.correctionRemark() == null || command.correctionRemark().isBlank()
                ? "复核补录"
                : command.correctionRemark().trim();
        RegisterImportBatch updated = repository.updateRowFields(
                importBatchNo,
                sourceLineNo,
                Map.copyOf(merged),
                "已完成登记表复核补录：" + operator + " / " + remark + "；重新生成入账映射计划后再决定是否显式入账"
        );
        if (updated == null) {
            throw new BusinessException("导入批次行补录失败：" + importBatchNo + " / " + sourceLineNo);
        }
        return updated;
    }

    public RegisterImportBatch markRowsPosted(
            String importBatchNo,
            List<Integer> sourceLineNos,
            boolean batchPosted,
            String nextAction
    ) {
        RegisterImportBatch updated = repository.markRowsPosted(
                importBatchNo,
                sourceLineNos,
                batchPosted,
                nextAction
        );
        if (updated == null) {
            throw new BusinessException("未找到导入批次：" + importBatchNo);
        }
        return updated;
    }

    private void validatePreview(RegisterImportPreview preview) {
        if (preview == null) {
            throw new BusinessException("确认导入必须携带预检结果");
        }
        if (preview.importBatchNo() == null || preview.importBatchNo().isBlank()) {
            throw new BusinessException("确认导入缺少 importBatchNo");
        }
        if (preview.rows() == null || preview.rows().isEmpty()) {
            throw new BusinessException("确认导入没有可保存的预检行");
        }
        if (!preview.executable() || preview.blockedRows() > 0) {
            throw new BusinessException("存在阻断行，不能确认导入批次");
        }
    }

    private Map<String, String> normalizeCorrectionFields(CorrectRegisterImportRowCommand command) {
        if (command == null || command.fields() == null || command.fields().isEmpty()) {
            throw new BusinessException("补录字段不能为空");
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : command.fields().entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().trim();
            if (key.isBlank()) {
                throw new BusinessException("补录字段名不能为空");
            }
            if (LOCKED_AUDIT_FIELDS.contains(key)) {
                throw new BusinessException("来源审计字段不能通过补录接口修改：" + key);
            }
            normalized.put(key, entry.getValue() == null ? "" : entry.getValue().trim());
        }
        return normalized;
    }

    private RegisterImportTemplate findTemplate(String templateCode) {
        return templateService.findTemplates().stream()
                .filter(template -> template.code().equals(templateCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException("未知登记表模板：" + templateCode));
    }

    private String normalizeOperator(String operator) {
        if (operator == null || operator.isBlank()) {
            return "登记表复核员";
        }
        return operator.trim();
    }

    private String normalizeRemark(String remark) {
        if (remark == null || remark.isBlank()) {
            return "确认导入仅保存结构化批次，暂不直接写业务台账";
        }
        return remark.trim();
    }
}
