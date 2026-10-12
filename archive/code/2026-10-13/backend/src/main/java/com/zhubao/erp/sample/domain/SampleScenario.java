package com.zhubao.erp.sample.domain;

import java.util.List;

public record SampleScenario(
        String id,
        String name,
        String owner,
        String purpose,
        List<String> chain,
        List<String> evidence,
        SampleScenarioStatus status
) {
}
