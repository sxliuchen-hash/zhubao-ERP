package com.zhubao.erp.shell;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ShellPoliciesTest {

    @Test
    public void onlyHttpsOriginIsAccepted() {
        assertNull(ShellPolicies.INSTANCE.parseHttpsOrigin("http://erp.example.com"));
        assertNull(ShellPolicies.INSTANCE.parseHttpsOrigin(""));
        ShellPolicies.HttpsOrigin origin = ShellPolicies.INSTANCE.parseHttpsOrigin("https://erp.factory.local:8443/app");
        assertEquals("erp.factory.local", origin.getHost());
        assertEquals(":8443", origin.getPort());
    }

    @Test
    public void mainFrameErrorsStayDistinctFromPrintedSuccess() {
        assertEquals("登录已失效", ShellPolicies.INSTANCE.mainFrameError(401).getFirst());
        assertEquals("服务暂时不可用", ShellPolicies.INSTANCE.mainFrameError(500).getFirst());
        assertEquals("服务暂时不可用", ShellPolicies.INSTANCE.mainFrameError(503).getFirst());
        assertNull(ShellPolicies.INSTANCE.mainFrameError(404));
    }

    @Test
    public void cameraDenialCanRequestAgainOrOpenSettings() {
        assertEquals("CAPTURE", ShellPolicies.INSTANCE.cameraNextStep(true, false, false));
        assertEquals("REQUEST", ShellPolicies.INSTANCE.cameraNextStep(false, false, false));
        assertEquals("EXPLAIN_AND_REQUEST", ShellPolicies.INSTANCE.cameraNextStep(false, true, true));
        assertEquals("OPEN_SETTINGS", ShellPolicies.INSTANCE.cameraNextStep(false, true, false));
    }
}