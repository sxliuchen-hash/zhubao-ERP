package com.zhubao.erp.scan.web;

import com.zhubao.erp.scan.app.PublicDeliveryAccessService;
import com.zhubao.erp.scan.domain.PublicDeliverySummary;
import com.zhubao.erp.common.api.ApiResponse;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** 仅凭不可枚举标识读取明确公开的摘要和图片，每次写出前再次检查停用状态。 */
@RestController
@RequestMapping("/api/public/deliveries")
public class PublicDeliveryController {
    private final PublicDeliveryAccessService access;
    public PublicDeliveryController(PublicDeliveryAccessService access) { this.access=access; }
    @GetMapping("/{token}") public ResponseEntity<ApiResponse<PublicDeliverySummary>> read(@PathVariable String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Referrer-Policy","no-referrer").body(ApiResponse.ok(access.read(token)));
    }
    @GetMapping("/{token}/photos/{code}") public ResponseEntity<StreamingResponseBody> photo(@PathVariable String token,@PathVariable String code) {
        var file=access.photo(token,code);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.mediaType())).contentLength(file.size())
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").header("Referrer-Policy","no-referrer")
                .body(output -> file.write(0,file.size(),output));
    }
}
