package com.zhubao.erp.document.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
@ConfigurationProperties(prefix = "zhubao.documents.storage")
public class AttachmentStorageProperties {

    private String provider = "local";
    private String localRoot = "target/zhubao-evidence";

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getLocalRoot() {
        return localRoot;
    }

    public void setLocalRoot(String localRoot) {
        this.localRoot = localRoot;
    }

    public Path localRootPath() {
        return Path.of(localRoot);
    }
}
