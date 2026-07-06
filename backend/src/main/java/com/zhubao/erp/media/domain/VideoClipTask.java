package com.zhubao.erp.media.domain;

import java.time.Instant;

public record VideoClipTask(
        String code,
        String bizType,
        String bizCode,
        String cameraCode,
        Instant startAt,
        Instant endAt,
        int bufferSeconds,
        int retryCount,
        VideoTaskStatus status,
        String storageKey
) {

    public VideoClipTask withStatus(VideoTaskStatus nextStatus, int nextRetryCount, String nextStorageKey) {
        return new VideoClipTask(
                code,
                bizType,
                bizCode,
                cameraCode,
                startAt,
                endAt,
                bufferSeconds,
                nextRetryCount,
                nextStatus,
                nextStorageKey
        );
    }
}
