package com.zhubao.erp.finance.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

/**
 * 经营核算仓储。本地默认内存，MySQL 可切换 JDBC。
 */
public interface FinanceRepository {

    void saveAgreement(PriceAgreement agreement);

    List<PriceAgreement> listAgreements();

    void saveReceivable(ReceivableLine line);

    /** 在工作会话中追加应收。失败时内存撤销，JDBC 随连接回滚。 */
    void saveReceivable(ReceivableLine line, WorkSession session);

    List<ReceivableLine> listReceivables();

    Optional<ReceivableLine> findReceivableByCode(String code);

    Optional<ReceivableLine> findReceivableBySourceKey(String sourceKey);

    void saveReceipt(CustomerReceipt receipt);

    /**
     * 在同一持久化动作里保存收款单和核销行。
     * 幂等键相同且客户、金额、收款日、客户名称和核销明细一致时返回原收款单；内容不同则拒绝。
     * 空键不去重。JDBC 使用单连接事务；内存用同一把锁。
     */
    CustomerReceipt registerReceipt(CustomerReceipt receipt, String idempotencyKey);

    List<CustomerReceipt> listReceipts();

    void saveWorkerRate(WorkerRate rate);

    List<WorkerRate> listWorkerRates();

    void replacePayroll(String yearMonth, List<PayrollLine> lines);

    /** 追加一行工资调整，不删除当月已有工资。 */
    void savePayrollLine(PayrollLine line);

    void savePayrollLine(PayrollLine line, WorkSession session);

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

    void savePayable(Payable payable, WorkSession session);

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
