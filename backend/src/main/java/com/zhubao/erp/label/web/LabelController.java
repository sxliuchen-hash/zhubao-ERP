package com.zhubao.erp.label.web;

import com.zhubao.erp.business.app.BusinessDataAccess;
import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.label.app.LabelService;
import com.zhubao.erp.label.app.LabelTemplateService;
import com.zhubao.erp.label.domain.GenerateLabelCommand;
import com.zhubao.erp.label.domain.LabelPayload;
import com.zhubao.erp.label.domain.LabelTemplate;
import com.zhubao.erp.label.domain.ReprintLabelCommand;
import com.zhubao.erp.label.domain.SaveLabelTemplateCommand;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.domain.OperatorContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 标签打印接口。
 *
 * <p>能力：<br>
 * 1) 通用生成 {@code POST /api/label/generate}：前端传入类型、主编码与固定字段，返回可打印载荷（可指定模板编码）；<br>
 * 2) 母单标签便捷生成 {@code GET /api/label/mother-orders/{code}}：从结构化母单便捷生成（可指定模板编码）；<br>
 * 3) 模板管理 {@code GET/POST /api/label/templates}：查询与自定义标签模板和打印机设置。<br>
 * 返回的 {@link LabelPayload} 携带二维码内容（JG|类型|编码）、品牌抬头和打印机设置，前端据此渲染标签并出码打印。</p>
 */
@RestController
@RequestMapping("/api/label")
public class LabelController {

    private final LabelService labelService;
    private final LabelTemplateService labelTemplateService;
    private final OperatorContextResolver operatorContextResolver;
    private final BusinessDataAccess dataAccess;
    private com.zhubao.erp.inventory.app.MaterialStockService materialStocks;
    private com.zhubao.erp.security.app.AuthSessionService sessions;
    @org.springframework.beans.factory.annotation.Autowired
    public void setStockAccess(com.zhubao.erp.inventory.app.MaterialStockService materialStocks, com.zhubao.erp.security.app.AuthSessionService sessions) { this.materialStocks = materialStocks; this.sessions = sessions; }
    private void assertBatch(String code, HttpServletRequest request) {
        var account = sessions.currentAccount(request.getHeader(com.zhubao.erp.security.app.AuthSessionService.AUTHORIZATION_HEADER)).orElseThrow(() -> new com.zhubao.erp.common.error.BusinessException("请先登录"));
        materialStocks.batchLabelSource(code, account);
    }

    public LabelController(
            LabelService labelService,
            LabelTemplateService labelTemplateService,
            OperatorContextResolver operatorContextResolver,
            BusinessDataAccess dataAccess
    ) {
        this.labelService = labelService;
        this.labelTemplateService = labelTemplateService;
        this.operatorContextResolver = operatorContextResolver;
        this.dataAccess = dataAccess;
    }

    /** 通用标签生成。已保存单据使用与母单、子单标签相同的客户范围。 */
    @PostMapping("/generate")
    public ApiResponse<LabelPayload> generate(@Valid @RequestBody GenerateLabelCommand command, HttpServletRequest request) {
        OperatorContext operator = operatorContextResolver.requireSession(request);
        if (command != null && command.primaryCode() != null) {
            if (com.zhubao.erp.label.domain.LabelType.fromString(command.type()) == com.zhubao.erp.label.domain.LabelType.STOCK_BATCH) assertBatch(command.primaryCode(), request);
            else dataAccess.assertCanReadIfStored(operator, command.primaryCode());
        }
        return ApiResponse.ok(labelService.generate(command));
    }

    @GetMapping("/stock-batches/{code}")
    public ApiResponse<LabelPayload> stockBatchLabel(@PathVariable String code, HttpServletRequest request) {
        operatorContextResolver.requireSession(request);
        assertBatch(code, request);
        return ApiResponse.ok(labelService.generateStockBatchLabel(code));
    }

    private com.zhubao.erp.label.app.LabelStorageEditionService storageEditions;
    private com.zhubao.erp.security.app.PermissionGuard permissions;
    @org.springframework.beans.factory.annotation.Autowired
    public void setStorageEditions(com.zhubao.erp.label.app.LabelStorageEditionService storageEditions, com.zhubao.erp.security.app.PermissionGuard permissions) { this.storageEditions = storageEditions; this.permissions = permissions; }
    public record AdoptStorage(String requestId, int expectedEdition, String reason, String templateCode) { }
    @GetMapping("/{code}/storage-editions")
    public ApiResponse<List<com.zhubao.erp.label.domain.LabelStorageEdition>> storageHistory(@PathVariable String code, HttpServletRequest request) {
        var operator = operatorContextResolver.requireSession(request);
        if (labelService.isStockBatch(code)) assertBatch(code, request); else dataAccess.assertCanRead(operator, code);
        return ApiResponse.ok(storageEditions.history(code));
    }
    @PostMapping("/{code}/storage-editions")
    public ApiResponse<Integer> adoptStorage(@PathVariable String code, @RequestBody AdoptStorage command, HttpServletRequest request) {
        var operator = operatorContextResolver.requireSession(request);
        permissions.assertAllowed(com.zhubao.erp.security.domain.PermissionCode.LABEL_PRINT, operator);
        permissions.assertAllowed(com.zhubao.erp.security.domain.PermissionCode.MASTER_DATA_MANAGE, operator);
        if (labelService.isStockBatch(code)) assertBatch(code, request); else dataAccess.assertCanRead(operator, code);
        return ApiResponse.ok(storageEditions.adopt(code, command.requestId(), command.expectedEdition(), command.reason(), operator.operator(), command.templateCode()));
    }

    /** 按母单号便捷生成母单标签，可指定模板编码。 */
    @GetMapping("/mother-orders/{code}")
    public ApiResponse<LabelPayload> motherLabel(
            @PathVariable String code,
            @RequestParam(required = false) String templateCode,
            HttpServletRequest request
    ) {
        dataAccess.assertCanRead(operatorContextResolver.requireSession(request), code);
        return ApiResponse.ok(labelService.generateMotherLabel(code, templateCode));
    }

    /** 按子单号生成贴纸，可指定模板编码。 */
    @GetMapping("/work-orders/{code}")
    public ApiResponse<LabelPayload> workOrderLabel(
            @PathVariable String code,
            @RequestParam(required = false) String templateCode,
            HttpServletRequest request
    ) {
        dataAccess.assertCanRead(operatorContextResolver.requireSession(request), code);
        return ApiResponse.ok(labelService.generateWorkOrderLabel(code, templateCode));
    }

    /** 补打母单或子单贴纸，并写入补打日志。 */
    @PostMapping("/reprints")
    public ApiResponse<LabelPayload> reprint(
            @Valid @RequestBody ReprintLabelCommand command,
            HttpServletRequest request
    ) {
        OperatorContext operator = operatorContextResolver.requireSession(request);
        if (command != null && command.primaryCode() != null && !command.primaryCode().isBlank()) {
            dataAccess.assertCanRead(operator, command.primaryCode().trim());
        }
        return ApiResponse.ok(labelService.reprint(new ReprintLabelCommand(
                command.type(),
                command.primaryCode(),
                command.reason(),
                operator.operator(),
                command.expectedSnapshotHash()
        )));
    }

    /** 查询全部标签模板。 */
    @GetMapping("/templates")
    public ApiResponse<List<LabelTemplate>> listTemplates() {
        return ApiResponse.ok(labelTemplateService.findAll());
    }

    /** 新增或更新标签模板（受 LABEL_PRINT 权限守卫）。 */
    @PostMapping("/templates")
    public ApiResponse<LabelTemplate> saveTemplate(@Valid @RequestBody SaveLabelTemplateCommand command) {
        return ApiResponse.ok(labelTemplateService.save(command));
    }
}
