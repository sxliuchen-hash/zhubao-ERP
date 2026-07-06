package com.zhubao.erp.sample.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.sample.app.SampleScenarioService;
import com.zhubao.erp.sample.domain.SampleScenario;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/samples")
public class SampleScenarioController {

    private final SampleScenarioService sampleScenarioService;

    public SampleScenarioController(SampleScenarioService sampleScenarioService) {
        this.sampleScenarioService = sampleScenarioService;
    }

    @GetMapping("/scenarios")
    public ApiResponse<List<SampleScenario>> scenarios() {
        return ApiResponse.ok(sampleScenarioService.findAll());
    }

    @GetMapping("/summary")
    public ApiResponse<SampleSummary> summary() {
        List<SampleScenario> scenarios = sampleScenarioService.findAll();
        return ApiResponse.ok(new SampleSummary(scenarios.size(), sampleScenarioService.readyCount()));
    }

    public record SampleSummary(int total, long ready) {
    }
}
