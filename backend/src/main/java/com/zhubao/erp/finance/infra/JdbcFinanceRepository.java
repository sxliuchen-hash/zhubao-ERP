package com.zhubao.erp.finance.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.ChargeUnit;
import com.zhubao.erp.finance.domain.ConsumableItem;
import com.zhubao.erp.finance.domain.ConsumableMovement;
import com.zhubao.erp.finance.domain.CustomerReceipt;
import com.zhubao.erp.finance.domain.DepreciationEntry;
import com.zhubao.erp.finance.domain.FinancePaymentProbe;
import com.zhubao.erp.finance.domain.FinanceReceiptProbe;
import com.zhubao.erp.finance.domain.FinanceRepository;
import com.zhubao.erp.finance.domain.FixedAsset;
import com.zhubao.erp.finance.domain.Payable;
import com.zhubao.erp.finance.domain.PayablePayment;
import com.zhubao.erp.finance.domain.PayrollLine;
import com.zhubao.erp.finance.domain.PayrollRun;
import com.zhubao.erp.finance.domain.PriceAgreement;
import com.zhubao.erp.finance.domain.PriceLine;
import com.zhubao.erp.finance.domain.PricingMode;
import com.zhubao.erp.finance.domain.ReceiptAllocation;
import com.zhubao.erp.finance.domain.ReceivableLine;
import com.zhubao.erp.finance.domain.WorkerRate;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 经营核算 JDBC 仓储。表结构见 V36。
 */
@Repository
@ConditionalOnProperty(name = "zhubao.finance.repository", havingValue = "jdbc")
public class JdbcFinanceRepository implements FinanceRepository {

    private final DataSource dataSource;
    private final FinancePaymentProbe paymentProbe;
    private final FinanceReceiptProbe receiptProbe;

    @Autowired
    public JdbcFinanceRepository(DataSource dataSource) {
        this(dataSource, FinancePaymentProbe.NONE, FinanceReceiptProbe.NONE);
    }

    public JdbcFinanceRepository(DataSource dataSource, FinancePaymentProbe paymentProbe) {
        this(dataSource, paymentProbe, FinanceReceiptProbe.NONE);
    }

    public JdbcFinanceRepository(DataSource dataSource, FinancePaymentProbe paymentProbe, FinanceReceiptProbe receiptProbe) {
        this.dataSource = dataSource;
        this.paymentProbe = paymentProbe == null ? FinancePaymentProbe.NONE : paymentProbe;
        this.receiptProbe = receiptProbe == null ? FinanceReceiptProbe.NONE : receiptProbe;
    }

    @Override
    public void saveAgreement(PriceAgreement agreement) {
        String update = """
                update fin_price_agreement
                   set customer_id = ?, customer_name = ?, pricing_mode = ?, effective_from = ?, effective_to = ?,
                       operator_name = ?, created_at = ?
                 where code = ?
                """;
        String insert = """
                insert into fin_price_agreement (
                    code, customer_id, customer_name, pricing_mode, effective_from, effective_to, operator_name, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection()) {
            int updated;
            try (PreparedStatement statement = connection.prepareStatement(update)) {
                bindAgreement(statement, agreement, false);
                updated = statement.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement statement = connection.prepareStatement(insert)) {
                    bindAgreement(statement, agreement, true);
                    statement.executeUpdate();
                }
            }
            try (PreparedStatement delete = connection.prepareStatement("delete from fin_price_line where agreement_code = ?")) {
                delete.setString(1, agreement.code());
                delete.executeUpdate();
            }
            String lineSql = """
                    insert into fin_price_line (agreement_code, line_no, process_name, charge_unit, unit_price)
                    values (?, ?, ?, ?, ?)
                    """;
            try (PreparedStatement statement = connection.prepareStatement(lineSql)) {
                int lineNo = 1;
                for (PriceLine line : agreement.lines()) {
                    statement.setString(1, agreement.code());
                    statement.setInt(2, lineNo++);
                    statement.setString(3, line.processName());
                    statement.setString(4, line.unit().name());
                    statement.setBigDecimal(5, line.unitPrice());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save price agreement: " + agreement.code(), ex);
        }
    }

    @Override
    public List<PriceAgreement> listAgreements() {
        String sql = """
                select code, customer_id, customer_name, pricing_mode, effective_from, effective_to, operator_name, created_at
                  from fin_price_agreement
                 order by code
                """;
        record Header(
                String code,
                long customerId,
                String customerName,
                PricingMode mode,
                String effectiveFrom,
                String effectiveTo,
                String operator,
                String createdAt
        ) {
        }
        List<Header> headers = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                headers.add(new Header(
                        rs.getString("code"),
                        rs.getLong("customer_id"),
                        rs.getString("customer_name"),
                        PricingMode.valueOf(rs.getString("pricing_mode")),
                        rs.getString("effective_from"),
                        blank(rs.getString("effective_to")),
                        rs.getString("operator_name"),
                        rs.getString("created_at")
                ));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to list price agreements", ex);
        }
        List<PriceAgreement> agreements = new ArrayList<>();
        for (Header header : headers) {
            agreements.add(new PriceAgreement(
                    header.code(),
                    header.customerId(),
                    header.customerName(),
                    header.mode(),
                    header.effectiveFrom(),
                    header.effectiveTo(),
                    loadPriceLines(header.code()),
                    header.operator(),
                    header.createdAt()
            ));
        }
        return agreements;
    }

    @Override
    public void saveReceivable(ReceivableLine line) {
        try (Connection connection = dataSource.getConnection()) {
            insertReceivable(connection, line);
        } catch (SQLException ex) {
            if (isDuplicate(ex)) {
                throw new BusinessException("这张单据已经入过账");
            }
            throw new IllegalStateException("Failed to save receivable: " + line.code(), ex);
        }
    }

    @Override
    public void saveReceivable(ReceivableLine line, WorkSession session) {
        try {
            insertReceivable(requireSession(session), line);
        } catch (SQLException ex) {
            if (isDuplicate(ex)) {
                throw new BusinessException("这张单据已经入过账");
            }
            throw new IllegalStateException("Failed to save receivable: " + line.code(), ex);
        }
    }

    private void insertReceivable(Connection connection, ReceivableLine line) throws SQLException {
        String sql = """
                insert into fin_receivable_line (
                    code, customer_id, customer_name, `year_month`, source_type, source_key, mother_order_code,
                    work_order_code, process_name, quantity, charge_unit, unit_price, amount, reverses_code,
                    remark, operator_name, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, line.code());
            statement.setLong(2, line.customerId());
            statement.setString(3, line.customerName());
            statement.setString(4, line.yearMonth());
            statement.setString(5, line.sourceType());
            statement.setString(6, line.sourceKey());
            statement.setString(7, line.motherOrderCode());
            statement.setString(8, line.workOrderCode());
            statement.setString(9, line.processName());
            statement.setBigDecimal(10, line.quantity());
            statement.setString(11, line.unit().name());
            statement.setBigDecimal(12, line.unitPrice());
            statement.setBigDecimal(13, line.amount());
            statement.setString(14, line.reversesCode());
            statement.setString(15, line.remark());
            statement.setString(16, line.operator());
            statement.setString(17, line.createdAt());
            statement.executeUpdate();
        }
    }

    private Connection requireSession(WorkSession session) {
        if (session == null || session.connection().isEmpty()) {
            throw new IllegalStateException("财务写入必须使用工作单元连接");
        }
        return session.connection().get();
    }

    @Override
    public List<ReceivableLine> listReceivables() {
        return queryReceivables("select * from fin_receivable_line order by code");
    }

    @Override
    public Optional<ReceivableLine> findReceivableByCode(String code) {
        return queryReceivables("select * from fin_receivable_line where code = ?", code).stream().findFirst();
    }

    @Override
    public Optional<ReceivableLine> findReceivableBySourceKey(String sourceKey) {
        return queryReceivables("select * from fin_receivable_line where source_key = ?", sourceKey).stream().findFirst();
    }

    @Override
    public void saveReceipt(CustomerReceipt receipt) {
        String sql = """
                insert into fin_customer_receipt (
                    code, customer_id, customer_name, `year_month`, amount, received_on, operator_name, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String allocationSql = """
                insert into fin_receipt_allocation (receipt_code, line_no, receivable_code, amount)
                values (?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, receipt.code());
            statement.setLong(2, receipt.customerId());
            statement.setString(3, receipt.customerName());
            statement.setString(4, receipt.yearMonth());
            statement.setBigDecimal(5, receipt.amount());
            statement.setString(6, receipt.receivedOn());
            statement.setString(7, receipt.operator());
            statement.setString(8, receipt.createdAt());
            statement.executeUpdate();
            try (PreparedStatement allocation = connection.prepareStatement(allocationSql)) {
                int lineNo = 1;
                for (ReceiptAllocation item : receipt.allocations()) {
                    allocation.setString(1, receipt.code());
                    allocation.setInt(2, lineNo++);
                    allocation.setString(3, item.receivableCode());
                    allocation.setBigDecimal(4, item.amount());
                    allocation.addBatch();
                }
                allocation.executeBatch();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save receipt: " + receipt.code(), ex);
        }
    }

    @Override
    public CustomerReceipt registerReceipt(CustomerReceipt receipt, String idempotencyKey) {
        String key = idempotencyKey == null ? "" : idempotencyKey.trim();
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                if (!key.isEmpty()) {
                    CustomerReceipt existing = findReceiptByKey(connection, key);
                    if (existing != null) {
                        if (!sameReceipt(existing, receipt)) {
                            throw new BusinessException("同一幂等键的收款内容不一致");
                        }
                        connection.rollback();
                        return existing;
                    }
                }
                insertReceipt(connection, receipt, key);
                receiptProbe.beforeAllocationInsert();
                insertAllocations(connection, receipt);
                connection.commit();
                return receipt;
            } catch (RuntimeException | SQLException ex) {
                connection.rollback();
                if (ex instanceof SQLException sql && isDuplicate(sql) && !key.isEmpty()) {
                    try (Connection lookup = dataSource.getConnection()) {
                        CustomerReceipt existing = findReceiptByKey(lookup, key);
                        if (existing != null && sameReceipt(existing, receipt)) {
                            return existing;
                        }
                        if (existing != null) {
                            throw new BusinessException("同一幂等键的收款内容不一致");
                        }
                    }
                }
                if (ex instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException("收款事务失败", ex);
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("收款事务失败", ex);
        }
    }

    private void insertReceipt(Connection connection, CustomerReceipt receipt, String key) throws SQLException {
        String sql = """
                insert into fin_customer_receipt (
                    code, customer_id, customer_name, `year_month`, amount, received_on, operator_name, created_at,
                    idempotency_key
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, receipt.code());
            statement.setLong(2, receipt.customerId());
            statement.setString(3, receipt.customerName());
            statement.setString(4, receipt.yearMonth());
            statement.setBigDecimal(5, receipt.amount());
            statement.setString(6, receipt.receivedOn());
            statement.setString(7, receipt.operator());
            statement.setString(8, receipt.createdAt());
            if (key.isEmpty()) {
                statement.setNull(9, Types.VARCHAR);
            } else {
                statement.setString(9, key);
            }
            statement.executeUpdate();
        }
    }

    private void insertAllocations(Connection connection, CustomerReceipt receipt) throws SQLException {
        String sql = """
                insert into fin_receipt_allocation (receipt_code, line_no, receivable_code, amount)
                values (?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int lineNo = 1;
            for (ReceiptAllocation item : receipt.allocations()) {
                statement.setString(1, receipt.code());
                statement.setInt(2, lineNo++);
                statement.setString(3, item.receivableCode());
                statement.setBigDecimal(4, item.amount());
                statement.addBatch();
            }
            if (lineNo > 1) {
                statement.executeBatch();
            }
        }
    }

    private CustomerReceipt findReceiptByKey(Connection connection, String key) throws SQLException {
        String sql = """
                select code, customer_id, customer_name, `year_month`, amount, received_on, operator_name, created_at
                  from fin_customer_receipt
                 where idempotency_key = ?
                """;
        String code;
        long customerId;
        String customerName;
        String yearMonth;
        BigDecimal amount;
        String receivedOn;
        String operator;
        String createdAt;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                code = rs.getString("code");
                customerId = rs.getLong("customer_id");
                customerName = rs.getString("customer_name");
                yearMonth = rs.getString("year_month");
                amount = rs.getBigDecimal("amount");
                receivedOn = rs.getString("received_on");
                operator = rs.getString("operator_name");
                createdAt = rs.getString("created_at");
            }
        }
        return new CustomerReceipt(
                code,
                customerId,
                customerName,
                yearMonth,
                amount,
                receivedOn,
                loadAllocations(connection, code),
                operator,
                createdAt
        );
    }

    private List<ReceiptAllocation> loadAllocations(Connection connection, String receiptCode) throws SQLException {
        String sql = """
                select receivable_code, amount
                  from fin_receipt_allocation
                 where receipt_code = ?
                 order by line_no
                """;
        List<ReceiptAllocation> allocations = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, receiptCode);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    allocations.add(new ReceiptAllocation(rs.getString("receivable_code"), rs.getBigDecimal("amount")));
                }
            }
        }
        return allocations;
    }

    private boolean sameReceipt(CustomerReceipt existing, CustomerReceipt requested) {
        if (existing.customerId() != requested.customerId()
                || existing.amount().compareTo(requested.amount()) != 0
                || !existing.receivedOn().equals(requested.receivedOn())
                || !existing.customerName().equals(requested.customerName())
                || existing.allocations().size() != requested.allocations().size()) {
            return false;
        }
        List<ReceiptAllocation> left = existing.allocations().stream()
                .sorted(Comparator.comparing(ReceiptAllocation::receivableCode).thenComparing(ReceiptAllocation::amount))
                .toList();
        List<ReceiptAllocation> right = requested.allocations().stream()
                .sorted(Comparator.comparing(ReceiptAllocation::receivableCode).thenComparing(ReceiptAllocation::amount))
                .toList();
        for (int index = 0; index < left.size(); index++) {
            if (!left.get(index).receivableCode().equals(right.get(index).receivableCode())
                    || left.get(index).amount().compareTo(right.get(index).amount()) != 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<CustomerReceipt> listReceipts() {
        String sql = """
                select code, customer_id, customer_name, `year_month`, amount, received_on, operator_name, created_at
                  from fin_customer_receipt
                 order by code
                """;
        record Header(
                String code,
                long customerId,
                String customerName,
                String yearMonth,
                BigDecimal amount,
                String receivedOn,
                String operator,
                String createdAt
        ) {
        }
        List<Header> headers = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                headers.add(new Header(
                        rs.getString("code"),
                        rs.getLong("customer_id"),
                        rs.getString("customer_name"),
                        rs.getString("year_month"),
                        rs.getBigDecimal("amount"),
                        rs.getString("received_on"),
                        rs.getString("operator_name"),
                        rs.getString("created_at")
                ));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to list receipts", ex);
        }
        List<CustomerReceipt> receipts = new ArrayList<>();
        for (Header header : headers) {
            receipts.add(new CustomerReceipt(
                    header.code(),
                    header.customerId(),
                    header.customerName(),
                    header.yearMonth(),
                    header.amount(),
                    header.receivedOn(),
                    loadAllocations(header.code()),
                    header.operator(),
                    header.createdAt()
            ));
        }
        return receipts;
    }

    @Override
    public void saveWorkerRate(WorkerRate rate) {
        String update = """
                update fin_worker_rate
                   set process_name = ?, charge_unit = ?, unit_price = ?, effective_from = ?, effective_to = ?,
                       operator_name = ?, created_at = ?
                 where code = ?
                """;
        String insert = """
                insert into fin_worker_rate (
                    code, process_name, charge_unit, unit_price, effective_from, effective_to, operator_name, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection()) {
            int updated;
            try (PreparedStatement statement = connection.prepareStatement(update)) {
                bindRate(statement, rate, false);
                updated = statement.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement statement = connection.prepareStatement(insert)) {
                    bindRate(statement, rate, true);
                    statement.executeUpdate();
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save worker rate: " + rate.code(), ex);
        }
    }

    @Override
    public List<WorkerRate> listWorkerRates() {
        String sql = """
                select code, process_name, charge_unit, unit_price, effective_from, effective_to, operator_name, created_at
                  from fin_worker_rate
                 order by code
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<WorkerRate> rates = new ArrayList<>();
            while (rs.next()) {
                rates.add(new WorkerRate(
                        rs.getString("code"),
                        rs.getString("process_name"),
                        ChargeUnit.valueOf(rs.getString("charge_unit")),
                        rs.getBigDecimal("unit_price"),
                        rs.getString("effective_from"),
                        blank(rs.getString("effective_to")),
                        rs.getString("operator_name"),
                        rs.getString("created_at")
                ));
            }
            return rates;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to list worker rates", ex);
        }
    }

    @Override
    public void replacePayroll(String yearMonth, List<PayrollLine> lines) {
        String delete = "delete from fin_payroll_line where `year_month` = ? and source_key not like 'PAY-ADJ|%'";
        String insert = """
                insert into fin_payroll_line (
                    code, `year_month`, operator_name, order_code, process_name, mother_order_code, customer_id,
                    quantity, charge_unit, unit_price, gross_amount, loss_deduction, net_amount, confirmed, source_key
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(delete)) {
                statement.setString(1, yearMonth);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(insert)) {
                for (PayrollLine line : lines) {
                    statement.setString(1, line.code());
                    statement.setString(2, line.yearMonth());
                    statement.setString(3, line.operatorName());
                    statement.setString(4, line.orderCode());
                    statement.setString(5, line.processName());
                    statement.setString(6, line.motherOrderCode());
                    statement.setLong(7, line.customerId());
                    statement.setBigDecimal(8, line.quantity());
                    statement.setString(9, line.unit().name());
                    statement.setBigDecimal(10, line.unitPrice());
                    statement.setBigDecimal(11, line.grossAmount());
                    statement.setBigDecimal(12, line.lossDeduction());
                    statement.setBigDecimal(13, line.netAmount());
                    statement.setBoolean(14, line.confirmed());
                    statement.setString(15, line.sourceKey());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to replace payroll: " + yearMonth, ex);
        }
    }

    @Override
    public void savePayrollLine(PayrollLine line) {
        try (Connection connection = dataSource.getConnection()) {
            insertPayrollLine(connection, line);
        } catch (SQLException ex) {
            throw new IllegalStateException("无法追加工资调整", ex);
        }
    }

    @Override
    public void savePayrollLine(PayrollLine line, WorkSession session) {
        try {
            insertPayrollLine(requireSession(session), line);
        } catch (SQLException ex) {
            throw new IllegalStateException("无法追加工资调整", ex);
        }
    }

    private void insertPayrollLine(Connection connection, PayrollLine line) throws SQLException {
        String insert = """
                insert into fin_payroll_line (
                    code, `year_month`, operator_name, order_code, process_name, mother_order_code, customer_id,
                    quantity, charge_unit, unit_price, gross_amount, loss_deduction, net_amount, confirmed, source_key
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, line.code());
            statement.setString(2, line.yearMonth());
            statement.setString(3, line.operatorName());
            statement.setString(4, line.orderCode());
            statement.setString(5, line.processName());
            statement.setString(6, line.motherOrderCode());
            statement.setLong(7, line.customerId());
            statement.setBigDecimal(8, line.quantity());
            statement.setString(9, line.unit().name());
            statement.setBigDecimal(10, line.unitPrice());
            statement.setBigDecimal(11, line.grossAmount());
            statement.setBigDecimal(12, line.lossDeduction());
            statement.setBigDecimal(13, line.netAmount());
            statement.setBoolean(14, line.confirmed());
            statement.setString(15, line.sourceKey());
            statement.executeUpdate();
        }
    }

    @Override
    public List<PayrollLine> listPayrollLines() {
        String sql = "select * from fin_payroll_line order by code";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<PayrollLine> lines = new ArrayList<>();
            while (rs.next()) {
                lines.add(new PayrollLine(
                        rs.getString("code"),
                        rs.getString("year_month"),
                        rs.getString("operator_name"),
                        rs.getString("order_code"),
                        rs.getString("process_name"),
                        rs.getString("mother_order_code"),
                        rs.getLong("customer_id"),
                        rs.getBigDecimal("quantity"),
                        ChargeUnit.valueOf(rs.getString("charge_unit")),
                        rs.getBigDecimal("unit_price"),
                        rs.getBigDecimal("gross_amount"),
                        rs.getBigDecimal("loss_deduction"),
                        rs.getBigDecimal("net_amount"),
                        rs.getBoolean("confirmed"),
                        rs.getString("source_key")
                ));
            }
            return lines;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to list payroll lines", ex);
        }
    }

    @Override
    public Optional<PayrollRun> findPayrollRun(String yearMonth) {
        String sql = "select `year_month`, confirmed, confirmed_by, confirmed_at from fin_payroll_run where `year_month` = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, yearMonth);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PayrollRun(
                        rs.getString("year_month"),
                        rs.getBoolean("confirmed"),
                        rs.getString("confirmed_by"),
                        rs.getString("confirmed_at")
                ));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load payroll run: " + yearMonth, ex);
        }
    }

    @Override
    public void savePayrollRun(PayrollRun run) {
        String update = "update fin_payroll_run set confirmed = ?, confirmed_by = ?, confirmed_at = ? where `year_month` = ?";
        String insert = "insert into fin_payroll_run (`year_month`, confirmed, confirmed_by, confirmed_at) values (?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection()) {
            int updated;
            try (PreparedStatement statement = connection.prepareStatement(update)) {
                statement.setBoolean(1, run.confirmed());
                statement.setString(2, run.confirmedBy());
                statement.setString(3, run.confirmedAt());
                statement.setString(4, run.yearMonth());
                updated = statement.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement statement = connection.prepareStatement(insert)) {
                    statement.setString(1, run.yearMonth());
                    statement.setBoolean(2, run.confirmed());
                    statement.setString(3, run.confirmedBy());
                    statement.setString(4, run.confirmedAt());
                    statement.executeUpdate();
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save payroll run: " + run.yearMonth(), ex);
        }
    }

    @Override
    public void saveConsumableItem(ConsumableItem item) {
        String update = "update fin_consumable_item set item_name = ?, unit_name = ? where code = ?";
        String insert = "insert into fin_consumable_item (code, item_name, unit_name) values (?, ?, ?)";
        try (Connection connection = dataSource.getConnection()) {
            int updated;
            try (PreparedStatement statement = connection.prepareStatement(update)) {
                statement.setString(1, item.name());
                statement.setString(2, item.unitName());
                statement.setString(3, item.code());
                updated = statement.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement statement = connection.prepareStatement(insert)) {
                    statement.setString(1, item.code());
                    statement.setString(2, item.name());
                    statement.setString(3, item.unitName());
                    statement.executeUpdate();
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save consumable item: " + item.code(), ex);
        }
    }

    @Override
    public List<ConsumableItem> listConsumableItems() {
        String sql = "select code, item_name, unit_name from fin_consumable_item order by code";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<ConsumableItem> items = new ArrayList<>();
            while (rs.next()) {
                items.add(new ConsumableItem(rs.getString("code"), rs.getString("item_name"), rs.getString("unit_name")));
            }
            return items;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to list consumable items", ex);
        }
    }

    @Override
    public Optional<ConsumableItem> findConsumableItem(String code) {
        return listConsumableItems().stream().filter(item -> item.code().equals(code)).findFirst();
    }

    @Override
    public void saveConsumableMovement(ConsumableMovement movement) {
        String sql = """
                insert into fin_consumable_movement (
                    code, item_code, direction, quantity, amount, supplier_name, customer_id, `year_month`,
                    payable_code, operator_name, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, movement.code());
            statement.setString(2, movement.itemCode());
            statement.setString(3, movement.direction());
            statement.setBigDecimal(4, movement.quantity());
            statement.setBigDecimal(5, movement.amount());
            statement.setString(6, movement.supplier());
            setNullableLong(statement, 7, movement.customerId());
            statement.setString(8, movement.yearMonth());
            statement.setString(9, movement.payableCode());
            statement.setString(10, movement.operator());
            statement.setString(11, movement.createdAt());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save consumable movement: " + movement.code(), ex);
        }
    }

    @Override
    public List<ConsumableMovement> listConsumableMovements() {
        String sql = "select * from fin_consumable_movement order by code";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<ConsumableMovement> movements = new ArrayList<>();
            while (rs.next()) {
                movements.add(new ConsumableMovement(
                        rs.getString("code"),
                        rs.getString("item_code"),
                        rs.getString("direction"),
                        rs.getBigDecimal("quantity"),
                        rs.getBigDecimal("amount"),
                        rs.getString("supplier_name"),
                        nullableLong(rs, "customer_id"),
                        rs.getString("year_month"),
                        rs.getString("payable_code"),
                        rs.getString("operator_name"),
                        rs.getString("created_at")
                ));
            }
            return movements;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to list consumable movements", ex);
        }
    }

    @Override
    public void saveAsset(FixedAsset asset) {
        String sql = """
                insert into fin_fixed_asset (
                    code, asset_name, category_name, original_value, residual_value, start_month,
                    useful_months, operator_name, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, asset.code());
            statement.setString(2, asset.name());
            statement.setString(3, asset.category());
            statement.setBigDecimal(4, asset.originalValue());
            statement.setBigDecimal(5, asset.residualValue());
            statement.setString(6, asset.startMonth());
            statement.setInt(7, asset.usefulMonths());
            statement.setString(8, asset.operator());
            statement.setString(9, asset.createdAt());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save fixed asset: " + asset.code(), ex);
        }
    }

    @Override
    public List<FixedAsset> listAssets() {
        String sql = "select * from fin_fixed_asset order by code";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<FixedAsset> assets = new ArrayList<>();
            while (rs.next()) {
                assets.add(new FixedAsset(
                        rs.getString("code"),
                        rs.getString("asset_name"),
                        rs.getString("category_name"),
                        rs.getBigDecimal("original_value"),
                        rs.getBigDecimal("residual_value"),
                        rs.getString("start_month"),
                        rs.getInt("useful_months"),
                        rs.getString("operator_name"),
                        rs.getString("created_at")
                ));
            }
            return assets;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to list fixed assets", ex);
        }
    }

    @Override
    public void saveDepreciation(DepreciationEntry entry) {
        String sql = "insert into fin_depreciation_entry (code, asset_code, `year_month`, amount) values (?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entry.code());
            statement.setString(2, entry.assetCode());
            statement.setString(3, entry.yearMonth());
            statement.setBigDecimal(4, entry.amount());
            statement.executeUpdate();
        } catch (SQLException ex) {
            if (isDuplicate(ex)) {
                throw new BusinessException("这个月的折旧已经生成");
            }
            throw new IllegalStateException("Failed to save depreciation: " + entry.code(), ex);
        }
    }

    @Override
    public List<DepreciationEntry> listDepreciations() {
        String sql = "select code, asset_code, `year_month`, amount from fin_depreciation_entry order by code";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<DepreciationEntry> entries = new ArrayList<>();
            while (rs.next()) {
                entries.add(new DepreciationEntry(
                        rs.getString("code"),
                        rs.getString("asset_code"),
                        rs.getString("year_month"),
                        rs.getBigDecimal("amount")
                ));
            }
            return entries;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to list depreciation entries", ex);
        }
    }

    @Override
    public void savePayable(Payable payable) {
        try (Connection connection = dataSource.getConnection()) {
            writePayable(connection, payable);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save payable: " + payable.code(), ex);
        }
    }

    @Override
    public void savePayable(Payable payable, WorkSession session) {
        try {
            writePayable(requireSession(session), payable);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save payable: " + payable.code(), ex);
        }
    }

    private void writePayable(Connection connection, Payable payable) throws SQLException {
        String update = """
                update fin_payable
                   set kind = ?, `year_month` = ?, payee = ?, source_code = ?, customer_id = ?, mother_order_code = ?,
                       amount = ?, paid_amount = ?, operator_name = ?, created_at = ?
                 where code = ?
                """;
        String insert = """
                insert into fin_payable (
                    code, kind, `year_month`, payee, source_code, customer_id, mother_order_code, amount, paid_amount,
                    operator_name, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        int updated;
        try (PreparedStatement statement = connection.prepareStatement(update)) {
            bindPayable(statement, payable, false);
            updated = statement.executeUpdate();
        }
        if (updated == 0) {
            try (PreparedStatement statement = connection.prepareStatement(insert)) {
                bindPayable(statement, payable, true);
                statement.executeUpdate();
            }
        }
    }

    @Override
    public List<Payable> listPayables() {
        String sql = "select * from fin_payable order by code";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<Payable> payables = new ArrayList<>();
            while (rs.next()) {
                payables.add(mapPayable(rs));
            }
            return payables;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to list payables", ex);
        }
    }

    @Override
    public Optional<Payable> findPayable(String code) {
        return listPayables().stream().filter(payable -> payable.code().equals(code)).findFirst();
    }

    @Override
    public Optional<Payable> findPayableBySource(String sourceCode) {
        return listPayables().stream().filter(payable -> sourceCode.equals(payable.sourceCode())).findFirst();
    }

    @Override
    public PayablePayment registerPayablePayment(PayablePayment payment, String idempotencyKey) {
        String key = idempotencyKey == null ? "" : idempotencyKey.trim();
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                Payable payable = lockPayable(connection, payment.payableCode());
                if (!key.isEmpty()) {
                    PayablePayment existing = findPaymentByKey(connection, key);
                    if (existing != null) {
                        if (!samePayment(existing, payment)) {
                            throw new BusinessException("同一幂等键的付款内容不一致");
                        }
                        connection.rollback();
                        return existing;
                    }
                }
                if (payable == null) {
                    throw new BusinessException("找不到应付");
                }
                if (payable.paidAmount().add(payment.amount()).compareTo(payable.amount()) > 0) {
                    throw new BusinessException("付款金额不能超过尚未支付的余额");
                }
                paymentProbe.beforePaymentInsert();
                insertPayment(connection, payment, key);
                paymentProbe.beforeBalanceUpdate();
                if (addPaidAmount(connection, payable.code(), payment.amount()) != 1) {
                    throw new BusinessException("付款金额不能超过尚未支付的余额");
                }
                connection.commit();
                return payment;
            } catch (RuntimeException | SQLException ex) {
                connection.rollback();
                if (ex instanceof SQLException sql && isDuplicate(sql) && !key.isEmpty()) {
                    try (Connection lookup = dataSource.getConnection()) {
                        PayablePayment existing = findPaymentByKey(lookup, key);
                        if (existing != null && samePayment(existing, payment)) {
                            return existing;
                        }
                        if (existing != null) {
                            throw new BusinessException("同一幂等键的付款内容不一致");
                        }
                    }
                }
                if (ex instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException("付款事务失败", ex);
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("付款事务失败", ex);
        }
    }

    private Payable lockPayable(Connection connection, String code) throws SQLException {
        String sql = "select * from fin_payable where code = ? for update";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return mapPayable(rs);
            }
        }
    }

    private PayablePayment findPaymentByKey(Connection connection, String key) throws SQLException {
        String sql = """
                select code, payable_code, amount, paid_on, operator_name, created_at
                  from fin_payable_payment
                 where idempotency_key = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new PayablePayment(
                        rs.getString("code"),
                        rs.getString("payable_code"),
                        rs.getBigDecimal("amount"),
                        rs.getString("paid_on"),
                        rs.getString("operator_name"),
                        rs.getString("created_at")
                );
            }
        }
    }

    private void insertPayment(Connection connection, PayablePayment payment, String key) throws SQLException {
        String sql = """
                insert into fin_payable_payment (
                    code, payable_code, amount, paid_on, operator_name, created_at, idempotency_key
                ) values (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, payment.code());
            statement.setString(2, payment.payableCode());
            statement.setBigDecimal(3, payment.amount());
            statement.setString(4, payment.paidOn());
            statement.setString(5, payment.operator());
            statement.setString(6, payment.createdAt());
            if (key.isEmpty()) {
                statement.setNull(7, Types.VARCHAR);
            } else {
                statement.setString(7, key);
            }
            statement.executeUpdate();
        }
    }

    private int addPaidAmount(Connection connection, String payableCode, BigDecimal amount) throws SQLException {
        String sql = """
                update fin_payable
                   set paid_amount = paid_amount + ?
                 where code = ?
                   and paid_amount + ? <= amount
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBigDecimal(1, amount);
            statement.setString(2, payableCode);
            statement.setBigDecimal(3, amount);
            return statement.executeUpdate();
        }
    }

    private boolean samePayment(PayablePayment existing, PayablePayment requested) {
        return existing.payableCode().equals(requested.payableCode())
                && existing.amount().compareTo(requested.amount()) == 0
                && existing.paidOn().equals(requested.paidOn());
    }

    @Override
    public void savePayablePayment(PayablePayment payment) {
        String sql = """
                insert into fin_payable_payment (code, payable_code, amount, paid_on, operator_name, created_at)
                values (?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, payment.code());
            statement.setString(2, payment.payableCode());
            statement.setBigDecimal(3, payment.amount());
            statement.setString(4, payment.paidOn());
            statement.setString(5, payment.operator());
            statement.setString(6, payment.createdAt());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save payable payment: " + payment.code(), ex);
        }
    }

    @Override
    public List<PayablePayment> listPayablePayments() {
        String sql = "select code, payable_code, amount, paid_on, operator_name, created_at from fin_payable_payment order by code";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<PayablePayment> payments = new ArrayList<>();
            while (rs.next()) {
                payments.add(new PayablePayment(
                        rs.getString("code"),
                        rs.getString("payable_code"),
                        rs.getBigDecimal("amount"),
                        rs.getString("paid_on"),
                        rs.getString("operator_name"),
                        rs.getString("created_at")
                ));
            }
            return payments;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to list payable payments", ex);
        }
    }

    private void bindAgreement(PreparedStatement statement, PriceAgreement agreement, boolean insert) throws SQLException {
        if (insert) {
            statement.setString(1, agreement.code());
            statement.setLong(2, agreement.customerId());
            statement.setString(3, agreement.customerName());
            statement.setString(4, agreement.mode().name());
            statement.setString(5, agreement.effectiveFrom());
            setNullableString(statement, 6, agreement.effectiveTo());
            statement.setString(7, agreement.operator());
            statement.setString(8, agreement.createdAt());
            return;
        }
        statement.setLong(1, agreement.customerId());
        statement.setString(2, agreement.customerName());
        statement.setString(3, agreement.mode().name());
        statement.setString(4, agreement.effectiveFrom());
        setNullableString(statement, 5, agreement.effectiveTo());
        statement.setString(6, agreement.operator());
        statement.setString(7, agreement.createdAt());
        statement.setString(8, agreement.code());
    }

    private void bindRate(PreparedStatement statement, WorkerRate rate, boolean insert) throws SQLException {
        if (insert) {
            statement.setString(1, rate.code());
            statement.setString(2, rate.processName());
            statement.setString(3, rate.unit().name());
            statement.setBigDecimal(4, rate.unitPrice());
            statement.setString(5, rate.effectiveFrom());
            setNullableString(statement, 6, rate.effectiveTo());
            statement.setString(7, rate.operator());
            statement.setString(8, rate.createdAt());
            return;
        }
        statement.setString(1, rate.processName());
        statement.setString(2, rate.unit().name());
        statement.setBigDecimal(3, rate.unitPrice());
        statement.setString(4, rate.effectiveFrom());
        setNullableString(statement, 5, rate.effectiveTo());
        statement.setString(6, rate.operator());
        statement.setString(7, rate.createdAt());
        statement.setString(8, rate.code());
    }

    private void bindPayable(PreparedStatement statement, Payable payable, boolean insert) throws SQLException {
        if (insert) {
            statement.setString(1, payable.code());
            statement.setString(2, payable.kind());
            statement.setString(3, payable.yearMonth());
            statement.setString(4, payable.payee());
            statement.setString(5, payable.sourceCode());
            setNullableLong(statement, 6, payable.customerId());
            statement.setString(7, payable.motherOrderCode());
            statement.setBigDecimal(8, payable.amount());
            statement.setBigDecimal(9, payable.paidAmount());
            statement.setString(10, payable.operator());
            statement.setString(11, payable.createdAt());
            return;
        }
        statement.setString(1, payable.kind());
        statement.setString(2, payable.yearMonth());
        statement.setString(3, payable.payee());
        statement.setString(4, payable.sourceCode());
        setNullableLong(statement, 5, payable.customerId());
        statement.setString(6, payable.motherOrderCode());
        statement.setBigDecimal(7, payable.amount());
        statement.setBigDecimal(8, payable.paidAmount());
        statement.setString(9, payable.operator());
        statement.setString(10, payable.createdAt());
        statement.setString(11, payable.code());
    }

    private List<PriceLine> loadPriceLines(String agreementCode) {
        String sql = """
                select process_name, charge_unit, unit_price
                  from fin_price_line
                 where agreement_code = ?
                 order by line_no
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, agreementCode);
            try (ResultSet rs = statement.executeQuery()) {
                List<PriceLine> lines = new ArrayList<>();
                while (rs.next()) {
                    lines.add(new PriceLine(
                            blank(rs.getString("process_name")),
                            ChargeUnit.valueOf(rs.getString("charge_unit")),
                            rs.getBigDecimal("unit_price")
                    ));
                }
                return lines;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load price lines: " + agreementCode, ex);
        }
    }

    private List<ReceiptAllocation> loadAllocations(String receiptCode) {
        String sql = """
                select receivable_code, amount
                  from fin_receipt_allocation
                 where receipt_code = ?
                 order by line_no
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, receiptCode);
            try (ResultSet rs = statement.executeQuery()) {
                List<ReceiptAllocation> allocations = new ArrayList<>();
                while (rs.next()) {
                    allocations.add(new ReceiptAllocation(rs.getString("receivable_code"), rs.getBigDecimal("amount")));
                }
                return allocations;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load receipt allocations: " + receiptCode, ex);
        }
    }

    private List<ReceivableLine> queryReceivables(String sql, String... params) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < params.length; index++) {
                statement.setString(index + 1, params[index]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<ReceivableLine> lines = new ArrayList<>();
                while (rs.next()) {
                    lines.add(new ReceivableLine(
                            rs.getString("code"),
                            rs.getLong("customer_id"),
                            rs.getString("customer_name"),
                            rs.getString("year_month"),
                            rs.getString("source_type"),
                            rs.getString("source_key"),
                            rs.getString("mother_order_code"),
                            rs.getString("work_order_code"),
                            rs.getString("process_name"),
                            rs.getBigDecimal("quantity"),
                            ChargeUnit.valueOf(rs.getString("charge_unit")),
                            rs.getBigDecimal("unit_price"),
                            rs.getBigDecimal("amount"),
                            rs.getString("reverses_code"),
                            rs.getString("remark"),
                            rs.getString("operator_name"),
                            rs.getString("created_at")
                    ));
                }
                return lines;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to query receivables", ex);
        }
    }

    private Payable mapPayable(ResultSet rs) throws SQLException {
        return new Payable(
                rs.getString("code"),
                rs.getString("kind"),
                rs.getString("year_month"),
                rs.getString("payee"),
                rs.getString("source_code"),
                nullableLong(rs, "customer_id"),
                rs.getString("mother_order_code"),
                rs.getBigDecimal("amount"),
                rs.getBigDecimal("paid_amount"),
                rs.getString("operator_name"),
                rs.getString("created_at")
        );
    }

    private void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            statement.setNull(index, Types.VARCHAR);
            return;
        }
        statement.setString(index, value);
    }

    private void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
            return;
        }
        statement.setLong(index, value);
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }

    private boolean isDuplicate(SQLException ex) {
        return ex.getErrorCode() == 1062 || ex.getErrorCode() == 23505
                || (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("unique"));
    }
}
