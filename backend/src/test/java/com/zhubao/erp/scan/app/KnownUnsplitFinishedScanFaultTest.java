package com.zhubao.erp.scan.app;

import com.zhubao.erp.business.app.ProcessingTargetResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 扫码已能按母单号定位整单成品。末道补录 7.780 克和成品账面 7.260 克的差异仍待核，解析不挑选其中一个覆盖原重量。
 */
class KnownUnsplitFinishedScanFaultTest {

    @Test
    void 母单执行号可定位且不改写待核重量() {
        assertThat(ProcessingTargetResolver.keptMotherStock("MD-20261029-0003", "MD-20261029-0003")).isTrue();
        assertThat(ProcessingTargetResolver.identity("MD-20261029-0003", ""))
                .isEqualTo(ProcessingTargetResolver.ExecutionIdentity.EMPTY);
        assertThat(ProcessingTargetResolver.identity("MD-20261029-0003", "MD-20261029-0003"))
                .isEqualTo(ProcessingTargetResolver.ExecutionIdentity.MOTHER);
    }
}
