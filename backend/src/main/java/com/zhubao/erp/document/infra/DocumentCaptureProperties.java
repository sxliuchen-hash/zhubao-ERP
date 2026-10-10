package com.zhubao.erp.document.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "zhubao.documents.capture")
public class DocumentCaptureProperties {

    private long photoMaxBytes = 8L * 1024 * 1024;
    private long videoMaxBytes = 50L * 1024 * 1024;

    public long getPhotoMaxBytes() {
        return photoMaxBytes;
    }

    public void setPhotoMaxBytes(long photoMaxBytes) {
        this.photoMaxBytes = photoMaxBytes;
    }

    public long getVideoMaxBytes() {
        return videoMaxBytes;
    }

    public void setVideoMaxBytes(long videoMaxBytes) {
        this.videoMaxBytes = videoMaxBytes;
    }
}
