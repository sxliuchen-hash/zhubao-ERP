package com.zhubao.erp.sampling.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.sampling.app.SamplingVersionService;
import com.zhubao.erp.sampling.domain.SamplingVersionView;
import com.zhubao.erp.security.app.OperatorContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 打样版本和客户确认。不接收模型文件本体。
 */
@RestController
@RequestMapping("/api/sampling/orders/{samplingCode}/versions")
public class SamplingVersionController {

    private final SamplingVersionService versions;
    private final OperatorContextResolver actors;

    public SamplingVersionController(SamplingVersionService versions, OperatorContextResolver actors) {
        this.versions = versions;
        this.actors = actors;
    }

    @GetMapping
    public ApiResponse<List<SamplingVersionView>> list(@PathVariable String samplingCode, HttpServletRequest request) {
        return ApiResponse.ok(versions.list(actors.resolve(request), samplingCode));
    }

    @PostMapping
    public ApiResponse<SamplingVersionView> add(@PathVariable String samplingCode, @RequestBody AddRequest body,
                                                HttpServletRequest request) {
        return ApiResponse.ok(versions.add(
                body.requestId(),
                actors.resolve(request),
                samplingCode,
                body.kind(),
                body.parameterText(),
                body.evidenceCode(),
                body.modelFileName(),
                body.modelFormat(),
                body.modelVersion(),
                body.modelLocationNote(),
                body.modelEvidenceCode(),
                body.versionNo()
        ));
    }

    @PostMapping("/confirmations")
    public ApiResponse<SamplingVersionView> confirm(@PathVariable String samplingCode, @RequestBody DecisionRequest body,
                                                    HttpServletRequest request) {
        return ApiResponse.ok(versions.confirm(
                body.requestId(), actors.resolve(request), samplingCode, body.kind(), body.versionNo(),
                body.note(), body.credentialCode()));
    }

    @PostMapping("/rejections")
    public ApiResponse<SamplingVersionView> reject(@PathVariable String samplingCode, @RequestBody DecisionRequest body,
                                                   HttpServletRequest request) {
        return ApiResponse.ok(versions.reject(
                body.requestId(), actors.resolve(request), samplingCode, body.kind(), body.versionNo(),
                body.note(), body.credentialCode()));
    }

    public record AddRequest(
            String requestId,
            String kind,
            String parameterText,
            String evidenceCode,
            String modelFileName,
            String modelFormat,
            String modelVersion,
            String modelLocationNote,
            String modelEvidenceCode,
            Integer versionNo
    ) {
    }

    public record DecisionRequest(
            String requestId,
            String kind,
            Integer versionNo,
            String note,
            String credentialCode
    ) {
    }
}
