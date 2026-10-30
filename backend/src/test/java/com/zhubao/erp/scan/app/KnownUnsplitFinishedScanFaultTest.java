package com.zhubao.erp.scan.app;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * P0 已知故障。不把漏查或未核定的重量写成已经通过。
 */
class KnownUnsplitFinishedScanFaultTest {

    @Test
    @Disabled("已知故障：不拆母单 MD-20261029-0003 有 120 颗成品却会被扫码查询漏掉，另有重量差异待核。修复前不运行，避免把漏查或某个重量写成通过。")
    void 不拆母单成品数量应能被扫码读到且不改原重量() {
        fail("修复后应按母单号读到已记录的 120 颗成品，并单独核定重量，不在本测试里填一个重量默认值");
    }
}
