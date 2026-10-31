package com.zhubao.erp.sampling.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.sampling.app.SamplingProductionLinkService;
import com.zhubao.erp.sampling.domain.SamplingProductionLinkView;
import com.zhubao.erp.security.app.OperatorContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 把已确认样品版本引用到普通母单。不改母单原文，也不自动建单。 */
@RestController
@RequestMapping("/api/sampling/production-links")
public class SamplingProductionLinkController {

    private final SamplingProductionLinkService links;
    private final OperatorContextResolver actors;

    public SamplingProductionLinkController(SamplingProductionLinkService links, OperatorContextResolver actors) {
        this.links = links;
        this.actors = actors;
    }

    @GetMapping
    public ApiResponse<SamplingProductionLinkView> find(@RequestParam String motherCode, HttpServletRequest request) {
        return ApiResponse.ok(links.find(actors.resolve(request), motherCode));
    }

    @PostMapping
    public ApiResponse<SamplingProductionLinkView> link(@RequestBody LinkRequest body, HttpServletRequest request) {
        return ApiResponse.ok(links.link(
                body.requestId(), actors.resolve(request), body.motherCode(), body.samplingCode(),
                body.versionKind(), body.versionNo()));
    }

    @PostMapping("/revocations")
    public ApiResponse<SamplingProductionLinkView> unlink(@RequestBody UnlinkRequest body, HttpServletRequest request) {
        return ApiResponse.ok(links.unlink(body.requestId(), actors.resolve(request), body.motherCode()));
    }

    public record LinkRequest(String requestId, String motherCode, String samplingCode, String versionKind, Integer versionNo) {
    }

    public record UnlinkRequest(String requestId, String motherCode) {
    }
}
