package com.zhubao.erp.document.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.document.app.DocumentCaptureService;
import com.zhubao.erp.document.domain.EvidenceAttachmentUploadResult;
import com.zhubao.erp.document.domain.EvidenceCodes;
import com.zhubao.erp.security.app.OperatorContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

/** 独立打样与金工的现场照片，服务按真实业务单据校验权限和客户归属。 */
@RestController
public class IndependentReportPhotoController {
    private final DocumentCaptureService captures;
    private final OperatorContextResolver actors;
    public IndependentReportPhotoController(DocumentCaptureService captures, OperatorContextResolver actors) {
        this.captures = captures; this.actors = actors;
    }
    @PostMapping(value = "/api/sampling/orders/{code}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<EvidenceAttachmentUploadResult> sampling(@PathVariable String code, @RequestParam MultipartFile file,
                                                                HttpServletRequest request) throws IOException {
        return upload(EvidenceCodes.SAMPLING, code, file, request);
    }
    @PostMapping(value = "/api/goldwork/orders/{code}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<EvidenceAttachmentUploadResult> goldwork(@PathVariable String code, @RequestParam MultipartFile file,
                                                                HttpServletRequest request) throws IOException {
        return upload(EvidenceCodes.GOLDWORK, code, file, request);
    }
    private ApiResponse<EvidenceAttachmentUploadResult> upload(String type, String code, MultipartFile file, HttpServletRequest request) throws IOException {
        return ApiResponse.ok(captures.storeVerified(type, code, "PROCESS", file.getOriginalFilename(), file.getContentType(),
                file.getInputStream(), file.getSize(), actors.requireSession(request)));
    }
}
