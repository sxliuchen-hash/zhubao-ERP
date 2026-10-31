package com.zhubao.erp.finance.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.finance.app.FixedAssetRepairService;
import com.zhubao.erp.finance.domain.FixedAssetRepairView;
import com.zhubao.erp.security.app.OperatorContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 资产维修登记和费用确认。付款不另记成本，维修不报废资产。
 */
@RestController
@RequestMapping("/api/finance/fixed-asset-repairs")
public class FixedAssetRepairController {

    private final FixedAssetRepairService repairs;
    private final OperatorContextResolver actors;

    public FixedAssetRepairController(FixedAssetRepairService repairs, OperatorContextResolver actors) {
        this.repairs = repairs;
        this.actors = actors;
    }

    @GetMapping
    public ApiResponse<List<FixedAssetRepairView>> list(@RequestParam String assetCode, HttpServletRequest request) {
        return ApiResponse.ok(repairs.list(actors.resolve(request), assetCode));
    }

    @PostMapping
    public ApiResponse<FixedAssetRepairView> record(@RequestBody RecordRequest body, HttpServletRequest request) {
        return ApiResponse.ok(repairs.record(
                body.requestId(), actors.resolve(request), body.assetCode(), body.repairedOn(),
                body.handoverName(), body.evidenceCode()));
    }

    @PostMapping("/cost-confirmations")
    public ApiResponse<FixedAssetRepairView> confirm(@RequestBody ConfirmRequest body, HttpServletRequest request) {
        return ApiResponse.ok(repairs.confirm(
                body.requestId(), actors.resolve(request), body.repairId(), body.amount(), body.evidenceCode()));
    }

    @PostMapping("/payments")
    public ApiResponse<FixedAssetRepairView> pay(@RequestBody PayRequest body, HttpServletRequest request) {
        return ApiResponse.ok(repairs.pay(
                body.requestId(), actors.resolve(request), body.repairId(), body.amount(),
                body.paidOn(), body.voucher()));
    }

    public record RecordRequest(String requestId, String assetCode, String repairedOn, String handoverName, String evidenceCode) {
    }

    public record ConfirmRequest(String requestId, String repairId, BigDecimal amount, String evidenceCode) {
    }

    public record PayRequest(String requestId, String repairId, BigDecimal amount, String paidOn, String voucher) {
    }
}
