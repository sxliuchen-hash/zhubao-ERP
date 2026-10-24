package com.zhubao.erp.label.app;

import com.zhubao.erp.business.app.MasterDataService;
import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.label.domain.*;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

/** 采用当前档案要求前明确核对，换贴独立编号，原收货和旧打印任务保留。 */
@Service
public class LabelStorageEditionService {
    private final LabelStorageEditionRepository editions;
    private final BusinessDocumentRepository documents;
    private final MasterDataService materials;
    private final LabelService labels;
    private final BusinessWorkUnit unit;
    private com.zhubao.erp.inventory.domain.StockDocumentRepository stockDocuments;
    public LabelStorageEditionService(LabelStorageEditionRepository editions, BusinessDocumentRepository documents, MasterDataService materials, LabelService labels, BusinessWorkUnit unit) {
        this.editions = editions; this.documents = documents; this.materials = materials; this.labels = labels; this.unit = unit;
    }
    @org.springframework.beans.factory.annotation.Autowired
    public LabelStorageEditionService(LabelStorageEditionRepository editions, BusinessDocumentRepository documents, MasterDataService materials, LabelService labels, BusinessWorkUnit unit, com.zhubao.erp.inventory.domain.StockDocumentRepository stockDocuments) {
        this(editions, documents, materials, labels, unit); this.stockDocuments = stockDocuments;
    }
    public java.util.List<LabelStorageEdition> history(String code) { return editions.history(code); }
    public int adopt(String code, String requestId, int expectedEdition, String reason, String operator, String templateCode) {
        if (reason == null || reason.isBlank() || reason.length() > 200) throw new BusinessException("采用新保存要求换贴必须填写 1—200 字原因");
        var child = documents.findChildWorkOrderByCode(code).orElse(null);
        boolean batch = labels.isStockBatch(code);
        var batchDocument = batch && stockDocuments != null ? stockDocuments.list().stream().filter(document -> document.lines().stream().anyMatch(line -> code.equals(line.batchCode()) && line.counted())).findFirst().orElseThrow() : null;
        String mother = child == null ? code : child.motherOrderCode();
        String result = unit.execute(new CommandCall(requestId, operator, "LABEL_STORAGE_EDITION", batch ? "STOCK_BATCH" : "MOTHER_ORDER", mother,
                code + "|" + expectedEdition + "|" + reason + "|" + templateCode), session -> {
            if (batchDocument != null) stockDocuments.lock(batchDocument.documentCode(), session).orElseThrow();
            else documents.lockMotherOrder(mother, session);
            int previous = editions.latest(code, session).map(LabelStorageEdition::edition).orElse(0);
            if (previous != expectedEdition) throw new BusinessException("保存要求标签已被更新，请重新预览核对");
            LabelPayload original = batch ? labels.generateStockBatchLabel(code, templateCode) : child == null ? labels.generateMotherLabel(code, templateCode) : labels.generateWorkOrderLabel(code, templateCode);
            var sources = original.storageSources().stream().map(source -> StorageLabelSource.of(source.sourceCode(), source.sourceLabel(), source.materialCode(),
                    com.zhubao.erp.business.domain.StorageRequirements.of(materials.materialForIntake(source.materialCode())))).toList();
            LabelService.validateStorageArea(original.withStorage(sources, previous + 1));
            editions.save(new LabelStorageEdition(code, previous + 1, sources, requestId, reason.trim(), operator, LocalDateTime.now().toString()), session);
            return String.valueOf(previous + 1);
        });
        return Integer.parseInt(result);
    }
}
