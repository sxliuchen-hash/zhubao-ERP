package com.zhubao.erp.security.app;

import com.zhubao.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    private final PasswordPolicy passwordPolicy = new PasswordPolicy();

    @Test
    void acceptsLongPasswordWithLettersAndDigits() {
        assertThatCode(() -> passwordPolicy.assertAcceptable("Secure-2026-Plan!", "planner"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsBlankShortWeakUsernameAndClassMissingPasswords() {
        assertThatThrownBy(() -> passwordPolicy.assertAcceptable("", "planner"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("密码不能为空");
        assertThatThrownBy(() -> passwordPolicy.assertAcceptable("short7", "planner"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少 12 位");
        assertThatThrownBy(() -> passwordPolicy.assertAcceptable("admin123", "admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("常见弱口令");
        assertThatThrownBy(() -> passwordPolicy.assertAcceptable("verylonguser1", "verylonguser1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账号名相同");
        assertThatThrownBy(() -> passwordPolicy.assertAcceptable("NoDigitsHere!", "planner"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("字母和数字");
        assertThatThrownBy(() -> passwordPolicy.assertAcceptable("  Secure-2026-Plan!", "planner"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("首尾空白");
    }
}
