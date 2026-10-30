package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.ActivityBagRepository;
import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.business.domain.NodePortionBalance;
import com.zhubao.erp.business.domain.NodePortionReader;
import com.zhubao.erp.business.domain.NodePortionWriter;
import com.zhubao.erp.business.domain.ProcessAvailableQuantity;
import com.zhubao.erp.business.domain.QuantityDispositionCommand;
import com.zhubao.erp.business.domain.QuantityDispositionLine;
import com.zhubao.erp.business.domain.QuantityDispositionRepository;
import com.zhubao.erp.business.domain.QuantityDispositionResult;
import com.zhubao.erp.business.domain.RouteSnapshot;
import com.zhubao.erp.business.domain.RouteSnapshotRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.domain.DocumentManagementRepository;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.document.domain.EvidenceCodes;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 部分交出和退出数量。交出流到下道，损耗、报废、退回分开记，待核不算损耗。
 * 上道还有货时，本道处理完已到数量也不关闭整道。未知重量不按 0 写入库存，也不产生工资。
 */
@Service
public class QuantityDispositionService {

    private final ActivityBagRepository bags;
    private final ActivityBagService bagService;
    private final RouteSnapshotRepository routes;
    private final ProcessAvailabilityQuery availability;
    private final NodePortionReader portionReader;
    private final NodePortionWriter portions;
    private final QuantityDispositionRepository dispositions;
    private final DocumentManagementRepository documents;
    private final StockLedgerService stocks;
    private final BusinessWorkUnit workUnit;

    public QuantityDispositionService(ActivityBagRepository bags, ActivityBagService bagService,
                                      RouteSnapshotRepository routes, ProcessAvailabilityQuery availability,
                                      NodePortionReader portionReader, NodePortionWriter portions,
                                      QuantityDispositionRepository dispositions,
                                      DocumentManagementRepository documents, StockLedgerService stocks,
                                      BusinessWorkUnit workUnit) {
        this.bags = bags;
        this.bagService = bagService;
        this.routes = routes;
        this.availability = availability;
        this.portionReader = portionReader;
        this.portions = portions;
        this.dispositions = dispositions;
        this.documents = documents;
        this.stocks = stocks;
        this.workUnit = workUnit;
    }

    public QuantityDispositionResult dispose(QuantityDispositionCommand command, OperatorContext actor) {
        if (actor == null || actor.operator() == null || actor.operator().isBlank()) {
            throw new BusinessException("请先登录后再报工");
        }
        if (actor.dataScope() == DataScope.CUSTOMER_RANGE && actor.customerScope().isEmpty()) {
            throw new PermissionDeniedException("客户范围为空，不能报工");
        }
        if (command == null || command.requestId() == null || command.requestId().isBlank()) {
            throw new BusinessException("交工必须提供稳定请求编号");
        }
        BigDecimal handoff = nonNegative(command.handoff(), "交出");
        BigDecimal loss = nonNegative(command.loss(), "损耗");
        BigDecimal scrap = nonNegative(command.scrap(), "报废");
        BigDecimal returned = nonNegative(command.returned(), "退回");
        BigDecimal pending = nonNegative(command.pending(), "待核");
        BigDecimal used = handoff.add(loss).add(scrap).add(returned).add(pending);
        if (used.signum() == 0) {
            throw new BusinessException("请填写本次处置数量");
        }
        ActivityBag bag = bags.find(text(command.bagId())).orElseThrow(() -> new BusinessException("袋不存在"));
        BusinessTargetReference reference = BusinessTargetReference.parse(bag.targetCode());
        if (reference.kind() != BusinessKind.PROCESSING) {
            throw new BusinessException("普通加工报工只能用于母单或子单");
        }
        String payload = String.join("|", bag.id(), Integer.toString(command.bagVersion()),
                handoff.toPlainString(), loss.toPlainString(), scrap.toPlainString(), returned.toPlainString(),
                pending.toPlainString(), text(command.attachmentCode()));
        String text = workUnit.execute(new CommandCall(
                command.requestId(), actor.operator().trim(), "QUANTITY_DISPOSITION", "BAG", bag.id(), payload
        ), session -> write(execute(command, actor, bag.id(), handoff, loss, scrap, returned, pending, session)));
        return read(text);
    }

    private QuantityDispositionResult execute(QuantityDispositionCommand command, OperatorContext actor, String bagId,
                                              BigDecimal handoff, BigDecimal loss, BigDecimal scrap,
                                              BigDecimal returned, BigDecimal pending, WorkSession session) {
        ActivityBag current = bags.find(bagId).orElseThrow(() -> new BusinessException("袋不存在"));
        if (current.version() != command.bagVersion()) {
            throw new BusinessException("袋已被其他人修改，请刷新后重试");
        }
        ProcessAvailableQuantity available = availability.lookup(current.id(), actor);
        if (available.blockingReasons().contains("缺少生产报工权限")) {
            throw new PermissionDeniedException("缺少生产报工权限");
        }
        if (!available.actionable()) {
            throw new BusinessException(available.blockingReasons().isEmpty() ? "当前没有可处理数量" : available.blockingReasons().get(0));
        }
        BigDecimal used = handoff.add(loss).add(scrap).add(returned).add(pending);
        if (used.compareTo(available.availableQuantity()) > 0) {
            throw new BusinessException("处置数量超过当前可处理量");
        }
        assertPhoto(documents.findAttachmentByCode(text(command.attachmentCode())).orElse(null), current.targetCode());
        RouteSnapshot route = confirmed(current.targetCode());
        int stepIndex = route.stepIds().indexOf(available.stepId());
        BigDecimal unprocessed = available.availableQuantity().subtract(used);
        String remainderId = "";
        String handoffId = "";
        String pendingId = "";
        if (unprocessed.signum() > 0) {
            ActivityBag child = openChild(current, unprocessed, session);
            place(child, route, stepIndex, unprocessed, session);
            remainderId = child.id();
        }
        if (pending.signum() > 0) {
            ActivityBag child = openChild(current, pending, session);
            place(child, route, stepIndex, pending, session);
            dispositions.save(new QuantityDispositionLine(
                    "QD" + UUID.randomUUID().toString().replace("-", ""), command.requestId().trim() + "-P",
                    child.id(), available.stepId(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, pending), session);
            pendingId = child.id();
        }
        if (handoff.signum() > 0) {
            if (stepIndex + 1 >= route.stepIds().size()) {
                ActivityBag child = openChild(current, handoff, session);
                placeFinished(child, route, handoff, session);
                handoffId = child.id();
            } else {
                ActivityBag child = openChild(current, handoff, session);
                place(child, route, stepIndex + 1, handoff, session);
                handoffId = child.id();
            }
        }
        bagService.close(current.id(), session);
        String stockType = postStock(command, current, available.stepId(), loss, scrap, returned, session);
        boolean weightPending = loss.add(scrap).add(returned).signum() > 0 && !knownWeight(command.exitWeight());
        dispositions.save(new QuantityDispositionLine(
                "QD" + UUID.randomUUID().toString().replace("-", ""), command.requestId().trim(),
                current.id(), available.stepId(), handoff, loss, scrap, returned, pending), session);
        boolean earlierOpen = earlierOpen(current.targetCode(), route, stepIndex);
        boolean hereOpen = hereOpen(current.targetCode(), route, stepIndex);
        boolean stepClosed = !earlierOpen && !hereOpen;
        boolean arrivedFinished = unprocessed.signum() == 0 && pending.signum() == 0 && !stepClosed;
        return new QuantityDispositionResult(
                command.requestId().trim(), current.id(), available.stepId(), handoff, loss, scrap, returned, pending,
                unprocessed, handoff.signum() > 0, arrivedFinished, stepClosed, weightPending, stockType,
                remainderId, handoffId, pendingId);
    }

    private String postStock(QuantityDispositionCommand command, ActivityBag bag, String stepId,
                             BigDecimal loss, BigDecimal scrap, BigDecimal returned, WorkSession session) {
        BigDecimal exits = loss.add(scrap).add(returned);
        if (exits.signum() == 0) {
            return "";
        }
        if (!knownWeight(command.exitWeight())) {
            return "";
        }
        int kinds = (loss.signum() > 0 ? 1 : 0) + (scrap.signum() > 0 ? 1 : 0) + (returned.signum() > 0 ? 1 : 0);
        if (kinds > 1) {
            throw new BusinessException("请分别提交损耗、报废和退回");
        }
        if (command.stockFrom() == null) {
            throw new BusinessException("已称重的退出数量必须指定在制库存");
        }
        BigDecimal weight = command.exitWeight().value();
        if (scrap.signum() > 0) {
            StockDimension to = new StockDimension(
                    command.stockFrom().ownerType(), command.stockFrom().ownerCustomerId(), command.stockFrom().customerId(),
                    command.stockFrom().motherOrderCode(), command.stockFrom().workOrderCode(),
                    command.stockFrom().materialCode(), command.stockFrom().warehouseCode(), StockStatus.SCRAP);
            stocks.transfer(new StockTransferCommand(
                    "process_scrap", bag.id(), command.stockFrom(), to, scrap, weight, "实物报废：" + stepId), session);
            return "process_scrap";
        }
        String type = loss.signum() > 0 ? "process_loss" : "process_return";
        BigDecimal quantity = loss.signum() > 0 ? loss : returned;
        stocks.postProcessConsumption(type, bag.id(), command.stockFrom(), quantity, weight, type + "：" + stepId, session);
        return type;
    }

    private boolean earlierOpen(String targetCode, RouteSnapshot route, int stepIndex) {
        for (ActivityBag bag : bagService.activeLeaves(targetCode)) {
            if (position(bag, route) < stepIndex) {
                return true;
            }
        }
        return false;
    }

    private boolean hereOpen(String targetCode, RouteSnapshot route, int stepIndex) {
        for (ActivityBag bag : bagService.activeLeaves(targetCode)) {
            if (position(bag, route) == stepIndex) {
                return true;
            }
        }
        return false;
    }

    private int position(ActivityBag bag, RouteSnapshot route) {
        for (int index = 0; index < route.stepIds().size(); index++) {
            String stepId = route.stepIds().get(index);
            if (remainingAt(bag, route, index).signum() > 0 || dispositions.pending(bag.id(), stepId).signum() > 0) {
                return index;
            }
        }
        return route.stepIds().size();
    }

    private BigDecimal remainingAt(ActivityBag bag, RouteSnapshot route, int index) {
        String stepId = route.stepIds().get(index);
        NodePortionBalance balance = portionReader.find(bag.id(), stepId).orElse(null);
        BigDecimal arrived;
        BigDecimal handled;
        if (balance != null) {
            arrived = balance.arrived();
            handled = balance.handled();
        } else if (index == 0) {
            arrived = bag.quantity();
            handled = BigDecimal.ZERO;
        } else {
            arrived = BigDecimal.ZERO;
            handled = BigDecimal.ZERO;
        }
        return arrived.subtract(handled).subtract(dispositions.pending(bag.id(), stepId));
    }

    private void place(ActivityBag child, RouteSnapshot route, int atIndex, BigDecimal quantity, WorkSession session) {
        for (int index = 0; index < atIndex; index++) {
            String stepId = route.stepIds().get(index);
            portions.save(new NodePortionBalance(child.id(), stepId, quantity, quantity), session);
        }
        portions.save(new NodePortionBalance(child.id(), route.stepIds().get(atIndex), quantity, BigDecimal.ZERO), session);
    }

    private void placeFinished(ActivityBag child, RouteSnapshot route, BigDecimal quantity, WorkSession session) {
        for (String stepId : route.stepIds()) {
            portions.save(new NodePortionBalance(child.id(), stepId, quantity, quantity), session);
        }
    }

    private ActivityBag openChild(ActivityBag parent, BigDecimal quantity, WorkSession session) {
        return bagService.open(parent.targetCode(), parent.materialCode(), parent.requirementVersion(),
                parent.sourceKey(), quantity, StockMeasurement.unknown(), session);
    }

    private RouteSnapshot confirmed(String targetCode) {
        BusinessKind kind = BusinessTargetReference.parse(targetCode).kind();
        return routes.list(kind, targetCode).stream().filter(RouteSnapshot::confirmed)
                .max(java.util.Comparator.comparingInt(RouteSnapshot::version))
                .orElseThrow(() -> new BusinessException("路线尚未确认"));
    }

    private void assertPhoto(EvidenceAttachment attachment, String targetCode) {
        if (attachment == null || attachment.status() == null || attachment.status().isBlank()
                || "已下线".equals(attachment.status()) || "待核对".equals(attachment.status())) {
            throw new BusinessException("没有有效的现场照片");
        }
        if (attachment.objectType() == null || attachment.objectType().isBlank()
                || EvidenceCodes.HISTORICAL.equals(attachment.objectType())) {
            throw new BusinessException("历史照片不能当作本次拍摄");
        }
        if (!targetCode.equals(attachment.businessCode())) {
            throw new BusinessException("照片不属于当前加工目标");
        }
        String media = attachment.mediaType() == null ? "" : attachment.mediaType().toLowerCase(Locale.ROOT);
        if (!"image/jpeg".equals(media) && !"image/png".equals(media)) {
            throw new BusinessException("报工照片必须是可用图片");
        }
    }

    private static boolean knownWeight(StockMeasurement.Weight weight) {
        return weight != null && weight.state() == StockMeasurement.WeightState.KNOWN
                && weight.value() != null && weight.value().signum() > 0;
    }

    private static BigDecimal nonNegative(BigDecimal value, String label) {
        BigDecimal number = value == null ? BigDecimal.ZERO : value;
        if (number.signum() < 0) {
            throw new BusinessException(label + "数量不能为负");
        }
        return number;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String write(QuantityDispositionResult result) {
        return String.join("|",
                result.dispositionId(), result.bagId(), result.stepId(),
                result.handoff().toPlainString(), result.loss().toPlainString(), result.scrap().toPlainString(),
                result.returned().toPlainString(), result.pending().toPlainString(), result.unprocessed().toPlainString(),
                result.batchHanded() ? "1" : "0", result.arrivedPortionFinished() ? "1" : "0",
                result.stepClosed() ? "1" : "0", result.weightPending() ? "1" : "0", result.stockDocumentType(),
                result.remainderBagId(), result.handoffBagId(), result.pendingBagId());
    }

    private static QuantityDispositionResult read(String text) {
        String[] parts = text.split("\\|", -1);
        if (parts.length != 17) {
            throw new BusinessException("数量处置结果无法识别");
        }
        return new QuantityDispositionResult(
                parts[0], parts[1], parts[2], new BigDecimal(parts[3]), new BigDecimal(parts[4]),
                new BigDecimal(parts[5]), new BigDecimal(parts[6]), new BigDecimal(parts[7]), new BigDecimal(parts[8]),
                "1".equals(parts[9]), "1".equals(parts[10]), "1".equals(parts[11]), "1".equals(parts[12]),
                parts[13], parts[14], parts[15], parts[16]);
    }
}
