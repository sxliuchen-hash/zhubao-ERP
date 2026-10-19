package com.zhubao.erp.scan.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.scan.domain.ScanObjectType;
import com.zhubao.erp.scan.domain.ScanResolveResponse;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ScanCodeParser {

    private static final String SYSTEM_PREFIX = "JG";
    private static final Map<String, ScanObjectType> TYPE_MAP = Map.of(
            "mother", ScanObjectType.MOTHER_ORDER,
            "MO", ScanObjectType.MOTHER_ORDER,
            "work_order", ScanObjectType.WORK_ORDER,
            "WO", ScanObjectType.WORK_ORDER,
            "outbound", ScanObjectType.OUTBOUND_ORDER,
            "OUT", ScanObjectType.OUTBOUND_ORDER,
            "rework", ScanObjectType.REWORK,
            "EXP", ScanObjectType.EXPRESS_NO
    );

    public ScanResolveResponse resolve(String content, String scene) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.startsWith(SYSTEM_PREFIX + "|")) {
            return resolveSystemCode(normalized, scene);
        }
        String waybill = CourierNumbers.extract(normalized);
        if (!waybill.isBlank()) {
            return new ScanResolveResponse(ScanObjectType.EXPRESS_NO, waybill, "intake", true);
        }
        throw new BusinessException("未识别的二维码内容");
    }

    private ScanResolveResponse resolveSystemCode(String content, String scene) {
        String[] parts = content.split("\\|", -1);
        if ((parts.length != 3 && parts.length != 5) || !SYSTEM_PREFIX.equals(parts[0])) {
            throw new BusinessException("未识别的二维码内容");
        }
        ScanObjectType objectType = TYPE_MAP.get(parts[1]);
        if (objectType == null || parts[2].isBlank()) {
            throw new BusinessException("未识别的二维码类型");
        }
        String code = objectType == ScanObjectType.EXPRESS_NO ? CourierNumbers.normalize(parts[2]) : parts[2].trim();
        if (code.isBlank()) {
            throw new BusinessException("未识别的二维码类型");
        }
        return new ScanResolveResponse(objectType, code, nextAction(objectType, scene), true);
    }

    /** 三段旧码没有版本，返回空字符串，调用方应显示为版本未知。 */
    public String labelVersion(String content) {
        String normalized = content == null ? "" : content.trim();
        if (!normalized.startsWith(SYSTEM_PREFIX + "|")) {
            return "";
        }
        String[] parts = normalized.split("\\|", -1);
        if (parts.length == 3) {
            return "";
        }
        if (parts.length == 5 && !parts[3].isBlank()) {
            return parts[3].trim();
        }
        throw new BusinessException("未识别的二维码内容");
    }

    /** 五段新码的标签实例。三段旧码没有实例，不能据此区分两次打印。 */
    public String labelInstance(String content) {
        String normalized = content == null ? "" : content.trim();
        if (!normalized.startsWith(SYSTEM_PREFIX + "|")) {
            return "";
        }
        String[] parts = normalized.split("\\|", -1);
        if (parts.length == 3) {
            return "";
        }
        if (parts.length == 5) {
            return parts[4].trim();
        }
        throw new BusinessException("未识别的二维码内容");
    }

    private String nextAction(ScanObjectType objectType, String scene) {
        if (objectType == ScanObjectType.WORK_ORDER && "pwa_report".equals(scene)) {
            return "report";
        }
        if (objectType == ScanObjectType.MOTHER_ORDER) {
            return "trace";
        }
        if (objectType == ScanObjectType.OUTBOUND_ORDER) {
            return "outbound";
        }
        if (objectType == ScanObjectType.EXPRESS_NO) {
            return "intake";
        }
        return "lookup";
    }
}
