package com.zhubao.erp.finance.domain;

import java.util.List;
import java.util.Optional;

/**
 * 经营核算仓储。本地默认内存，MySQL 可切换 JDBC。
 */
public interface FinanceRepository {

    void saveAgreement(PriceAgreement agreement);

    List<PriceAgreement> listAgreements();

    void saveReceivable(ReceivableLine line);

    List<ReceivableLine> listReceivables();

    Optional<ReceivableLine> findReceivableByCode(String code);

    Optional<ReceivableLine> findReceivableBySourceKey(String sourceKey);

    void saveReceipt(CustomerReceipt receipt);

    List<CustomerReceipt> listReceipts();

    void saveWorkerRate(WorkerRate rate);

    List<WorkerRate> listWorkerRates();

    void replacePayroll(String yearMonth, List<PayrollLine> lines);

    List<PayrollLine> listPayrollLines();

    Optional<PayrollRun> findPayrollRun(String yearMonth);

    void savePayrollRun(PayrollRun run);

    void saveConsumableItem(ConsumableItem item);

    List<ConsumableItem> listConsumableItems();

    Optional<ConsumableItem> findConsumableItem(String code);

    void saveConsumableMovement(ConsumableMovement movement);

    List<ConsumableMovement> listConsumableMovements();

    void saveAsset(FixedAsset asset);

    List<FixedAsset> listAssets();

    void saveDepreciation(DepreciationEntry entry);

    List<DepreciationEntry> listDepreciations();

    void savePayable(Payable payable);

    List<Payable> listPayables();

    Optional<Payable> findPayable(String code);

    Optional<Payable> findPayableBySource(String sourceCode);

    void savePayablePayment(PayablePayment payment);

    /**
     * 在同一持久化动作里检查余额、写入付款并更新已付金额。
     * 幂等键相同且应付单、金额、付款日一致时返回原付款；内容不同则拒绝。
     * 空键不参与去重，但仍禁止超付。内存用锁；JDBC 用单事务、应付行锁和条件更新。
     */
    PayablePayment registerPayablePayment(PayablePayment payment, String idempotencyKey);

    List<PayablePayment> listPayablePayments();
}
