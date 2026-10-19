package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.OrderAdjustment;
import com.zhubao.erp.business.domain.OrderRecycleMark;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.label.domain.PrintJob;
import com.zhubao.erp.label.domain.PrintJobRepository;
import com.zhubao.erp.label.domain.PrintJobStatus;
import com.zhubao.erp.label.domain.PrintRecycle;
import com.zhubao.erp.label.domain.PrintRoutingRepository;
import com.zhubao.erp.label.domain.PrintTargetJob;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 管理员改正或回收母单、子单和打印任务。已入账事实不能直接覆盖。
 * 回收后其他人看不到；库存流水和已入账账单保持原样。
 */
@Service
public class OrderMaintenanceService {
    private final BusinessDocumentRepository documents;
    private final PrintJobRepository printJobs;
    private final PrintRoutingRepository routing;
    private final PermissionGuard permissions;
    private final BusinessWorkUnit workUnit;

    public OrderMaintenanceService(BusinessDocumentRepository documents, PrintJobRepository printJobs,
                                   PrintRoutingRepository routing, PermissionGuard permissions) {
        this(documents, printJobs, routing, permissions, null);
    }

    @Autowired
    public OrderMaintenanceService(BusinessDocumentRepository documents, PrintJobRepository printJobs,
                                   PrintRoutingRepository routing, PermissionGuard permissions,
                                   BusinessWorkUnit workUnit) {
        this.documents = documents;
        this.printJobs = printJobs;
        this.routing = routing;
        this.permissions = permissions;
        this.workUnit = workUnit;
    }

    public List<MaintainedOrder> search(String code, OperatorContext operator) {
        requireAdmin(operator);
        String key = required(code, "请填写单号");
        List<MaintainedOrder> rows = new ArrayList<>();
        documents.findMotherOrderAny(key).ifPresent(mother -> {
            rows.add(toMother(mother));
            for (ChildWorkOrderRecord child : documents.listChildWorkOrdersAny(mother.code())) {
                rows.add(toChild(child));
                rows.addAll(printsFor(child.code()));
            }
            rows.addAll(printsFor(mother.code()));
        });
        if (!rows.isEmpty()) {
            return rows;
        }
        documents.findChildWorkOrderAny(key).ifPresent(child -> {
            documents.findMotherOrderAny(child.motherOrderCode()).ifPresent(mother -> rows.add(toMother(mother)));
            rows.add(toChild(child));
            rows.addAll(printsFor(child.code()));
        });
        if (!rows.isEmpty()) {
            return rows;
        }
        printJobs.findAny(key).ifPresent(job -> {
            attachBusiness(rows, job.businessCode());
            rows.add(toPrintJob(job));
        });
        routing.jobAny(key).ifPresent(job -> {
            routing.batch(job.batchId()).ifPresent(batch -> attachBusiness(rows, batch.businessCode()));
            rows.add(toTarget(job));
        });
        if (rows.isEmpty()) {
            throw new BusinessException("没有这张单据：" + key);
        }
        return rows;
    }

    public List<MaintainedOrder> recycleBin(OperatorContext operator) {
        requireAdmin(operator);
        List<MaintainedOrder> rows = new ArrayList<>();
        for (OrderRecycleMark mark : documents.listMotherRecycles()) {
            documents.findMotherOrderAny(mark.code()).ifPresent(mother -> rows.add(toMother(mother)));
        }
        for (OrderRecycleMark mark : documents.listChildRecycles()) {
            documents.findChildWorkOrderAny(mark.code()).ifPresent(child -> rows.add(toChild(child)));
        }
        for (PrintRecycle mark : printJobs.recycled()) {
            printJobs.findAny(mark.id()).ifPresent(job -> rows.add(toPrintJob(job)));
        }
        for (PrintRecycle mark : routing.recycledJobs()) {
            routing.jobAny(mark.id()).ifPresent(job -> rows.add(toTarget(job)));
        }
        return rows;
    }

    public MaintainedOrder correctMother(String code, OrderMaintenanceCommands.MotherCorrection command, OperatorContext operator) {
        requireAdmin(operator);
        String key = required(code, "请填写母单号");
        if (workUnit != null) {
            required(command.expectedRevision(), "请重新查找母单后再保存");
            workUnit.execute(new CommandCall(required(command.requestId(), "改单必须带请求编号"),
                    operator.operator(), "ADMIN_CORRECT_MOTHER", "MOTHER", key,
                    OrderMaintenanceRevision.of(payload(command.customerId(), command.customerName(), command.materialCode(), command.goodsName(),
                            command.quantity(), command.weight(), command.requirement(), command.dueDate(),
                            command.remark(), command.reason(), command.expectedRevision()))), session -> {
                applyMotherCorrection(key, command, operator, session);
                return key;
            });
            return toMother(documents.findMotherOrderAny(key).orElseThrow(() -> new BusinessException("母单不存在")));
        }
        return toMother(applyMotherCorrection(key, command, operator, null));
    }

    private MotherOrderRecord applyMotherCorrection(String code, OrderMaintenanceCommands.MotherCorrection command,
                                                     OperatorContext operator, WorkSession session) {
        if (session != null) {
            documents.lockMotherOrder(code, session);
        }
        MotherOrderRecord current = (session == null ? documents.findMotherOrderAny(code)
                : documents.findMotherOrderAny(code, session))
                .orElseThrow(() -> new BusinessException("母单不存在"));
        if (session != null && !OrderMaintenanceRevision.of(current).equals(command.expectedRevision())) {
            throw new BusinessException("母单已被修改，请重新查找后再保存");
        }
        String reason = requireReason(command.reason());
        BigDecimal quantity = requireQuantity(command.quantity());
        BigDecimal weight = requireWeight(command.weight());
        MotherOrderRecord next = new MotherOrderRecord(current.code(),
                command.customerId() == null ? current.customerId() : command.customerId(),
                current.ownerCustomerId(), required(command.materialCode(), "请填写材质"), current.inboundDate(),
                current.expressCompany(), current.courierNo(), current.senderName(), current.receiverName(),
                current.unpackSigner(), required(command.goodsName(), "请填写货品"), current.ownerType(),
                current.customerOwned(), current.warehouseCode(), quantity, current.quantityUnit(), weight,
                current.stockStatus(), current.ledgerCode(), current.sourceRegisterType(), current.sourceRegisterLineNo(),
                current.sourceRegisterSequence(), current.operator(), current.evidenceCode(), text(command.remark()),
                current.status(), current.createdAt(), text(command.requirement()), text(command.dueDate()),
                required(command.customerName(), "请填写客户"));
        assertPostedMotherFactsUnchanged(current, next);
        OrderAdjustment adjustment = adjustment("MOTHER", current.code(), motherText(current),
                motherText(next), reason, operator);
        if (session == null) {
            documents.correctMotherOrder(next, adjustment);
        } else {
            documents.correctMotherOrder(next, adjustment, session);
        }
        return next;
    }

    public MaintainedOrder correctChild(String code, OrderMaintenanceCommands.ChildCorrection command, OperatorContext operator) {
        requireAdmin(operator);
        String key = required(code, "请填写子单号");
        if (workUnit != null) {
            required(command.expectedRevision(), "请重新查找子单后再保存");
            workUnit.execute(new CommandCall(required(command.requestId(), "改单必须带请求编号"),
                    operator.operator(), "ADMIN_CORRECT_CHILD", "WORK_ORDER", key,
                    OrderMaintenanceRevision.of(payload(command.customerId(), command.customerName(), command.materialCode(), command.goodsName(),
                            command.quantity(), command.weight(), command.requirement(), command.dueDate(),
                            command.processes(), command.reason(), command.expectedRevision()))), session -> {
                applyChildCorrection(key, command, operator, session);
                return key;
            });
            return toChild(documents.findChildWorkOrderAny(key).orElseThrow(() -> new BusinessException("子单不存在")));
        }
        return toChild(applyChildCorrection(key, command, operator, null));
    }

    private ChildWorkOrderRecord applyChildCorrection(String code, OrderMaintenanceCommands.ChildCorrection command,
                                                       OperatorContext operator, WorkSession session) {
        if (session != null) {
            documents.lockChildWorkOrder(code, session);
        }
        ChildWorkOrderRecord current = (session == null ? documents.findChildWorkOrderAny(code)
                : documents.findChildWorkOrderAny(code, session))
                .orElseThrow(() -> new BusinessException("子单不存在"));
        if (session != null && !OrderMaintenanceRevision.of(current).equals(command.expectedRevision())) {
            throw new BusinessException("子单已被修改，请重新查找后再保存");
        }
        String reason = requireReason(command.reason());
        ChildWorkOrderRecord next = new ChildWorkOrderRecord(current.code(), current.motherOrderCode(),
                command.customerId() == null ? current.customerId() : command.customerId(),
                required(command.customerName(), "请填写客户"), text(command.requirement()), text(command.dueDate()),
                required(command.materialCode(), "请填写材质"), required(command.goodsName(), "请填写货品"),
                requireQuantity(command.quantity()), current.quantityUnit(), requireWeight(command.weight()),
                current.warehouseCode(), required(command.processes(), "请填写工序"), current.verificationCode(),
                current.ledgerCode(), current.operator(), current.createdAt());
        assertPostedChildFactsUnchanged(current, next);
        OrderAdjustment adjustment = adjustment("WORK_ORDER", current.code(), childText(current),
                childText(next), reason, operator);
        if (session == null) {
            documents.correctChildWorkOrder(next, adjustment);
        } else {
            documents.correctChildWorkOrder(next, adjustment, session);
        }
        return next;
    }

    public MaintainedOrder correctPrintJob(String id, OrderMaintenanceCommands.PrintCorrection command, OperatorContext operator) {
        requireAdmin(operator);
        String key = required(id, "请填写打印任务");
        if (workUnit == null) throw new BusinessException("打印改单工作单元尚未装配");
        String reason = requireReason(command.reason());
        int copies = requireCopies(command.copies());
        String expected = required(command.expectedRevision(), "请重新查找打印任务后再保存");
        workUnit.execute(new CommandCall(required(command.requestId(), "改单必须带请求编号"),
                operator.operator(), "ADMIN_CORRECT_PRINT_JOB", "PRINT_JOB", key,
                OrderMaintenanceRevision.of(payload(copies, command.stationId(), reason, expected))), session -> {
            PrintJob current = printJobs.lockAny(key, session)
                    .orElseThrow(() -> new BusinessException("打印任务不存在"));
            assertPrintRevision(current, expected);
            assertPending(current.status());
            if (!text(command.stationId()).equals(text(current.stationId()))) {
                throw new BusinessException("旧打印任务的工位是领取记录，不能直接改目标；请核纸后新建打印批次");
            }
            PrintJob next = printJobs.replaceCopies(key, copies, current.stationId(), now(), session);
            documents.saveOrderAdjustment(adjustment("PRINT_JOB", key,
                    current.copies() + "份 " + current.stationId(),
                    next.copies() + "份 " + next.stationId(), reason, operator), session);
            return key;
        });
        return toPrintJob(printJobs.findAny(key).orElseThrow(() -> new BusinessException("打印任务不存在")));
    }

    public MaintainedOrder correctPrintTarget(String id, OrderMaintenanceCommands.PrintCorrection command, OperatorContext operator) {
        requireAdmin(operator);
        String key = required(id, "请填写打印任务");
        if (workUnit == null) throw new BusinessException("打印改单工作单元尚未装配");
        String reason = requireReason(command.reason());
        int copies = requireCopies(command.copies());
        String station = required(command.stationId(), "请填写工位");
        String queue = required(command.queueId(), "请填写队列");
        String expected = required(command.expectedRevision(), "请重新查找打印任务后再保存");
        workUnit.execute(new CommandCall(required(command.requestId(), "改单必须带请求编号"),
                operator.operator(), "ADMIN_CORRECT_PRINT_TARGET", "PRINT_TARGET", key,
                OrderMaintenanceRevision.of(payload(copies, station, queue, reason, expected))), session -> {
            PrintTargetJob current = routing.lockJobAny(key, session)
                    .orElseThrow(() -> new BusinessException("打印任务不存在"));
            assertPrintRevision(current, expected);
            assertPending(current.status());
            if (!station.equals(current.targetStationId()) || !queue.equals(current.targetQueueId())) {
                throw new BusinessException("目标工位和队列不能原地覆盖，请使用打印批次改派并保留原任务关联");
            }
            PrintTargetJob next = routing.replaceTarget(key, copies, station, queue, now(), session);
            documents.saveOrderAdjustment(adjustment("PRINT_TARGET", key,
                    current.copies() + "份 " + current.targetStationId() + "/" + current.targetQueueId(),
                    next.copies() + "份 " + next.targetStationId() + "/" + next.targetQueueId(), reason, operator), session);
            return key;
        });
        return toTarget(routing.jobAny(key).orElseThrow(() -> new BusinessException("打印任务不存在")));
    }

    public void recycle(OrderMaintenanceCommands.RecycleCommand command, OperatorContext operator) {
        requireAdmin(operator);
        String type = required(command.targetType(), "请选择单据类型");
        String code = required(command.code(), "请填写单号");
        String reason = requireReason(command.reason());
        String at = now();
        String batch = UUID.randomUUID().toString();
        switch (type) {
            case "MOTHER" -> {
                if (workUnit == null) {
                    recycleMother(code, operator.operator(), at, batch, reason);
                } else {
                    workUnit.execute(new CommandCall(required(command.requestId(), "回收必须带请求编号"),
                            operator.operator(), "ADMIN_RECYCLE_MOTHER", type, code,
                            OrderMaintenanceRevision.of(payload(reason))), session -> {
                        recycleMother(code, operator.operator(), at, batch, reason, session);
                        return code;
                    });
                }
            }
            case "WORK_ORDER" -> {
                if (workUnit == null) {
                    ChildWorkOrderRecord child = documents.findChildWorkOrderAny(code).orElseThrow(() -> new BusinessException("子单不存在"));
                    documents.recycleChild(code, operator.operator(), at, batch, reason);
                    hidePrints(child.code(), operator.operator(), at, batch, reason);
                } else {
                    workUnit.execute(new CommandCall(required(command.requestId(), "回收必须带请求编号"),
                            operator.operator(), "ADMIN_RECYCLE_CHILD", type, code,
                            OrderMaintenanceRevision.of(payload(reason))), session -> {
                        documents.recycleChild(code, operator.operator(), at, batch, reason, session);
                        hidePrints(code, operator.operator(), at, batch, reason, session);
                        return code;
                    });
                }
            }
            case "PRINT_JOB" -> printJobs.recycle(code, operator.operator(), at, batch, reason);
            case "PRINT_TARGET" -> routing.recycleJob(code, operator.operator(), at, batch, reason);
            default -> throw new BusinessException("不能回收这种单据");
        }
    }

    public void restore(OrderMaintenanceCommands.RestoreCommand command, OperatorContext operator) {
        requireAdmin(operator);
        String type = required(command.targetType(), "请选择单据类型");
        String code = required(command.code(), "请填写单号");
        switch (type) {
            case "MOTHER" -> {
                if (workUnit == null) {
                    OrderRecycleMark mark = documents.motherRecycle(code).orElseThrow(() -> new BusinessException("母单不在回收站"));
                    documents.clearRecycleBatch(mark.batch());
                    printJobs.restoreBatch(mark.batch());
                    routing.restoreBatch(mark.batch());
                } else {
                    workUnit.execute(new CommandCall(required(command.requestId(), "恢复必须带请求编号"),
                            operator.operator(), "ADMIN_RESTORE_MOTHER", type, code,
                            OrderMaintenanceRevision.of(payload(type, code))), session -> {
                        OrderRecycleMark mark = documents.motherRecycle(code)
                                .orElseThrow(() -> new BusinessException("母单不在回收站"));
                        documents.clearRecycleBatch(mark.batch(), session);
                        printJobs.restoreBatch(mark.batch(), session);
                        routing.restoreBatch(mark.batch(), session);
                        return code;
                    });
                }
            }
            case "WORK_ORDER" -> {
                if (workUnit == null) {
                    restoreChild(code);
                } else {
                    workUnit.execute(new CommandCall(required(command.requestId(), "恢复必须带请求编号"),
                            operator.operator(), "ADMIN_RESTORE_CHILD", type, code,
                            OrderMaintenanceRevision.of(payload(type, code))), session -> {
                        ChildWorkOrderRecord child = documents.findChildWorkOrderAny(code)
                                .orElseThrow(() -> new BusinessException("子单不存在"));
                        OrderRecycleMark mark = documents.childRecycle(code)
                                .orElseThrow(() -> new BusinessException("子单不在回收站"));
                        if (documents.motherRecycle(child.motherOrderCode()).isPresent()) {
                            throw new BusinessException("请先恢复母单");
                        }
                        documents.clearChildRecycle(code, session);
                        printJobs.restoreBatch(mark.batch(), session);
                        routing.restoreBatch(mark.batch(), session);
                        return code;
                    });
                }
            }
            case "PRINT_JOB" -> {
                PrintJob job = printJobs.findAny(code).orElseThrow(() -> new BusinessException("打印任务不存在"));
                assertOrderVisible(job.businessCode());
                printJobs.restore(code);
            }
            case "PRINT_TARGET" -> {
                PrintTargetJob job = routing.jobAny(code).orElseThrow(() -> new BusinessException("打印任务不存在"));
                routing.batch(job.batchId()).ifPresent(batch -> assertOrderVisible(batch.businessCode()));
                routing.restoreJob(code);
            }
            default -> throw new BusinessException("不能恢复这种单据");
        }
    }

    private void recycleMother(String code, String operator, String at, String batch, String reason) {
        documents.recycleMother(code, operator, at, batch, reason);
        for (ChildWorkOrderRecord child : documents.listChildWorkOrdersAny(code)) {
            if (documents.childRecycle(child.code()).isEmpty()) {
                documents.recycleChild(child.code(), operator, at, batch, reason);
            }
            hidePrints(child.code(), operator, at, batch, reason);
        }
        hidePrints(code, operator, at, batch, reason);
    }

    private void recycleMother(String code, String operator, String at, String batch, String reason, WorkSession session) {
        documents.recycleMother(code, operator, at, batch, reason, session);
        for (ChildWorkOrderRecord child : documents.listChildWorkOrdersAny(code)) {
            if (documents.childRecycle(child.code()).isEmpty()) {
                documents.recycleChild(child.code(), operator, at, batch, reason, session);
            }
            hidePrints(child.code(), operator, at, batch, reason, session);
        }
        hidePrints(code, operator, at, batch, reason, session);
    }

    private void restoreChild(String code) {
        ChildWorkOrderRecord child = documents.findChildWorkOrderAny(code).orElseThrow(() -> new BusinessException("子单不存在"));
        OrderRecycleMark mark = documents.childRecycle(code).orElseThrow(() -> new BusinessException("子单不在回收站"));
        if (documents.motherRecycle(child.motherOrderCode()).isPresent()) {
            throw new BusinessException("请先恢复母单");
        }
        documents.clearChildRecycle(code);
        printJobs.restoreBatch(mark.batch());
        routing.restoreBatch(mark.batch());
    }

    private void hidePrints(String businessCode, String operator, String at, String batch, String reason) {
        printJobs.recycleByBusinessCode(businessCode, operator, at, batch, reason);
        routing.recycleByBusinessCode(businessCode, operator, at, batch, reason);
    }

    private void hidePrints(String businessCode, String operator, String at, String batch, String reason,
                            WorkSession session) {
        printJobs.recycleByBusinessCode(businessCode, operator, at, batch, reason, session);
        routing.recycleByBusinessCode(businessCode, operator, at, batch, reason, session);
    }

    private void assertOrderVisible(String businessCode) {
        if (documents.motherRecycle(businessCode).isPresent() || documents.childRecycle(businessCode).isPresent()) {
            throw new BusinessException("请先恢复对应单据");
        }
    }

    private void attachBusiness(List<MaintainedOrder> rows, String businessCode) {
        if (businessCode == null || businessCode.isBlank()) {
            return;
        }
        documents.findMotherOrderAny(businessCode).ifPresent(mother -> {
            if (rows.stream().noneMatch(row -> "MOTHER".equals(row.targetType()) && mother.code().equals(row.code()))) {
                rows.add(toMother(mother));
            }
        });
        documents.findChildWorkOrderAny(businessCode).ifPresent(child -> {
            documents.findMotherOrderAny(child.motherOrderCode()).ifPresent(mother -> {
                if (rows.stream().noneMatch(row -> "MOTHER".equals(row.targetType()) && mother.code().equals(row.code()))) {
                    rows.add(toMother(mother));
                }
            });
            if (rows.stream().noneMatch(row -> "WORK_ORDER".equals(row.targetType()) && child.code().equals(row.code()))) {
                rows.add(toChild(child));
            }
        });
    }

    private List<MaintainedOrder> printsFor(String businessCode) {
        List<MaintainedOrder> rows = new ArrayList<>();
        for (PrintJob job : printJobs.listByBusinessAny(businessCode)) {
            rows.add(toPrintJob(job));
        }
        for (PrintTargetJob job : routing.jobsForBusinessAny(businessCode)) {
            rows.add(toTarget(job));
        }
        return rows;
    }

    private MaintainedOrder toMother(MotherOrderRecord record) {
        OrderRecycleMark mark = documents.motherRecycle(record.code()).orElse(null);
        return new MaintainedOrder("MOTHER", record.code(), record.code(), record.customerName(), record.materialCode(),
                record.goodsName(), record.processingRequirement(), record.dueDate(), plain(record.quantity()),
                plain(record.weight()), "", null, "", "", mark != null, mark == null ? "" : mark.reason(),
                lines("MOTHER", record.code()), OrderMaintenanceRevision.of(record), isPosted(record.ledgerCode()));
    }

    private MaintainedOrder toChild(ChildWorkOrderRecord record) {
        OrderRecycleMark mark = documents.childRecycle(record.code()).orElse(null);
        boolean motherRecycled = documents.motherRecycle(record.motherOrderCode()).isPresent();
        return new MaintainedOrder("WORK_ORDER", record.code(), record.motherOrderCode(), record.customerName(),
                record.materialCode(), record.goodsName(), record.processingRequirement(), record.dueDate(),
                plain(record.quantity()), plain(record.weight()), record.processes(), null, "", "",
                mark != null || motherRecycled, mark == null ? "" : mark.reason(), lines("WORK_ORDER", record.code()),
                OrderMaintenanceRevision.of(record), isPosted(record.ledgerCode()));
    }

    private MaintainedOrder toPrintJob(PrintJob job) {
        PrintRecycle mark = printJobs.recycleMark(job.id()).orElse(null);
        return new MaintainedOrder("PRINT_JOB", job.id(), job.businessCode(), "", "", "", "", "", "", "", "",
                job.copies(), job.stationId(), "", mark != null, mark == null ? "" : mark.reason(),
                lines("PRINT_JOB", job.id()), OrderMaintenanceRevision.of(job), false);
    }

    private MaintainedOrder toTarget(PrintTargetJob job) {
        PrintRecycle mark = routing.jobRecycle(job.id()).orElse(null);
        String business = routing.batch(job.batchId()).map(batch -> batch.businessCode()).orElse("");
        return new MaintainedOrder("PRINT_TARGET", job.id(), business, "", "", "", "", "", "", "", "",
                job.copies(), job.targetStationId(), job.targetQueueId(), mark != null,
                mark == null ? "" : mark.reason(), lines("PRINT_TARGET", job.id()), OrderMaintenanceRevision.of(job), false);
    }

    private List<String> lines(String type, String code) {
        List<String> lines = new ArrayList<>();
        for (OrderAdjustment item : documents.listOrderAdjustments(type, code)) {
            lines.add(item.createdAt() + " " + item.operatorName() + " " + item.reason()
                    + "：" + item.beforeText() + " → " + item.afterText());
        }
        return lines;
    }

    private OrderAdjustment adjustment(String type, String code, String before, String after,
                                       String reason, OperatorContext operator) {
        return new OrderAdjustment(type, code, clip(before), clip(after), reason, operator.operator(), now());
    }

    private static String payload(Object... fields) {
        StringBuilder result = new StringBuilder();
        for (Object field : fields) {
            if (field == null) {
                result.append("-1:");
            } else {
                String value = field instanceof BigDecimal amount
                        ? amount.stripTrailingZeros().toPlainString() : field.toString();
                result.append(value.length()).append(':').append(value);
            }
        }
        return result.toString();
    }

    private void requireAdmin(OperatorContext operator) {
        permissions.assertAllowed(PermissionCode.ORDER_ADMIN_MAINTAIN, operator);
    }

    private static String motherText(MotherOrderRecord record) {
        return record.customerName() + " " + record.materialCode() + " " + record.goodsName()
                + " " + plain(record.quantity()) + "/" + plain(record.weight())
                + " " + record.processingRequirement() + " " + record.dueDate();
    }

    private static String childText(ChildWorkOrderRecord record) {
        return record.customerName() + " " + record.materialCode() + " " + record.goodsName()
                + " " + plain(record.quantity()) + "/" + plain(record.weight())
                + " " + record.processingRequirement() + " " + record.dueDate() + " " + record.processes();
    }

    private static void assertPostedMotherFactsUnchanged(MotherOrderRecord current, MotherOrderRecord next) {
        if (isPosted(current.ledgerCode()) && (!Objects.equals(current.customerId(), next.customerId())
                || !Objects.equals(current.customerName(), next.customerName())
                || !Objects.equals(current.materialCode(), next.materialCode())
                || !Objects.equals(current.goodsName(), next.goodsName())
                || !sameAmount(current.quantity(), next.quantity())
                || !sameAmount(current.weight(), next.weight()))) {
            throw new BusinessException("已入账母单的客户、材质、货品、数量和重量须走差额或冲正，不能直接覆盖");
        }
    }

    private static void assertPostedChildFactsUnchanged(ChildWorkOrderRecord current, ChildWorkOrderRecord next) {
        if (isPosted(current.ledgerCode()) && (!Objects.equals(current.customerId(), next.customerId())
                || !Objects.equals(current.customerName(), next.customerName())
                || !Objects.equals(current.materialCode(), next.materialCode())
                || !Objects.equals(current.goodsName(), next.goodsName())
                || !sameAmount(current.quantity(), next.quantity())
                || !sameAmount(current.weight(), next.weight())
                || !Objects.equals(current.processes(), next.processes()))) {
            throw new BusinessException("已入账子单的客户、材质、货品、数量、重量和工序须走差额或冲正，不能直接覆盖");
        }
    }

    private static boolean isPosted(String ledgerCode) {
        return ledgerCode != null && !ledgerCode.isBlank();
    }

    private static boolean sameAmount(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private static void assertPrintRevision(Object current, String expected) {
        if (!OrderMaintenanceRevision.of(current).equals(expected)) {
            throw new BusinessException("打印任务已被修改，请重新查找后再保存");
        }
    }

    private static void assertPending(PrintJobStatus status) {
        if (status != PrintJobStatus.PENDING) {
            throw new BusinessException("任务已领取、状态不明或已出队，不能直接改单；请核纸后使用改派或新批次");
        }
    }

    private static String plain(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String required(String value, String message) {
        String text = text(value);
        if (text.isBlank()) {
            throw new BusinessException(message);
        }
        return text;
    }

    private static String requireReason(String reason) {
        String text = required(reason, "请填写原因");
        if (text.length() > 200) {
            throw new BusinessException("原因不能超过 200 字");
        }
        return text;
    }

    private static BigDecimal requireQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessException("数量必须大于 0");
        }
        return quantity;
    }

    private static BigDecimal requireWeight(BigDecimal weight) {
        if (weight == null || weight.signum() < 0) {
            throw new BusinessException("重量不能为负");
        }
        return weight;
    }

    private static int requireCopies(int copies) {
        if (copies < 1) {
            throw new BusinessException("份数至少为 1");
        }
        return copies;
    }

    private static String clip(String value) {
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private static String now() {
        return LocalDateTime.now().toString();
    }
}
