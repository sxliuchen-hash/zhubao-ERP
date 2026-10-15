package com.zhubao.print;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrintAgentRoutingTest {
    @Test
    void 一台工位映射多个逻辑队列且缺失映射即报错() {
        Properties config = new Properties();
        config.setProperty("queue.Q1", "Windows 队列一");
        config.setProperty("queue.Q2", "Windows 队列二");
        var mappings = PrintAgent.queueMappings(config);
        assertEquals("Windows 队列一", mappings.get("Q1"));
        assertEquals("Windows 队列二", mappings.get("Q2"));
        assertEquals(null, mappings.get("Q3"));
        assertThrows(IllegalStateException.class, () -> PrintAgent.resolvePrinterName(mappings, "Q3"));
        assertThrows(IllegalArgumentException.class, () -> PrintAgent.queueMappings(new Properties()));
    }
}
