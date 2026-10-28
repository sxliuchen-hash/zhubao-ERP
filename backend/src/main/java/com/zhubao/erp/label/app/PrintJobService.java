package com.zhubao.erp.label.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhubao.erp.business.app.BusinessDataAccess;
import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.OrderChangeRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.label.domain.LabelEdition;
import com.zhubao.erp.label.domain.PrintJob;
import com.zhubao.erp.label.domain.PrintJobRepository;
import com.zhubao.erp.label.domain.PrintJobStatus;
import com.zhubao.erp.scan.app.ScanCodeParser;
import com.zhubao.erp.label.domain.LabelPayload;
import com.zhubao.erp.label.domain.LabelField;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 打印任务只记录到系统队列为止。不确定任务保留原状，等待人工核对后再决定补打。
 */
@Service
public class PrintJobService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final PrintJobRepository jobs;
    private final OrderChangeRepository changes;
    private LabelVersionGuard labelGuard;
    private BusinessDocumentRepository documents;
    private BusinessDataAccess dataAccess;
    private LabelService labelService;
    private com.zhubao.erp.inventory.app.MaterialStockService materialStocks;
    @Autowired(required = false)
    public void setMaterialStocks(com.zhubao.erp.inventory.app.MaterialStockService materialStocks) { this.materialStocks = materialStocks; }
    private final ObjectMapper snapshotMapper = new ObjectMapper();

    public PrintJobService(PrintJobRepository jobs) {
        this(jobs, null);
    }

    @Autowired
    public PrintJobService(
            PrintJobRepository jobs,
            OrderChangeRepository changes
    ) {
        this.jobs = jobs;
        this.changes = changes;
    }

    @Autowired(required = false)
    public void setLabelVersionGuard(LabelVersionGuard labelGuard) {
        this.labelGuard = labelGuard;
    }

    @Autowired(required = false)
    public void setDocumentAccess(BusinessDocumentRepository documents, BusinessDataAccess dataAccess) {
        this.documents = documents;
        this.dataAccess = dataAccess;
    }

    @Autowired(required = false)
    public void setLabelService(LabelService labelService) {
        this.labelService = labelService;
    }

    public String prepareBatchSnapshot(String businessCode, String templateCode, int copies, AuthUserAccount account) {
        if (businessCode == null || businessCode.isBlank() || templateCode == null || templateCode.isBlank()
                || copies < 1 || copies > 20) throw new BusinessException("打印单号、模板或份数无效");
        assertPrintable(businessCode, account);
        int version = changes == null ? 0 : changes.latestAppliedVersion(businessCode.trim());
        return snapshotForJob(businessCode.trim(), templateCode.trim(), copies, version);
    }

    /**
     * 把客户暗记写入页脚，并去掉快照里的客户中文名。空白暗记保持原快照字符串不变。
     */
    public String withCustomerMark(String snapshot, String mark) {
        if (mark == null || mark.isBlank()) return snapshot;
        String normalized = mark.trim();
        if (!normalized.matches("[A-Z]{1,8}")) {
            throw new BusinessException("客户暗记只能是 1 到 8 位大写字母");
        }
        try {
            JsonNode tree = snapshotMapper.readTree(snapshot);
            if (!(tree instanceof ObjectNode object)) return snapshot;
            String left = object.path("footerLeft").asText("");
            String right = object.path("footerRight").asText("");
            if (!normalized.equals(left) && !normalized.equals(right)) {
                if (right.isBlank()) object.put("footerRight", normalized);
                else if (left.isBlank()) object.put("footerLeft", normalized);
                else object.put("footerRight", right + " " + normalized);
            }
            JsonNode fieldsNode = object.get("fields");
            if (fieldsNode instanceof ArrayNode fields) {
                for (int index = fields.size() - 1; index >= 0; index--) {
                    if ("客户".equals(fields.get(index).path("name").asText(""))) fields.remove(index);
                }
                ArrayNode lines = object.putArray("lines");
                for (JsonNode field : fields) {
                    String name = field.path("name").asText("");
                    String value = field.path("value").asText("");
                    if (!name.isBlank() && !value.isBlank()) lines.add(name + ":" + value);
                }
            }
            return snapshotMapper.writeValueAsString(object);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("标签快照无效");
        }
    }

    public void assertBatchReadable(String businessCode, AuthUserAccount account) {
        assertPrintable(businessCode, account);
    }

    public PrintJob get(String id, AuthUserAccount account) {
        PrintJob job = get(id);
        assertPrintable(job.businessCode(), account);
        return job;
    }

    public List<PrintJob> list(AuthUserAccount account) {
        return list().stream().filter(job -> readable(job.businessCode(), account)).toList();
    }

    public PrintJob get(String id) {
        expireLeases(TIME.format(LocalDateTime.now()));
        return jobs.findById(id).orElseThrow(() -> new BusinessException("打印任务不存在：" + id));
    }

    /** 从冻结快照的二维码读出版本和实例。三段旧码的版本和实例都为空。 */
    public LabelEdition readEdition(String snapshot) {
        try {
            JsonNode root = snapshotMapper.readTree(snapshot);
            String qr = root.path("qr").asText("");
            ScanCodeParser parser = new ScanCodeParser();
            String code = root.path("businessCode").asText("");
            if (code.isBlank()) {
                String[] parts = qr.split("\\|", -1);
                code = parts.length > 2 ? parts[2] : "";
            }
            String version = parser.labelVersion(qr);
            int storage = root.path("storageEdition").asInt(0);
            if ("STOCK_BATCH".equals(root.path("type").asText())) return new LabelEdition(code, "S" + storage, code + "-S" + storage);
            if (storage > 0) version += "/S" + storage;
            return new LabelEdition(code, version, parser.labelInstance(qr));
        } catch (JsonProcessingException ex) {
            throw new BusinessException("标签快照无效");
        }
    }

    public PrintJob verifyPaper(String id, String requestId, String note, String operator) {
        PrintJob job = get(id);
        if (note == null || note.isBlank()) {
            throw new BusinessException("出纸核实必须填写结果");
        }
        if (labelGuard != null) {
            labelGuard.recordEvent(motherCode(job.businessCode()), job.businessCode(), "PAPER_CHECKED", "", "", job.id(), note.trim(), operator);
        }
        return job;
    }

    public PrintJob relabel(String id, String requestId, String note, String operator) {
        PrintJob job = get(id);
        if (note == null || note.isBlank()) {
            throw new BusinessException("实物换贴必须填写结果");
        }
        if (labelGuard != null) {
            labelGuard.recordEvent(motherCode(job.businessCode()), job.businessCode(), "RELABELED", "", "", job.id(), note.trim(), operator);
        }
        return job;
    }

    public int expireLeases(String now) {
        int changed = 0;
        for (PrintJob job : jobs.list()) {
            if (job.status() == PrintJobStatus.LEASED && !job.leaseUntil().isBlank() && job.leaseUntil().compareTo(now) < 0) {
                replace(job, PrintJobStatus.UNCERTAIN, job.leaseUntil(), "租约到期，未能确认是否已送入队列");
                changed++;
            }
        }
        return changed;
    }

    public List<PrintJob> list() {
        expireLeases(TIME.format(LocalDateTime.now()));
        return jobs.list();
    }

    private String snapshotForJob(String businessCode, String templateCode, int copies, int version) {
        if (documents == null || labelService == null) {
            return snapshot(businessCode, templateCode, copies, version);
        }
        String selectedTemplate = "FLOW".equals(templateCode) ? null : templateCode;
        LabelPayload payload;
        if (documents.findMotherOrderByCode(businessCode).isPresent()) {
            payload = labelService.generateMotherLabel(businessCode, selectedTemplate);
        } else if (documents.findChildWorkOrderByCode(businessCode).isPresent()) {
            payload = labelService.generateWorkOrderLabel(businessCode, selectedTemplate);
        } else if (materialStocks != null) {
            payload = labelService.generateStockBatchLabel(businessCode, selectedTemplate);
        } else {
            return snapshot(businessCode, templateCode, copies, version);
        }
        String[] qrParts = payload.qrContent().split("\\|", -1);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("kind", "LABEL");
        snapshot.put("businessCode", businessCode);
        snapshot.put("templateCode", payload.templateCode());
        snapshot.put("copies", copies);
        snapshot.put("widthMm", payload.printerSetting().widthMm());
        snapshot.put("heightMm", payload.printerSetting().heightMm());
        snapshot.put("dpi", payload.printerSetting().dpi());
        snapshot.put("version", "V" + version);
        snapshot.put("instance", qrParts.length > 4 ? qrParts[4] : "");
        snapshot.put("qr", payload.qrContent());
        snapshot.put("brandTitle", payload.brandTitle());
        snapshot.put("type", payload.type().name());
        snapshot.put("typeLabel", payload.typeLabel());
        snapshot.put("primaryCode", payload.primaryCode());
        snapshot.put("fields", payload.fields());
        snapshot.put("storageSources", payload.storageSources());
        snapshot.put("storageEdition", payload.storageEdition());
        snapshot.put("storageHeightMm", payload.storageHeightMm());
        snapshot.put("footerLeft", payload.footerLeft());
        snapshot.put("footerRight", payload.footerRight());
        snapshot.put("layout", payload.layout());
        snapshot.put("lines", payload.fields().stream()
                .map(this::lineText).toList());
        try {
            return snapshotMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("无法生成标签打印快照", ex);
        }
    }

    private String lineText(LabelField field) {
        return field.name() + ":" + field.value();
    }

    /** 结构化标签快照。客户端不能改写其中的单号、版本和二维码。 */
    static String snapshot(String businessCode, String templateCode, int copies, int version) {
        String instance = "LI" + Integer.toHexString(Math.abs((businessCode + "|" + templateCode + "|" + version).hashCode()));
        String scanType = businessCode.startsWith("MD") ? "mother" : "work_order";
        String qr = "JG|" + scanType + "|" + businessCode + "|" + version + "|" + instance;
        return "{\"kind\":\"LABEL\",\"businessCode\":\"" + escape(businessCode)
                + "\",\"templateCode\":\"" + escape(templateCode)
                + "\",\"copies\":" + copies
                + ",\"widthMm\":40,\"heightMm\":30"
                + ",\"version\":\"V" + version
                + "\",\"instance\":\"" + instance
                + "\",\"qr\":\"" + qr
                + "\",\"lines\":[\"单号:" + escape(businessCode) + "\",\"模板:" + escape(templateCode) + "\"]}";
    }

    private String motherCode(String code) {
        if (documents == null) return "";
        if (documents.findMotherOrderByCode(code).isPresent()) return code;
        return documents.findChildWorkOrderByCode(code).map(child -> child.motherOrderCode()).orElse("");
    }

    private void assertPrintable(String businessCode, AuthUserAccount account) {
        if (!readable(businessCode, account)) {
            throw new PermissionDeniedException("打印对象不在当前账号的业务数据范围内");
        }
    }

    private boolean readable(String businessCode, AuthUserAccount account) {
        if (documents != null) {
            boolean known = documents.findMotherOrderByCode(businessCode).isPresent()
                    || documents.findChildWorkOrderByCode(businessCode).isPresent();
            if (!known && materialStocks != null && account != null) {
                materialStocks.batchLabelSource(businessCode, account);
                return true;
            }
            if (!known) {
                throw new BusinessException("打印对象不存在：" + businessCode);
            }
        }
        if (account == null || dataAccess == null) {
            return true;
        }
        try {
            dataAccess.assertCanRead(BusinessDataAccess.contextOf(account), businessCode);
            return true;
        } catch (PermissionDeniedException ex) {
            return false;
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private PrintJob replace(PrintJob job, PrintJobStatus status, String leaseUntil, String failureReason) {
        return jobs.save(new PrintJob(
                job.id(), job.requestId(), job.businessCode(), job.templateCode(), job.copies(), job.contentSnapshot(),
                status, job.stationId(), leaseUntil, failureReason, job.predecessorId(), job.retryReason(),
                job.createdBy(), job.createdAt(), TIME.format(LocalDateTime.now())
        ));
    }

}
