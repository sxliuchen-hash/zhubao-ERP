package com.zhubao.erp.business.web;

import com.zhubao.erp.business.app.BusinessDataAccess;
import com.zhubao.erp.business.app.PortionInspectionService;
import com.zhubao.erp.business.app.PortionReleaseService;
import com.zhubao.erp.business.app.ProcessAvailabilityQuery;
import com.zhubao.erp.business.app.QuantityDispositionService;
import com.zhubao.erp.business.app.ReworkPortionService;
import com.zhubao.erp.business.app.WeighingService;
import com.zhubao.erp.business.app.WholeBagReportService;
import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.ActivityBagRepository;
import com.zhubao.erp.business.domain.BagWeightFact;
import com.zhubao.erp.business.domain.PortionInspection;
import com.zhubao.erp.business.domain.PortionRelease;
import com.zhubao.erp.business.domain.ProcessAvailableQuantity;
import com.zhubao.erp.business.domain.QuantityDispositionCommand;
import com.zhubao.erp.business.domain.QuantityDispositionResult;
import com.zhubao.erp.business.domain.ReworkTask;
import com.zhubao.erp.business.domain.ShippableView;
import com.zhubao.erp.business.domain.WholeBagReport;
import com.zhubao.erp.business.domain.WholeBagReportCommand;
import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.error.PermissionDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 已实现的加工质量命令入口。只转调现有服务，不另写一套数量或库存规则。
 */
@RestController
@RequestMapping("/api/business/production-quality")
public class ProductionQualityController {

    private final ProcessAvailabilityQuery availability;
    private final WholeBagReportService wholeBagReports;
    private final QuantityDispositionService dispositions;
    private final WeighingService weighing;
    private final PortionInspectionService inspections;
    private final PortionReleaseService releases;
    private final ReworkPortionService rework;
    private final OperatorContextResolver actors;
    private final ActivityBagRepository bags;
    private final BusinessDataAccess access;

    public ProductionQualityController(ProcessAvailabilityQuery availability, WholeBagReportService wholeBagReports,
                                       QuantityDispositionService dispositions, WeighingService weighing,
                                       PortionInspectionService inspections, PortionReleaseService releases,
                                       ReworkPortionService rework, OperatorContextResolver actors) {
        this(availability, wholeBagReports, dispositions, weighing, inspections, releases, rework, actors, null, null);
    }

    @Autowired
    public ProductionQualityController(ProcessAvailabilityQuery availability, WholeBagReportService wholeBagReports,
                                       QuantityDispositionService dispositions, WeighingService weighing,
                                       PortionInspectionService inspections, PortionReleaseService releases,
                                       ReworkPortionService rework, OperatorContextResolver actors,
                                       ActivityBagRepository bags, BusinessDataAccess access) {
        this.availability = availability;
        this.wholeBagReports = wholeBagReports;
        this.dispositions = dispositions;
        this.weighing = weighing;
        this.inspections = inspections;
        this.releases = releases;
        this.rework = rework;
        this.actors = actors;
        this.bags = bags;
        this.access = access;
    }

    @GetMapping("/bags/{bagId}/availability")
    public ApiResponse<ProcessAvailableQuantity> availability(@PathVariable String bagId, HttpServletRequest request) {
        return ApiResponse.ok(availability.lookup(bagId, actorForBag(request, bagId)));
    }

    @PostMapping("/whole-bag-reports")
    public ApiResponse<WholeBagReport> report(@RequestBody WholeBagReportCommand command, HttpServletRequest request) {
        return ApiResponse.ok(wholeBagReports.report(command, actorForBag(request, command.bagId())));
    }

    @PostMapping("/dispositions")
    public ApiResponse<QuantityDispositionResult> dispose(@RequestBody QuantityDispositionCommand command, HttpServletRequest request) {
        return ApiResponse.ok(dispositions.dispose(command, actorForBag(request, command.bagId())));
    }

    @PostMapping("/weights")
    public ApiResponse<BagWeightFact> weigh(@RequestBody WeightSupplementRequest command, HttpServletRequest request) {
        return ApiResponse.ok(weighing.supplement(
                command.requestId(), command.bagId(), command.bagVersion(), command.weight(), actorForBag(request, command.bagId())));
    }

    @PostMapping("/inspections")
    public ApiResponse<PortionInspection> inspect(@RequestBody InspectionRequest command, HttpServletRequest request) {
        return ApiResponse.ok(inspections.record(
                command.requestId(), command.bagId(), command.bagVersion(), command.requirementVersion(),
                command.pass(), command.rework(), command.scrap(), command.pending(), command.failedClauses(), actorForBag(request, command.bagId())));
    }

    @PostMapping("/releases")
    public ApiResponse<PortionRelease> release(@RequestBody HoldRequest command, HttpServletRequest request) {
        return ApiResponse.ok(releases.release(command.requestId(), command.bagId(), command.bagVersion(), command.quantity(), actorForBag(request, command.bagId())));
    }

    @PostMapping("/freezes")
    public ApiResponse<PortionRelease> freeze(@RequestBody HoldRequest command, HttpServletRequest request) {
        return ApiResponse.ok(releases.freeze(command.requestId(), command.bagId(), command.bagVersion(), command.quantity(), actorForBag(request, command.bagId())));
    }

    @PostMapping("/unfreezes")
    public ApiResponse<PortionRelease> unfreeze(@RequestBody HoldRequest command,HttpServletRequest request) {
        return ApiResponse.ok(releases.unfreeze(command.requestId(),command.bagId(),command.bagVersion(),command.quantity(),actorForBag(request,command.bagId())));
    }

    @GetMapping("/bags/{bagId}/shippable")
    public ApiResponse<ShippableView> shippable(@PathVariable String bagId, @RequestParam(defaultValue = "IN_HOUSE") String location,
                                                HttpServletRequest request) {
        actorForBag(request, bagId);
        PortionReleaseService.Location place = "OUTSOURCING".equals(location)
                ? PortionReleaseService.Location.OUTSOURCING : PortionReleaseService.Location.IN_HOUSE;
        return ApiResponse.ok(releases.shippable(bagId, place));
    }

    @PostMapping("/reworks")
    public ApiResponse<ReworkTask> rework(@RequestBody ReworkRequest command, HttpServletRequest request) {
        return ApiResponse.ok(rework.open(command.requestId(), command.bagId(), command.bagVersion(), command.round(), actorForBag(request, command.bagId())));
    }

    private OperatorContext actor(HttpServletRequest request) {
        OperatorContext actor = actors.resolve(request);
        if (actor.dataScope() == DataScope.CUSTOMER_RANGE && actor.customerScope().isEmpty()) {
            throw new PermissionDeniedException("客户范围为空，不能报工");
        }
        return actor;
    }

    private OperatorContext actorForBag(HttpServletRequest request, String bagId) {
        OperatorContext actor = actor(request);
        if (bags == null || access == null) {
            return actor;
        }
        ActivityBag bag = bags.find(bagId == null ? "" : bagId.trim())
                .orElseThrow(() -> new BusinessException("袋不存在"));
        access.assertCanRead(actor, bag.targetCode());
        return actor;
    }

    public record WeightSupplementRequest(String requestId, String bagId, int bagVersion, BigDecimal weight) {
    }

    public record InspectionRequest(String requestId, String bagId, int bagVersion, String requirementVersion,
                                    BigDecimal pass, BigDecimal rework, BigDecimal scrap, BigDecimal pending,
                                    List<String> failedClauses) {
    }

    public record HoldRequest(String requestId, String bagId, int bagVersion, BigDecimal quantity) {
    }

    public record ReworkRequest(String requestId, String bagId, int bagVersion, int round) {
    }
}
