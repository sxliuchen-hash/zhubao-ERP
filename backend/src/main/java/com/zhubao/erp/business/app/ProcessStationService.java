package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.MotherSplitState;
import com.zhubao.erp.business.domain.ProcessStationCard;
import com.zhubao.erp.business.domain.ProcessStationHandoff;
import com.zhubao.erp.business.domain.ProcessStationReportResult;
import com.zhubao.erp.business.domain.ProcessStationView;
import com.zhubao.erp.business.domain.ReportProductionCommand;
import com.zhubao.erp.business.domain.WorkshopProcesses;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import com.zhubao.erp.scan.app.ScanCodeParser;
import com.zhubao.erp.scan.domain.ScanObjectType;
import com.zhubao.erp.scan.domain.ScanResolveResponse;
import com.zhubao.erp.security.domain.AuthUserAccount;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 三个工序岗位扫码。只展示账号绑定的工序，并按单上的顺序交付。
 * 中间工序写入台账但留在在制；最后一道再把在制转入半成品。
 */
@Service
public class ProcessStationService {

    private static final DateTimeFormatter RECORD_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String STATION_DOCUMENT = "process_station_report";
    private static final String WRONG_STICKER = "请扫描子单贴纸，或不拆母单的贴纸";

    private final BusinessDocumentRepository documents;
    private final StockLedgerService stockLedgerService;
    private final BusinessOperationService businessOperationService;
    private final ScanCodeParser scanCodeParser;

    public ProcessStationService(
            BusinessDocumentRepository documents,
            StockLedgerService stockLedgerService,
            BusinessOperationService businessOperationService,
            ScanCodeParser scanCodeParser
    ) {
        this.documents = documents;
        this.stockLedgerService = stockLedgerService;
        this.businessOperationService = businessOperationService;
        this.scanCodeParser = scanCodeParser;
    }

    public ProcessStationView lookup(String rawCode, AuthUserAccount account) {
        requireAccount(account);
        StationTicket ticket = resolve(rawCode);
        List<String> visible = visibleProcesses(ticket, account);
        if (visible.isEmpty()) {
            return new ProcessStationView(
                    ticket.orderCode(),
                    ticket.customerName(),
                    ticket.dueDate(),
                    ticket.processingRequirement(),
                    List.of(),
                    "当前账号未绑定这张单上的工序，不能报工"
            );
        }
        List<ProcessStationCard> stations = new ArrayList<>();
        for (String process : visible) {
            stations.add(card(ticket, process));
        }
        return new ProcessStationView(
                ticket.orderCode(),
                ticket.customerName(),
                ticket.dueDate(),
                ticket.processingRequirement(),
                stations,
                ""
        );
    }

    public ProcessStationReportResult report(String rawCode, String requestedProcess, AuthUserAccount account) {
        requireAccount(account);
        StationTicket ticket = resolve(rawCode);
        String process = normalizeRequested(requestedProcess);
        if (!ticket.processes().contains(process)) {
            throw new BusinessException("这张单没有「" + process + "」这道工序");
        }
        if (!boundProcesses(account).contains(process)) {
            throw new BusinessException("当前账号不能做这道工序");
        }
        if (submitted(ticket.orderCode(), process)) {
            throw new BusinessException("这道工序已经交过");
        }
        String previous = previousProcess(ticket.processes(), process);
        if (!previous.isBlank() && !submitted(ticket.orderCode(), previous)) {
            throw new BusinessException("上一道「" + previous + "」还没交，不能交「" + process + "」");
        }
        String operator = account.operator();
        ensureWipForKeptMother(ticket, operator);
        boolean last = process.equals(ticket.processes().get(ticket.processes().size() - 1));
        StockLedgerEntry entry = last
                ? moveToSemiFinished(ticket, process, operator)
                : recordStation(ticket, process, operator);
        documents.saveProcessStationHandoff(new ProcessStationHandoff(
                ticket.orderCode(),
                process,
                operator,
                entry.code(),
                RECORD_TIME.format(LocalDateTime.now())
        ));
        return new ProcessStationReportResult(
                ticket.orderCode(),
                process,
                entry.code(),
                last,
                last ? StockStatus.SEMI_FINISHED.name() : StockStatus.WIP.name()
        );
    }

    private void ensureWipForKeptMother(StationTicket ticket, String operator) {
        if (!ticket.keptMother()) {
            return;
        }
        StockDimension wip = wipDimension(ticket);
        if (balanceOf(wip).quantity().compareTo(BigDecimal.ZERO) > 0) {
            return;
        }
        stockLedgerService.transfer(new StockTransferCommand(
                "work_order_split",
                ticket.orderCode(),
                rawDimension(ticket),
                wip,
                ticket.quantity(),
                ticket.weight(),
                "不拆母单转入在制；操作人：" + operator
        ));
    }

    private StockLedgerEntry recordStation(StationTicket ticket, String process, String operator) {
        StockDimension wip = wipDimension(ticket);
        return stockLedgerService.transfer(new StockTransferCommand(
                STATION_DOCUMENT,
                ticket.orderCode(),
                wip,
                wip,
                ticket.quantity(),
                ticket.weight(),
                "岗位报工；工序：" + process + "；操作人：" + operator
        ));
    }

    private StockLedgerEntry moveToSemiFinished(StationTicket ticket, String process, String operator) {
        var result = businessOperationService.reportProduction(new ReportProductionCommand(
                ticket.motherOrderCode(),
                ticket.orderCode(),
                ticket.customerId(),
                ticket.ownerCustomerId(),
                ticket.materialCode(),
                ticket.warehouseCode(),
                ticket.warehouseCode(),
                ticket.quantity(),
                ticket.quantityUnit(),
                ticket.weight(),
                LocalDate.now().toString(),
                process,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                operator,
                null,
                null,
                null
        ));
        return new StockLedgerEntry(
                result.ledgerCode(),
                null,
                "production_report",
                ticket.orderCode(),
                null,
                null,
                result.quantity(),
                result.weight(),
                null
        );
    }

    private ProcessStationCard card(StationTicket ticket, String process) {
        String previous = previousProcess(ticket.processes(), process);
        boolean previousSubmitted = previous.isBlank() || submitted(ticket.orderCode(), previous);
        return new ProcessStationCard(
                process,
                ticket.processingRequirement(),
                ticket.dueDate(),
                previous,
                previousSubmitted,
                submitted(ticket.orderCode(), process)
        );
    }

    private List<String> visibleProcesses(StationTicket ticket, AuthUserAccount account) {
        List<String> allowed = boundProcesses(account);
        List<String> visible = new ArrayList<>();
        for (String process : ticket.processes()) {
            if (allowed.contains(process)) {
                visible.add(process);
            }
        }
        return visible;
    }

    private List<String> boundProcesses(AuthUserAccount account) {
        return WorkshopProcesses.bind(account.allowedProcesses());
    }

    private String normalizeRequested(String requestedProcess) {
        if (requestedProcess == null || requestedProcess.isBlank()) {
            throw new BusinessException("请选择要交付的工序");
        }
        return WorkshopProcesses.normalize(List.of(requestedProcess.trim())).get(0);
    }

    private String previousProcess(List<String> processes, String process) {
        int index = processes.indexOf(process);
        if (index <= 0) {
            return "";
        }
        return processes.get(index - 1);
    }

    private boolean submitted(String orderCode, String process) {
        return documents.findProcessStationHandoff(orderCode, process).isPresent();
    }

    private StationTicket resolve(String rawCode) {
        String text = rawCode == null ? "" : rawCode.trim();
        if (text.isEmpty()) {
            throw new BusinessException("请先扫贴纸");
        }
        if (text.startsWith("JG|")) {
            ScanResolveResponse resolved = scanCodeParser.resolve(text, "pwa_report");
            if (resolved.objectType() == ScanObjectType.WORK_ORDER) {
                return childTicket(resolved.code());
            }
            if (resolved.objectType() == ScanObjectType.MOTHER_ORDER) {
                return motherTicket(resolved.code());
            }
            throw new BusinessException(WRONG_STICKER);
        }
        if (documents.findChildWorkOrderByCode(text).isPresent()) {
            return childTicket(text);
        }
        if (documents.findMotherOrderByCode(text).isPresent()) {
            return motherTicket(text);
        }
        throw new BusinessException(WRONG_STICKER);
    }

    private StationTicket childTicket(String code) {
        ChildWorkOrderRecord child = documents.findChildWorkOrderByCode(code)
                .orElseThrow(() -> new BusinessException("子单不存在：" + code));
        MotherOrderRecord mother = requireMother(child.motherOrderCode());
        List<String> stored = WorkshopProcesses.split(child.processes());
        if (stored.isEmpty()) {
            throw new BusinessException("这张子单没有工序");
        }
        return new StationTicket(
                child.code(),
                mother.code(),
                false,
                child.customerId() == null ? mother.customerId() : child.customerId(),
                mother.ownerCustomerId() == null ? mother.customerId() : mother.ownerCustomerId(),
                ownerTypeOf(mother),
                blank(child.customerName()),
                blank(child.processingRequirement()),
                blank(child.dueDate()),
                blank(child.materialCode()),
                blank(child.warehouseCode()),
                blankToDefault(child.quantityUnit(), "件"),
                requirePositive(child.quantity(), "数量"),
                requirePositive(child.weight(), "重量"),
                WorkshopProcesses.normalize(stored)
        );
    }

    private StationTicket motherTicket(String code) {
        MotherOrderRecord mother = requireMother(code);
        MotherSplitState state = documents.findMotherSplitState(mother.code()).orElse(null);
        if (!documents.listChildWorkOrders(mother.code()).isEmpty()
                || (state != null && MotherSplitState.SPLIT.equals(state.decision()))) {
            throw new BusinessException("这张母单已经拆成子单，请扫描子单贴纸");
        }
        if (state == null || !MotherSplitState.KEEP.equals(state.decision())) {
            throw new BusinessException("请先选择不拆，再扫母单贴纸");
        }
        return new StationTicket(
                mother.code(),
                mother.code(),
                true,
                mother.customerId(),
                mother.ownerCustomerId() == null ? mother.customerId() : mother.ownerCustomerId(),
                ownerTypeOf(mother),
                blank(mother.customerName()),
                blank(mother.processingRequirement()),
                blank(mother.dueDate()),
                blank(mother.materialCode()),
                blank(mother.warehouseCode()),
                blankToDefault(mother.quantityUnit(), "件"),
                requirePositive(mother.quantity(), "数量"),
                requirePositive(mother.weight(), "重量"),
                WorkshopProcesses.DEFAULT_ORDER
        );
    }

    private StockDimension rawDimension(StationTicket ticket) {
        return new StockDimension(
                ticket.ownerType(),
                ticket.ownerCustomerId(),
                ticket.customerId(),
                ticket.motherOrderCode(),
                null,
                ticket.materialCode(),
                ticket.warehouseCode(),
                StockStatus.RAW_WAITING
        );
    }

    private StockDimension wipDimension(StationTicket ticket) {
        return new StockDimension(
                ticket.ownerType(),
                ticket.ownerCustomerId(),
                ticket.customerId(),
                ticket.motherOrderCode(),
                ticket.orderCode(),
                ticket.materialCode(),
                ticket.warehouseCode(),
                StockStatus.WIP
        );
    }

    private StockBalance balanceOf(StockDimension dimension) {
        return stockLedgerService.balances().stream()
                .filter(balance -> dimension.equals(balance.dimension()))
                .findFirst()
                .orElse(new StockBalance(dimension, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    private MotherOrderRecord requireMother(String code) {
        return documents.findMotherOrderByCode(code)
                .orElseThrow(() -> new BusinessException("母单不存在：" + code));
    }

    private void requireAccount(AuthUserAccount account) {
        if (account == null || account.operator() == null || account.operator().isBlank()) {
            throw new BusinessException("请先登录后再做岗位报工");
        }
    }

    private OwnerType ownerTypeOf(MotherOrderRecord mother) {
        if (mother.ownerType() == null || mother.ownerType().isBlank()) {
            return OwnerType.CUSTOMER;
        }
        try {
            return OwnerType.valueOf(mother.ownerType().trim());
        } catch (IllegalArgumentException ex) {
            return OwnerType.CUSTOMER;
        }
    }

    private BigDecimal requirePositive(BigDecimal value, String label) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("这张单的" + label + "无效，不能报工");
        }
        return value;
    }

    private String blank(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToDefault(String value, String fallback) {
        String text = blank(value);
        return text.isEmpty() ? fallback : text;
    }

    private record StationTicket(
            String orderCode,
            String motherOrderCode,
            boolean keptMother,
            Long customerId,
            Long ownerCustomerId,
            OwnerType ownerType,
            String customerName,
            String processingRequirement,
            String dueDate,
            String materialCode,
            String warehouseCode,
            String quantityUnit,
            BigDecimal quantity,
            BigDecimal weight,
            List<String> processes
    ) {
    }
}
