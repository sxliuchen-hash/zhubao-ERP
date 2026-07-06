package com.zhubao.erp.media.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.media.app.VideoClipTaskService;
import com.zhubao.erp.media.domain.VideoClipTask;
import com.zhubao.erp.media.domain.VideoClipTaskRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/video-clip-tasks")
public class VideoClipTaskController {

    private final VideoClipTaskService videoClipTaskService;

    public VideoClipTaskController(VideoClipTaskService videoClipTaskService) {
        this.videoClipTaskService = videoClipTaskService;
    }

    @PostMapping
    public ApiResponse<VideoClipTask> create(@Valid @RequestBody VideoClipTaskRequest request) {
        return ApiResponse.ok(videoClipTaskService.create(request));
    }

    @GetMapping
    public ApiResponse<List<VideoClipTask>> findAll() {
        return ApiResponse.ok(videoClipTaskService.findAll());
    }

    @PostMapping("/{code}/retry")
    public ApiResponse<VideoClipTask> retry(@PathVariable String code) {
        return ApiResponse.ok(videoClipTaskService.retry(code));
    }

    @PostMapping("/{code}/manual-upload")
    public ApiResponse<VideoClipTask> manualUpload(@PathVariable String code, @Valid @RequestBody ManualUploadRequest request) {
        return ApiResponse.ok(videoClipTaskService.markManualUploaded(code, request.storageKey()));
    }

    public record ManualUploadRequest(@NotBlank String storageKey) {
    }
}
