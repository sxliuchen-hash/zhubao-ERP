package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.*;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;

/** 把原收货来源接到实际袋、确认路线和库存；查询不创建执行事实。 */
@Service
public class ProductionExecutionService {
    private final BusinessDocumentRepository documents; private final ActivityBagRepository bags; private final ActivityBagService bagService;
    private final RouteSnapshotRepository routes; private final ProcessAvailabilityQuery availability; private final PortionReleaseService releases;
    private final PortionInspectionRepository inspections; private final PortionReleaseRepository holds; private final WeighingService weighing;
    private final NodePortionReader portions; private final QuantityDispositionRepository dispositions; private final DeliveryDraftRepository deliveries;
    private final DeliveryInHouseCheck inHouse; private final ProcessingStockFlow stock; private final BusinessWorkUnit unit;
    private final InitialBagAllocationService allocation;
    public ProductionExecutionService(BusinessDocumentRepository documents,ActivityBagRepository bags,ActivityBagService bagService,
            RouteSnapshotRepository routes,ProcessAvailabilityQuery availability,PortionReleaseService releases,PortionInspectionRepository inspections,
            PortionReleaseRepository holds,WeighingService weighing,NodePortionReader portions,QuantityDispositionRepository dispositions,
            DeliveryDraftRepository deliveries,DeliveryInHouseCheck inHouse,ProcessingStockFlow stock,BusinessWorkUnit unit) {
        this.documents=documents; this.bags=bags; this.bagService=bagService; this.routes=routes; this.availability=availability;
        this.releases=releases; this.inspections=inspections; this.holds=holds; this.weighing=weighing; this.portions=portions;
        this.dispositions=dispositions; this.deliveries=deliveries; this.inHouse=inHouse; this.stock=stock; this.unit=unit;
        this.allocation=new InitialBagAllocationService(bagService,this::sourceLimit,dispositions);
    }
    public View read(String target,OperatorContext actor) {
        Facts facts=facts(target,null); var route=route(target);
        List<ProcessStep> defaults;
        try { defaults=ProcessCatalog.defaults(BusinessKind.PROCESSING,facts.material()); }
        catch (BusinessException ex) { defaults=List.of(); }
        var active=bags.listByTarget(target).stream().filter(ActivityBag::activeLeaf).filter(bag -> !deliveries.bagConfirmed(bag.id()))
                .sorted(Comparator.comparing(ActivityBag::id)).map(bag -> new BagView(bag,availability.lookup(bag.id(),actor),shipping(bag,route))).toList();
        return new View(target,facts.customerId(),facts.quantityUnit(),facts.requirement(),defaults,route,active,!bags.listByTarget(target).isEmpty());
    }
    public BagDetail bag(String id) {
        ActivityBag bag=bags.find(id).orElseThrow(() -> new BusinessException("袋不存在"));
        return new BagDetail(bag,inspections.listByBag(id),holds.find(id).orElse(new PortionRelease(id,BigDecimal.ZERO,BigDecimal.ZERO)),weighing.current(id));
    }
    public View start(String request,OperatorContext actor,String target,List<String> selected) {
        List<String> steps=selected==null ? List.of() : List.copyOf(selected);
        if (steps.isEmpty() || new HashSet<>(steps).size()!=steps.size()) throw new BusinessException("请按实际顺序确认至少一道工序，不能重复");
        for (String step : steps) if (ProcessCatalog.require(step).kind()!=BusinessKind.PROCESSING) throw new BusinessException("普通加工不能混入打样或金工工序");
        unit.execute(new CommandCall(request,actor.username(),"START_PRODUCTION_EXECUTION","BUSINESS_TARGET",target,String.join(",",steps)),session -> {
            stock.lock(target,session); Facts facts=facts(target,session);
            if (!bags.listByTarget(target).isEmpty()) throw new BusinessException("本单已有执行袋，请使用现有袋办理");
            if (!documents.listProcessStationHandoffs(target).isEmpty() || !documents.listQualityRounds(target).isEmpty()
                    || documents.listOutboundOrders().stream().anyMatch(row -> target.equals(row.workOrderCode())))
                throw new BusinessException("本单已有旧版加工事实，请先核对接续，不能重复开工");
            if (facts.sources().size()!=1) throw new BusinessException("请先按材质、要求和来源拆分子单，再建立加工袋");
            RouteSnapshot current=route(target);
            if (current!=null && !current.stepIds().equals(steps)) throw new BusinessException("已有确认路线，不能在开工时覆盖");
            if (current==null) {
                int version=routes.list(BusinessKind.PROCESSING,target).stream().mapToInt(RouteSnapshot::version).max().orElse(0)+1;
                current=new RouteSnapshot("RS-"+UUID.randomUUID(),BusinessKind.PROCESSING,target,version,steps,true); routes.insert(current,session);
            }
            var source=facts.sources().get(0);
            stock.start(target,source.quantity(),source.weight(),session);
            allocation.arrange(target,List.of(new InitialBagAllocationService.SourceLine(source.sourceKey(),facts.material(),current.snapshotId(),source.quantity(),source.weight())),session);
            return target;
        });
        return read(target,actor);
    }
    public void finish(String request,OperatorContext actor,String id,int version) {
        var found=bags.find(id).orElseThrow(() -> new BusinessException("袋不存在"));
        unit.execute(new CommandCall(request,actor.username(),"FINISH_PRODUCTION_STOCK","BAG",id,id+"|"+version),session -> {
            stock.lock(found.targetCode(),session);
            var bag=bags.lock(id,session).orElseThrow(() -> new BusinessException("袋不存在"));
            if (bag.version()!=version) throw new BusinessException("袋已被其他人修改，请刷新后重试");
            if (!bag.activeLeaf() || deliveries.bagConfirmed(id)) throw new BusinessException("本袋已关闭或已发货");
            var view=shipping(bag,route(bag.targetCode()));
            if (!view.blockingReasons().isEmpty() || view.quantity().compareTo(bag.quantity())<0)
                throw new BusinessException(view.blockingReasons().isEmpty()?"请先把合格实物分成可发袋":view.blockingReasons().get(0));
            stock.finish(bag,session); return id;
        });
    }
    private ShippableView shipping(ActivityBag bag,RouteSnapshot route) {
        var view=releases.shippable(bag.id(),inHouse.inHouse(bag.id())?PortionReleaseService.Location.IN_HOUSE:PortionReleaseService.Location.OUTSOURCING);
        List<String> reasons=new ArrayList<>(view.blockingReasons());
        if (route==null) reasons.add("尚未确认加工路线");
        else for (String step : route.stepIds()) {
            var row=portions.find(bag.id(),step).orElse(null);
            if (row==null || row.arrived().compareTo(bag.quantity())<0 || row.remaining().signum()>0 || dispositions.pending(bag.id(),step).signum()>0) {
                reasons.add("工序尚未完整交工："+ProcessCatalog.require(step).name()); break;
            }
        }
        return new ShippableView(bag.id(),reasons.isEmpty()?view.quantity():BigDecimal.ZERO,List.copyOf(reasons));
    }
    private RouteSnapshot route(String target) { return routes.list(BusinessKind.PROCESSING,target).stream().filter(RouteSnapshot::confirmed).max(Comparator.comparingInt(RouteSnapshot::version)).orElse(null); }
    private Facts facts(String target,WorkSession session) {
        var child=documents.findChildWorkOrderByCode(target).orElse(null);
        if (child!=null) {
            var sources=documents.listChildAllocations(child.motherOrderCode(),session).stream().filter(row -> target.equals(row.childOrderCode()))
                    .map(row -> new Source(child.motherOrderCode()+"#"+row.sourceLineNo(),row.quantity(),weight(row.weight()))).toList();
            if (sources.isEmpty() && documents.listReceiptLines(child.motherOrderCode()).size()==1) sources=List.of(new Source(child.motherOrderCode()+"#"+documents.listReceiptLines(child.motherOrderCode()).get(0).lineNo(),child.quantity(),weight(child.weight())));
            return new Facts(child.customerId(),child.materialCode(),child.quantityUnit(),child.processingRequirement(),sources);
        }
        var mother=documents.findMotherOrderByCode(target).orElseThrow(() -> new BusinessException("加工目标不存在"));
        if (!documents.listChildWorkOrders(target).isEmpty()) throw new BusinessException("母单已有子单，请选择实际加工子单");
        var sources=documents.listReceiptLines(target).stream().map(row -> new Source(target+"#"+row.lineNo(),row.quantity(),weight(row.weight()))).toList();
        return new Facts(mother.customerId(),mother.materialCode(),mother.quantityUnit(),mother.processingRequirement(),sources);
    }
    private BigDecimal sourceLimit(String key) {
        int index=key.lastIndexOf('#'); if (index<1) return null;
        return documents.listReceiptLines(key.substring(0,index)).stream().filter(row -> key.equals(key.substring(0,index)+"#"+row.lineNo())).map(ReceiptLine::quantity).findFirst().orElse(null);
    }
    private static StockMeasurement.Weight weight(BigDecimal value) { return value==null || value.signum()<=0 ? StockMeasurement.unknown() : StockMeasurement.known(value); }
    private record Source(String sourceKey,BigDecimal quantity,StockMeasurement.Weight weight) {}
    private record Facts(Long customerId,String material,String quantityUnit,String requirement,List<Source> sources) {}
    public record BagView(ActivityBag bag,ProcessAvailableQuantity availability,ShippableView shippable) {}
    public record View(String targetCode,Long customerId,String quantityUnit,String requirement,List<ProcessStep> defaultSteps,RouteSnapshot route,List<BagView> bags,boolean hasExecution) {}
    public record BagDetail(ActivityBag bag,List<PortionInspection> inspections,PortionRelease hold,BagWeightFact weight) {}
}
