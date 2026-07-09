package com.zhubao.erp.label.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.label.app.LabelService;
import com.zhubao.erp.label.domain.GenerateLabelCommand;
import com.zhubao.erp.label.domain.LabelPayload;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 标签打印接口。
 *
 * <p>提供两个能力：<br>
 * 1) 通用生成 {@code POST /api/label/generate}：前端传入类型、主编码与固定字段，返回可打印载荷；<br>
 * 2) 母单标签便捷生成 {@code GET /api/label/mother-orders/{code}}：从已落库的结构化母单直接生成标签。<br>
 * 返回的 {@link LabelPayload} 携带二维码内容（JG|类型|编码），前端据此渲染标签并出码。</p>
 */
@RestController
@RequestMapping("/api/label")
public class LabelController {

    private final LabelService labelService;

    public LabelController(LabelService labelService) {
        this.labelService = labelService;
    }

    /** 通用标签生成。 */
    @PostMapping("/generate")
    public ApiResponse<LabelPayload> generate(@Valid @RequestBody GenerateLabelCommand command) {
        return ApiResponse.ok(labelService.generate(command));
    }

    /** 按母单号便捷生成母单标签。 */
    @GetMapping("/mother-orders/{code}")
    public ApiResponse<LabelPayload> motherLabel(@PathVariable String code) {
        return ApiResponse.ok(labelService.generateMotherLabel(code));
    }
}
