package com.zhubao.erp.document.domain;

import com.zhubao.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DesignArtifactReferenceTest {

    @Test
    void 保存外部说明和凭据编号并拒绝抓取路径() {
        DesignArtifactReference reference = new DesignArtifactReference(
                "戒托.3dm", "3DM", "v3", "样品柜A层，客户确认截图另存", "ATT-SHOT-1");
        assertThat(reference.version()).isEqualTo("v3");
        assertThat(reference.evidenceCode()).isEqualTo("ATT-SHOT-1");
        assertThatThrownBy(() -> new DesignArtifactReference("a.stl", "STL", "v1", "https://example.com/a.stl", ""))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new DesignArtifactReference("a.stl", "STL", "v1", "C:\\models\\a.stl", ""))
                .isInstanceOf(BusinessException.class);
    }
}
