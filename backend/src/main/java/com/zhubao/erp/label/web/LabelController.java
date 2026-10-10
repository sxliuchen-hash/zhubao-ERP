package com.zhubao.erp.label.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.label.app.LabelService;
import com.zhubao.erp.label.app.LabelTemplateService;
import com.zhubao.erp.label.domain.GenerateLabelCommand;
import com.zhubao.erp.label.domain.LabelPayload;
import com.zhubao.erp.label.domain.LabelTemplate;
import com.zhubao.erp.label.domain.ReprintLabelCommand;
import com.zhubao.erp.label.domain.SaveLabelTemplateCommand;
import com.zhubao.erp.security.app.OperatorContextResolver;
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

    public LabelController(
            LabelService labelService,
            LabelTemplateService labelTemplateService,
            OperatorContextResolver operatorContextResolver
    ) {
        this.labelService = labelService;
        this.labelTemplateService = labelTemplateService;
        this.operatorContextResolver = operatorContextResolver;
    }

    /** 通用标签生成。 */
    @PostMapping("/generate")
    public ApiResponse<LabelPayload> generate(@Valid @RequestBody GenerateLabelCommand command) {
        return ApiResponse.ok(labelService.generate(command));
    }

    /** 按母单号便捷生成母单标签，可指定模板编码。 */
    @GetMapping("/mother-orders/{code}")
    public ApiResponse<LabelPayload> motherLabel(
            @PathVariable String code,
            @RequestParam(required = false) String templateCode
    ) {
        return ApiResponse.ok(labelService.generateMotherLabel(code, templateCode));
    }

    /** 按子单号生成贴纸，可指定模板编码。 */
    @GetMapping("/work-orders/{code}")
    public ApiResponse<LabelPayload> workOrderLabel(
            @PathVariable String code,
            @RequestParam(required = false) String templateCode
    ) {
        return ApiResponse.ok(labelService.generateWorkOrderLabel(code, templateCode));
    }

    /** 补打母单或子单贴纸，并写入补打日志。 */
    @PostMapping("/reprints")
    public ApiResponse<LabelPayload> reprint(
            @Valid @RequestBody ReprintLabelCommand command,
            HttpServletRequest request
    ) {
        String operator = operatorContextResolver.resolve(request).operator();
        return ApiResponse.ok(labelService.reprint(new ReprintLabelCommand(
                command.type(),
                command.primaryCode(),
                command.reason(),
                operator
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
