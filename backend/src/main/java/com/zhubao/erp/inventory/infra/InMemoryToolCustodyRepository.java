package com.zhubao.erp.inventory.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.ToolCustodyRepository;
import com.zhubao.erp.inventory.domain.ToolDisposition;
import com.zhubao.erp.inventory.domain.ToolLoan;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.tool-custody.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryToolCustodyRepository implements ToolCustodyRepository {

    private final ConcurrentHashMap<String, BigDecimal> stocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> units = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Piece> pieces = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ToolLoan> loans = new ConcurrentHashMap<>();

    @Override
    public void addStock(String itemId, String warehouseCode, BigDecimal quantity, String unit, WorkSession session) {
        String key = itemId + "\n" + warehouseCode;
        synchronized (stocks) {
            BigDecimal previous = stocks.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal next = previous.add(quantity);
            if (next.signum() < 0) {
                throw new BusinessException("可借数量不足");
            }
            if (next.signum() == 0) {
                stocks.remove(key);
            } else {
                stocks.put(key, next);
                units.put(key, unit);
            }
            if (session != null) {
                session.onRollback(() -> {
                    synchronized (stocks) {
                        if (previous.signum() == 0) {
                            stocks.remove(key);
                        } else {
                            stocks.put(key, previous);
                        }
                    }
                });
            }
        }
    }

    @Override
    public BigDecimal stock(String itemId, String warehouseCode) {
        return stocks.getOrDefault(itemId + "\n" + warehouseCode, BigDecimal.ZERO);
    }

    @Override
    public void placePiece(String pieceCode, String itemId, String warehouseCode, WorkSession session) {
        Piece previous = pieces.putIfAbsent(pieceCode, new Piece(itemId, warehouseCode, ""));
        if (previous != null) {
            throw new BusinessException("工具编号已存在");
        }
        if (session != null) {
            session.onRollback(() -> pieces.remove(pieceCode));
        }
    }

    @Override
    public void loan(ToolLoan loan, WorkSession session) {
        if (!loan.pieceCode().isEmpty()) {
            Piece piece = pieces.get(loan.pieceCode());
            if (piece == null || !piece.holderId.isEmpty()) {
                throw new BusinessException("该工具已借出");
            }
            pieces.put(loan.pieceCode(), new Piece(piece.itemId, piece.warehouseCode, loan.holderId()));
            if (session != null) {
                session.onRollback(() -> pieces.put(loan.pieceCode(), piece));
            }
        }
        if (loans.putIfAbsent(loan.loanId(), loan) != null) {
            throw new BusinessException("借出单已存在");
        }
        if (session != null) {
            session.onRollback(() -> loans.remove(loan.loanId(), loan));
        }
    }

    @Override
    public Optional<ToolLoan> find(String loanId) {
        return Optional.ofNullable(loans.get(loanId));
    }

    @Override
    public ToolLoan settle(String loanId, BigDecimal quantity, int expectedVersion, WorkSession session) {
        synchronized (loans) {
            ToolLoan current = loans.get(loanId);
            if (current == null) {
                throw new BusinessException("借出单不存在");
            }
            if (current.version() != expectedVersion) {
                throw new BusinessException("借出单已被其他人修改，请刷新后重试");
            }
            if (current.returnedQuantity().add(quantity).compareTo(current.quantity()) > 0) {
                throw new BusinessException("归还数量不能超过未还");
            }
            ToolLoan next = new ToolLoan(
                    current.loanId(), current.itemId(), current.holderId(), current.holderName(), current.quantity(),
                    current.returnedQuantity().add(quantity), current.unit(), current.pieceCode(), current.warehouseCode(),
                    current.version() + 1);
            loans.put(loanId, next);
            if (session != null) {
                session.onRollback(() -> {
                    synchronized (loans) {
                        loans.put(loanId, current);
                    }
                });
            }
            return next;
        }
    }

    private final ConcurrentHashMap<String, ToolDisposition> dispositions = new ConcurrentHashMap<>();

    @Override
    public void record(ToolDisposition disposition, WorkSession session) {
        if (dispositions.putIfAbsent(disposition.dispositionId(), disposition) != null) {
            throw new BusinessException("工具处置已存在");
        }
        if (session != null) {
            session.onRollback(() -> dispositions.remove(disposition.dispositionId(), disposition));
        }
    }

    public java.util.List<ToolDisposition> dispositionsOf(String loanId) {
        return dispositions.values().stream().filter(row -> row.loanId().equals(loanId)).toList();
    }

    @Override
    public List<ToolLoan> loansOf(String itemId) {
        return loans.values().stream().filter(loan -> loan.itemId().equals(itemId)).toList();
    }

    @Override
    public BigDecimal outstanding(String itemId, String holderId) {
        return loans.values().stream()
                .filter(loan -> loan.itemId().equals(itemId) && loan.holderId().equals(holderId))
                .map(ToolLoan::outstanding)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private record Piece(String itemId, String warehouseCode, String holderId) {
    }
}
