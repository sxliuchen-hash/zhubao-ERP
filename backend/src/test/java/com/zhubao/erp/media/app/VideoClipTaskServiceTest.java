package com.zhubao.erp.media.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.media.domain.VideoClipTask;
import com.zhubao.erp.media.domain.VideoClipTaskRequest;
import com.zhubao.erp.media.domain.VideoTaskStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VideoClipTaskServiceTest {

    @Test
    void createsRetryAndManualUploadTask() {
        VideoClipTaskService service = new VideoClipTaskService(5, 3);
        VideoClipTask task = service.create(new VideoClipTaskRequest(
                "inbound_unpack",
                "CC-20260706-001-JL",
                "CAM-UNPACK-01",
                Instant.parse("2026-07-06T01:00:00Z"),
                Instant.parse("2026-07-06T01:05:00Z"),
                null
        ));

        assertThat(task.status()).isEqualTo(VideoTaskStatus.PENDING);
        assertThat(task.bufferSeconds()).isEqualTo(5);

        VideoClipTask retried = service.retry(task.code());
        assertThat(retried.retryCount()).isEqualTo(1);

        VideoClipTask uploaded = service.markManualUploaded(task.code(), "manual/video.mp4");
        assertThat(uploaded.status()).isEqualTo(VideoTaskStatus.MANUAL_UPLOADED);
        assertThat(uploaded.storageKey()).isEqualTo("manual/video.mp4");
    }

    @Test
    void rejectsInvalidTimeRange() {
        VideoClipTaskService service = new VideoClipTaskService(5, 3);

        assertThatThrownBy(() -> service.create(new VideoClipTaskRequest(
                "outbound_pack",
                "OUT-001",
                "CAM-PACK-01",
                Instant.parse("2026-07-06T01:05:00Z"),
                Instant.parse("2026-07-06T01:00:00Z"),
                null
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("结束时间");
    }
}
