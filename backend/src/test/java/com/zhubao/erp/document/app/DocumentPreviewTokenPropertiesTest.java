package com.zhubao.erp.document.app;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentPreviewTokenPropertiesTest {

    @Test
    void rejectsMissingShortAndDefaultProductionSecrets() {
        DocumentPreviewTokenProperties properties = new DocumentPreviewTokenProperties();
        properties.setRequireExplicitSecret(true);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class).hasMessageContaining("独立签名密钥");
        for (String invalid : new String[]{null, "", "  ", "short-secret"}) {
            properties.setSecret(invalid);
            assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
        }
        properties.setSecret("  installation-specific-random-secret-at-least-32  ");
        assertThatCode(properties::validate).doesNotThrowAnyException();
        assertThat(properties.signingSecret()).isEqualTo("installation-specific-random-secret-at-least-32");
    }

    @Test
    void permitsLocalDevelopmentDefaultWithoutProductionRequirement() {
        DocumentPreviewTokenProperties properties = new DocumentPreviewTokenProperties();
        assertThatCode(properties::validate).doesNotThrowAnyException();
        assertThat(properties.signingSecret()).isNotBlank();
    }
}
