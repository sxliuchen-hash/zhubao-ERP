package com.zhubao.erp.gateway.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.gateway.app.DeviceGatewayService;
import com.zhubao.erp.gateway.domain.ConsumeReadingCommand;
import com.zhubao.erp.gateway.domain.DeviceReading;
import com.zhubao.erp.gateway.domain.GatewayDeviceView;
import com.zhubao.erp.gateway.domain.GatewayIngestionSummary;
import com.zhubao.erp.gateway.domain.ReadingConsumption;
import com.zhubao.erp.gateway.domain.SubmitDeviceReadingCommand;
import com.zhubao.erp.security.app.OperatorContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HTTP surface of the low-cost hardware ingestion gateway.
 *
 * <p>The ingestion endpoint authenticates by device key (inside the service) rather than the
 * human RBAC interceptor, so cheap hardware bridges can push readings without a login. The read
 * endpoints expose devices, recent readings and an ingestion summary for the operations console.</p>
 */
@RestController
@RequestMapping("/api/hardware/gateway")
public class DeviceGatewayController {

    private final DeviceGatewayService service;
    private final OperatorContextResolver operatorContextResolver;

    public DeviceGatewayController(
            DeviceGatewayService service,
            OperatorContextResolver operatorContextResolver
    ) {
        this.service = service;
        this.operatorContextResolver = operatorContextResolver;
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
        return ApiResponse.ok(service.consume(command, operatorContextResolver.resolve(request)));
    }

    @GetMapping("/consumptions")
    public ApiResponse<List<ReadingConsumption>> recentConsumptions(@RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(service.recentConsumptions(limit));
    }
}
