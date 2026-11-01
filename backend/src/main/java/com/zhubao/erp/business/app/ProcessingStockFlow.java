package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.*;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.*;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

/** 加工实物的数量账与袋作业共用工作单元；复称是新测量事实，不推定损耗重量。 */
@Service
public class ProcessingStockFlow {
    private final DeliveryStockPlaceLookup places; private final StockLedgerService stock; private final BusinessDocumentRepository documents;
    public ProcessingStockFlow(DeliveryStockPlaceLookup places,StockLedgerService stock,BusinessDocumentRepository documents) { this.places=places; this.stock=stock; this.documents=documents; }
    public DeliveryStockPlace place(String target) { return places.place(BusinessTargetReference.parse(target)).orElseThrow(() -> new BusinessException("加工目标不存在")); }
    public void lock(String target,WorkSession session) { documents.lockMotherOrder(place(target).motherOrderCode(),session); }
    public StockDimension dimension(String target,StockStatus status) {
        var place=place(target);
        return new StockDimension(place.ownerType(),place.ownerCustomerId(),place.customerId(),place.motherOrderCode(),place.workOrderCode(),place.materialCode(),place.warehouseCode(),status);
    }
    public void start(String target,BigDecimal quantity,StockMeasurement.Weight weight,WorkSession session) {
        var wip=dimension(target,StockStatus.WIP); var existing=stock.balance(wip,session);
        if (existing.quantity().compareTo(quantity)>=0) return;
        if (existing.quantity().signum()>0) throw new BusinessException("在制库存已分批，请先核对原分配");
        if (target.startsWith("ZD-")) throw new BusinessException("子单缺少真实拆单在制库存");
        var original=documents.findMotherOrderByCode(wip.motherOrderCode()).orElseThrow(() -> new BusinessException("原收货不存在"));
        var raw=stock.lockedLedgersForMother(wip.motherOrderCode(),session).stream().filter(row -> row.code().equals(original.ledgerCode()))
                .map(StockLedgerEntry::to).filter(java.util.Objects::nonNull).filter(to -> to.stockStatus()==StockStatus.RAW_WAITING)
                .findFirst().orElseThrow(() -> new BusinessException("原收货库存来源待核，不能重复开工"));
        var balance=stock.balance(raw,session);
        BigDecimal actual=weight.state()==StockMeasurement.WeightState.KNOWN ? weight.value() : null;
        if (actual==null && !balance.weightPending() && balance.quantity().compareTo(quantity)==0 && balance.weight().signum()>0) actual=balance.weight();
        if (actual!=null && actual.signum()>0) stock.transfer(new StockTransferCommand("production_start",target,raw,wip,quantity,actual,"按实际来源开工"),session);
        else stock.postUnweighedMovement("production_start",target,raw,wip,quantity,"开工数量已到，重量待核",session);
    }
    public String dispose(QuantityDispositionCommand command,ActivityBag bag,String step,BigDecimal loss,BigDecimal scrap,BigDecimal returned,WorkSession session) {
        BigDecimal total=loss.add(scrap).add(returned); if (total.signum()==0) return "";
        var from=dimension(bag.targetCode(),StockStatus.WIP);
        if (!from.materialCode().equals(bag.materialCode())) throw new BusinessException("加工目标材质已变化，请核对原袋来源");
        if (command.stockFrom()!=null && !from.equals(command.stockFrom())) throw new BusinessException("退出库存必须属于本袋的真实在制来源");
        boolean known=command.exitWeight()!=null && command.exitWeight().state()==StockMeasurement.WeightState.KNOWN;
        int kinds=(loss.signum()>0?1:0)+(scrap.signum()>0?1:0)+(returned.signum()>0?1:0);
        if (known && kinds>1) throw new BusinessException("请分别提交损耗、报废和退回");
        if (known) ensureMeasured(from,total,command.exitWeight().value(),bag.id(),session);
        if (loss.signum()>0) exit("process_loss",bag.id(),from,null,loss,known?command.exitWeight().value():null,step,session);
        if (returned.signum()>0) exit("process_return",bag.id(),from,null,returned,known?command.exitWeight().value():null,step,session);
        if (scrap.signum()>0) exit("process_scrap",bag.id(),from,dimension(bag.targetCode(),StockStatus.SCRAP),scrap,known?command.exitWeight().value():null,step,session);
        return kinds==1 ? loss.signum()>0?"process_loss":scrap.signum()>0?"process_scrap":"process_return" : "process_disposition";
    }
    private void exit(String type,String id,StockDimension from,StockDimension to,BigDecimal quantity,BigDecimal weight,String step,WorkSession session) {
        if (weight==null) stock.postUnweighedMovement(type,id,from,to,quantity,step+"：实物数量已退出，重量待核",session);
        else if (to==null) stock.postProcessConsumption(type,id,from,quantity,weight,step+"：实际退出",session);
        else stock.transfer(new StockTransferCommand(type,id,from,to,quantity,weight,step+"：实际报废"),session);
    }
    public void finish(ActivityBag bag,WorkSession session) {
        var from=dimension(bag.targetCode(),StockStatus.WIP); var place=place(bag.targetCode());
        if (!from.materialCode().equals(bag.materialCode())) throw new BusinessException("加工目标材质已变化，请核对原袋来源");
        var posted=stock.lockedLedgersForMother(place.motherOrderCode(),session).stream().filter(row -> "production_finished".equals(row.documentType()) && bag.id().equals(row.documentCode())).findFirst().orElse(null);
        if (posted!=null) {
            if (posted.quantity().compareTo(bag.quantity())!=0 || posted.weight().compareTo(bag.weight().value())!=0) throw new BusinessException("本袋已转成品，请先核对原称重记录");
            return;
        }
        ensureMeasured(from,bag.quantity(),bag.weight().value(),bag.id(),session);
        stock.transfer(new StockTransferCommand("production_finished",bag.id(),from,dimension(bag.targetCode(),StockStatus.FINISHED),bag.quantity(),bag.weight().value(),"全部工序及质量已核对，按本袋实称转成品"),session);
    }
    private void ensureMeasured(StockDimension from,BigDecimal quantity,BigDecimal actual,String bagId,WorkSession session) {
        var balance=stock.balance(from,session);
        if (balance.quantity().compareTo(quantity)<0) throw new BusinessException("真实在制数量不足");
        if (actual==null || actual.signum()<=0) throw new BusinessException("请先记录本份额的实际重量");
        if (!balance.weightPending() && balance.quantity().compareTo(quantity)!=0) return;
        // 只补已实称份额；剩余份额仍未知。最后一份以实称重核正原账面，不把差额命名为损耗。
        BigDecimal corrected=balance.quantity().compareTo(quantity)==0 ? actual : balance.weight().max(actual);
        BigDecimal delta=corrected.subtract(balance.weight());
        if (delta.signum()>0) stock.transfer(new StockTransferCommand("stock_reweigh",bagId,null,from,BigDecimal.ZERO,delta,"实际复称，原账面保留在历史流水"),session);
        else if (delta.signum()<0) stock.depart(new StockTransferCommand("stock_reweigh",bagId,from,null,BigDecimal.ZERO,delta.negate(),"实际复称，原账面保留在历史流水"),null,session);
    }
}
