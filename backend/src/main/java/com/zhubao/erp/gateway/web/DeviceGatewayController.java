package com.zhubao.erp.gateway.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.gateway.app.DeviceGatewayService;
import com.zhubao.erp.gateway.domain.ConsumeReadingCommand;
import com.zhubao.erp.gateway.domain.DeviceReading;
import com.zhubao.erp.gateway.domain.GatewayDeviceView;
import com.zhubao.erp.gateway.domain.GatewayIngestionSummary;
import com.zhubao.erp.gateway.domain.ReadingConsumption;
import com.zhubao.erp.gateway.domain.RegisterGatewayDeviceCommand;
import com.zhubao.erp.gateway.domain.SubmitDeviceReadingCommand;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.app.AuthSessionService;
import com.zhubao.erp.security.error.PermissionDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 设备网关 HTTP 接口。设备读数上传使用设备密钥；人工消费必须使用真实登录会话。
 * 读取接口提供设备列表、近期读数和采集摘要。
 */
@RestController
@RequestMapping("/api/hardware/gateway")
public class DeviceGatewayController {

    private final DeviceGatewayService service;
    private final OperatorContextResolver operatorContextResolver;
    private final AuthSessionService authSessions;

    public DeviceGatewayController(
            DeviceGatewayService service,
            OperatorContextResolver operatorContextResolver
    ) {
        this(service, operatorContextResolver, null);
    }

    @Autowired
    public DeviceGatewayController(DeviceGatewayService service, OperatorContextResolver operatorContextResolver,
                                   AuthSessionService authSessions) {
        this.service = service;
        this.operatorContextResolver = operatorContextResolver;
        this.authSessions = authSessions;
    }

    @PostMapping("/readings")
    public ApiResponse<DeviceReading> ingest(@Valid @RequestBody SubmitDeviceReadingCommand command) {
        return ApiResponse.ok(service.ingest(command));
    }

    @GetMapping("/readings")
    public ApiResponse<List<DeviceReading>> recentReadings(@RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(service.recentReadings(limit));
    }

    @GetMapping("/devices/{deviceCode}/readings")
    public ApiResponse<List<DeviceReading>> readingsByDevice(
            @PathVariable String deviceCode,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ApiResponse.ok(service.readingsByDevice(deviceCode, limit));
    }

    @GetMapping("/devices")
    public ApiResponse<List<GatewayDeviceView>> devices() {
        return ApiResponse.ok(service.devices());
    }

    @GetMapping("/summary")
    public ApiResponse<GatewayIngestionSummary> summary() {
        return ApiResponse.ok(service.summary());
    }

    @PostMapping("/consumptions")
    public ApiResponse<ReadingConsumption> consume(
            @Valid @RequestBody ConsumeReadingCommand command,
            HttpServletRequest request
    ) {
        var context = operatorContextResolver.requireSession(request);
        if (authSessions == null) throw new PermissionDeniedException("设备读数消费必须登录");
        var account = authSessions.currentAccount(request.getHeader(AuthSessionService.AUTHORIZATION_HEADER))
                .orElseThrow(() -> new PermissionDeniedException("设备读数消费必须登录"));
        return ApiResponse.ok(service.consume(command, context, account));
    }

    @GetMapping("/consumptions")
    public ApiResponse<List<ReadingConsumption>> recentConsumptions(@RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(service.recentConsumptions(limit));
    }

    @PostMapping("/devices")
    public ApiResponse<GatewayDeviceView> registerDevice(@Valid @RequestBody RegisterGatewayDeviceCommand command) {
        return ApiResponse.ok(service.registerDevice(command));
    }

    @PostMapping("/devices/{deviceCode}/status")
    public ApiResponse<GatewayDeviceView> setStatus(
            @PathVariable String deviceCode,
            @RequestBody DeviceStatusRequest request
    ) {
        return ApiResponse.ok(service.setDeviceEnabled(deviceCode, request.enabled()));
    }

    @PostMapping("/devices/{deviceCode}/rotate-key")
    public ApiResponse<GatewayDeviceView> rotateKey(
            @PathVariable String deviceCode,
            @RequestBody RotateKeyRequest request
    ) {
        return ApiResponse.ok(service.rotateDeviceKey(deviceCode, request.deviceKey()));
    }

    public record DeviceStatusRequest(boolean enabled) {
    }

    public record RotateKeyRequest(String deviceKey) {
    }
}
