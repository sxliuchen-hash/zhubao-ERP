package com.zhubao.erp.sampling.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.sampling.app.SamplingMaterialService;
import com.zhubao.erp.sampling.domain.SamplingMaterialUseView;
import com.zhubao.erp.security.app.OperatorContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 打样领退料。数量记在真实库存来源上，本接口不另建实物余额。
 */
@RestController
@RequestMapping("/api/sampling/orders/{samplingCode}/material-uses")
public class SamplingMaterialController {

    private final SamplingMaterialService materials;
    private final OperatorContextResolver actors;

    public SamplingMaterialController(SamplingMaterialService materials, OperatorContextResolver actors) {
        this.materials = materials;
        this.actors = actors;
    }

    @GetMapping
    public ApiResponse<List<SamplingMaterialUseView>> list(@PathVariable String samplingCode, HttpServletRequest request) {
        return ApiResponse.ok(materials.list(actors.resolve(request), samplingCode));
    }

    @PostMapping
    public ApiResponse<SamplingMaterialUseView> pick(@PathVariable String samplingCode, @RequestBody PickRequest body,
                                                     HttpServletRequest request) {
        return ApiResponse.ok(materials.pick(
                body.requestId(), actors.resolve(request), samplingCode, body.receiptId(), body.sourceDocumentCode(),
                body.lineNo(), body.workshopCode(), body.quantity(), body.weight(), body.unit()));
    }

    @PostMapping("/{useId}/returns")
    public ApiResponse<SamplingMaterialUseView> returnMaterial(@PathVariable String samplingCode, @PathVariable String useId,
                                                              @RequestBody MoveRequest body, HttpServletRequest request) {
        return ApiResponse.ok(materials.returnMaterial(
                body.requestId(), actors.resolve(request), samplingCode, useId, body.quantity(), body.weight(), body.unit()));
    }

    @PostMapping("/{useId}/inputs")
    public ApiResponse<SamplingMaterialUseView> recordInput(@PathVariable String samplingCode, @PathVariable String useId,
                                                           @RequestBody InputRequest body, HttpServletRequest request) {
        return ApiResponse.ok(materials.recordInput(
                body.requestId(), actors.resolve(request), samplingCode, useId, body.quantity()));
    }

    public record PickRequest(
            String requestId,
            String receiptId,
            String sourceDocumentCode,
            Integer lineNo,
            String workshopCode,
            BigDecimal quantity,
            BigDecimal weight,
            String unit
    ) {
    }

    public record MoveRequest(String requestId, BigDecimal quantity, BigDecimal weight, String unit) {
    }

    public record InputRequest(String requestId, BigDecimal quantity) {
    }
}
