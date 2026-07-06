package com.zhubao.erp.media.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.media.domain.VideoClipTask;
import com.zhubao.erp.media.domain.VideoClipTaskRequest;
import com.zhubao.erp.media.domain.VideoTaskStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class VideoClipTaskService {

    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
            .withZone(ZoneId.systemDefault());

    private final AtomicLong sequence = new AtomicLong();
    private final List<VideoClipTask> tasks = new ArrayList<>();
    private final int defaultBufferSeconds;
    private final int maxRetry;

    public VideoClipTaskService(
            @Value("${zhubao.m0.video-buffer-seconds:5}") int defaultBufferSeconds,
            @Value("${zhubao.m0.max-video-retry:3}") int maxRetry
    ) {
        this.defaultBufferSeconds = defaultBufferSeconds;
        this.maxRetry = maxRetry;
    }

    public synchronized VideoClipTask create(VideoClipTaskRequest request) {
        if (!request.endAt().isAfter(request.startAt())) {
            throw new BusinessException("视频结束时间必须晚于开始时间");
        }
        VideoClipTask task = new VideoClipTask(
                nextCode(),
                request.bizType(),
                request.bizCode(),
                request.cameraCode(),
                request.startAt(),
                request.endAt(),
                request.bufferSeconds() == null ? defaultBufferSeconds : request.bufferSeconds(),
                0,
                VideoTaskStatus.PENDING,
                null
        );
        tasks.add(task);
        return task;
    }

    public synchronized VideoClipTask retry(String code) {
        VideoClipTask task = findByCode(code);
        if (task.retryCount() >= maxRetry) {
            throw new BusinessException("视频截取已达到最大重试次数");
        }
        VideoClipTask next = task.withStatus(VideoTaskStatus.PENDING, task.retryCount() + 1, task.storageKey());
        replace(task, next);
        return next;
    }

    public synchronized VideoClipTask markManualUploaded(String code, String storageKey) {
        VideoClipTask task = findByCode(code);
        VideoClipTask next = task.withStatus(VideoTaskStatus.MANUAL_UPLOADED, task.retryCount(), storageKey);
        replace(task, next);
        return next;
    }

    public synchronized List<VideoClipTask> findAll() {
        return List.copyOf(tasks);
    }

    private VideoClipTask findByCode(String code) {
        return tasks.stream()
                .filter(task -> task.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException("视频任务不存在"));
    }

    private void replace(VideoClipTask oldTask, VideoClipTask nextTask) {
        tasks.set(tasks.indexOf(oldTask), nextTask);
    }

    private String nextCode() {
        return "VID-" + CODE_DATE.format(Instant.now()) + "-" + String.format("%06d", sequence.incrementAndGet());
    }
}
