package com.zhubao.erp.inventory.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.ReceiptEntryReversal;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockLedgerRepository;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import com.zhubao.erp.inventory.domain.StockTransferRules;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class StockLedgerService {

    private static final DateTimeFormatter CODE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
            .withZone(ZoneId.systemDefault());

    private final AtomicLong sequence = new AtomicLong();
    private final StockLedgerRepository repository;

    public StockLedgerService(StockLedgerRepository repository) {
        this.repository = repository;
    }

    public StockLedgerEntry transfer(StockTransferCommand command) {
        return repository.save(newEntry(command));
    }

    /** 库存流水进入当前工作单元，不在仓储里另开连接提交。 */
    public StockLedgerEntry transfer(StockTransferCommand command, WorkSession session) {
        return repository.save(newEntry(command), session);
    }

    /** 在当前工作单元连接上读取余额，能看见本事务尚未提交的转入。 */
    public StockBalance balance(StockDimension dimension, WorkSession session) {
        return repository.findBalance(dimension, session);
    }

    /**
     * 为收货录入错误追加冲正流水。只扣原收货去向，不写入新的库存状态。
     * 调用方须把单据数量、审计和请求结果放在同一个工作单元里。
     */
    /** 退还客户或冲减只扣来源余额，不把数量留在另一种在厂状态。 */
    public StockLedgerEntry depart(StockTransferCommand command, String sourceLedgerCode, WorkSession session) {
        if (session == null) {
            throw new BusinessException("库存离厂必须处于业务工作单元");
        }
        StockLedgerEntry entry = new StockLedgerEntry(
                nextCode(),
                Instant.now(),
                command.documentType(),
                command.documentCode(),
                command.from(),
                null,
                scale(command.quantity()),
                scale(command.weight()),
                command.remark(),
                sourceLedgerCode
        );
        com.zhubao.erp.inventory.domain.StockTransferRules.verifyDeparture(entry);
        return repository.appendDeparture(entry, session);
    }

    /**
     * 记录加工损耗或退回。只扣在制余额，不关联原收货，也不把数量转成报废或退还客户。
     * 重量必须已经称过；调用方不能把未知重量传成 0。
     */
    public StockLedgerEntry postProcessConsumption(String documentType, String documentCode, StockDimension from,
                                                   BigDecimal quantity, BigDecimal weight, String remark, WorkSession session) {
        if (session == null) {
            throw new BusinessException("加工损耗必须处于业务工作单元");
        }
        StockLedgerEntry entry = new StockLedgerEntry(
                nextCode(), Instant.now(), documentType, documentCode, from, null, quantity, weight,
                remark == null ? "" : remark, null);
        com.zhubao.erp.inventory.domain.StockTransferRules.verifyProcessConsumption(entry);
        return repository.appendProcessConsumption(entry, session);
    }

    /**
     * 外协已确认的实损只扣原库存数量。重量未知时余额重量保持不变。
     */
    public StockLedgerEntry postUnweighedLoss(String documentCode, StockDimension from, BigDecimal quantity, WorkSession session) {
        if (session == null) {
            throw new BusinessException("外协损耗必须处于业务工作单元");
        }
        StockLedgerEntry entry = new StockLedgerEntry(
                nextCode(), Instant.now(), "outsourcing_loss", documentCode, from, null, quantity, null,
                "外协确认损耗，重量未称", null);
        return repository.appendUnweighedLoss(entry, session);
    }

    /** 数量先行但未实称时，去向也保留重量待核，不把未知写成0克的实际流转。 */
    public StockLedgerEntry postUnweighedMovement(String type,String document,StockDimension from,StockDimension to,
            BigDecimal quantity,String remark,WorkSession session) {
        if (session==null) throw new BusinessException("未称数量流转必须处于业务工作单元");
        var entry=new StockLedgerEntry(nextCode(),Instant.now(),type,document,from,to,quantity,null,remark,null);
        StockTransferRules.verifyUnweighedProcess(entry);
        return repository.appendUnweighedLoss(entry,session);
    }

    public StockLedgerEntry reverseInbound(ReceiptEntryReversal reversal, WorkSession session) {
        if (session == null) {
            throw new BusinessException("收货冲正必须处于业务工作单元");
        }
        if (reversal == null || reversal.sourceLedgerCode() == null || reversal.sourceLedgerCode().isBlank()) {
            throw new BusinessException("收货冲正必须关联原库存流水");
        }
        if (reversal.motherOrderCode() == null || reversal.motherOrderCode().isBlank()) {
            throw new BusinessException("收货冲正必须指定母单");
        }
        if (reversal.originalDestination() == null) {
            throw new BusinessException("收货冲正必须指定原收货库存");
        }
        if (reversal.quantity() == null || reversal.weight() == null
                || reversal.quantity().signum() <= 0 || reversal.weight().signum() < 0) {
            throw new BusinessException("收货冲正的数量必须为正数，重量不能为负数");
        }
        StockLedgerEntry entry = new StockLedgerEntry(
                nextCode(),
                Instant.now(),
                "inbound_reversal",
                reversal.motherOrderCode().trim(),
                reversal.originalDestination(),
                null,
                reversal.quantity(),
                reversal.weight(),
                reversal.remark() == null ? "" : reversal.remark().trim(),
                reversal.sourceLedgerCode().trim()
        );
        return repository.appendInboundReversal(entry, session);
    }

    private StockLedgerEntry newEntry(StockTransferCommand command) {
        return new StockLedgerEntry(
                nextCode(),
                Instant.now(),
                command.documentType(),
                command.documentCode(),
                command.from(),
                command.to(),
                scale(command.quantity()),
                scale(command.weight()),
                command.remark()
        );
    }

    public List<StockLedgerEntry> ledgers() {
        return repository.findAllLedgers();
    }

    /** 客户范围明确时只读取相关台账；全量读取仍走 {@link #ledgers()}。 */
    public List<StockLedgerEntry> ledgersForCustomers(Set<Long> customerIds, Set<String> motherOrderCodes) {
        return repository.findLedgersByCustomers(customerIds, motherOrderCodes);
    }

    public List<StockLedgerEntry> ledgersForMother(String motherOrderCode, WorkSession session) {
        return repository.ledgersForMother(motherOrderCode, session);
    }

    /** 母单行锁取得后，以当前读核对本单流水，避免等待锁期间沿用旧快照。 */
    public List<StockLedgerEntry> lockedLedgersForMother(String motherOrderCode, WorkSession session) {
        return repository.ledgersForMother(motherOrderCode, session, true);
    }

    /** 按母单范围判断单据是否已有库存流水，不把整本台账载入内存。 */
    public boolean hasOrderMovement(String motherOrderCode, String orderCode) {
        return repository.hasOrderMovement(motherOrderCode, orderCode);
    }

    public List<StockBalance> balances() {
        return repository.findAllBalances();
    }

    private String nextCode() {
        return "STK-" + CODE_TIME.format(Instant.now()) + "-" + String.format("%06d", sequence.incrementAndGet());
    }

    private BigDecimal scale(BigDecimal value) {
        return value.stripTrailingZeros();
    }
}
