package com.zhubao.erp.inventory.app;

import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockLedgerRepository;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
        StockLedgerEntry entry = new StockLedgerEntry(
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
        return repository.save(entry);
    }

    public List<StockLedgerEntry> ledgers() {
        return repository.findAllLedgers();
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
