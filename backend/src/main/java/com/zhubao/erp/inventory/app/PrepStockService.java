package com.zhubao.erp.inventory.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.BusinessWork;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 独立客户备料和公司自有生产物料。不创建加工母单，财务耗材不进这本台账。
 */
@Service
public class PrepStockService {

    private final StockLedgerService stocks;
    private final BusinessDocumentRepository documents;
    private final BusinessWorkUnit workUnit;

    public PrepStockService(StockLedgerService stocks, BusinessDocumentRepository documents, BusinessWorkUnit workUnit) {
        this.stocks = stocks;
        this.documents = documents;
        this.workUnit = workUnit;
    }

    public List<StockBalance> customerPrep(AuthUserAccount account) {
        requireAccount(account);
        return stocks.balances().stream()
                .filter(balance -> balance.dimension().ownerType() == OwnerType.CUSTOMER)
                .filter(balance -> !balance.dimension().batchCode().isBlank())
                .filter(balance -> balance.dimension().motherOrderCode().isBlank())
                .filter(balance -> visibleCustomer(account, balance.dimension().customerId()))
                .toList();
    }

    public List<StockBalance> factoryStock(AuthUserAccount account) {
        requireAccount(account);
        return stocks.balances().stream()
                .filter(balance -> balance.dimension().stockStatus() != StockStatus.SHIPPED)
                .filter(balance -> balance.quantity().signum() > 0 || balance.weight().signum() > 0)
                .filter(balance -> visibleBalance(account, balance))
                .toList();
    }

    public List<StockBalance> companyStock(AuthUserAccount account) {
        requireCompanyReader(account);
        return stocks.balances().stream()
                .filter(balance -> balance.dimension().ownerType() == OwnerType.COMPANY)
                .toList();
    }

    public void receiveCustomerPrep(
            Long customerId,
            String materialCode,
            String warehouseCode,
            BigDecimal quantity,
            BigDecimal weight,
            String batchCode,
            String requestId,
            AuthUserAccount account
    ) {
        requireAccount(account);
        assertCustomer(account, customerId);
        StockDimension to = prepDimension(customerId, materialCode, warehouseCode, batchCode);
        run(requestId, account, "PREP_RECEIVE", batchCode, payload(customerId, materialCode, quantity, weight, batchCode, ""),
                session -> {
                    stocks.transfer(new StockTransferCommand(
                            "prep_receipt", batchCode, null, to, quantity, weight, "客户备料收货"
                    ), session);
                    return "prep-received";
                });
    }

    public void issueToMother(
            Long customerId,
            String materialCode,
            String warehouseCode,
            String batchCode,
            String motherOrderCode,
            BigDecimal quantity,
            BigDecimal weight,
            String requestId,
            AuthUserAccount account
    ) {
        requireAccount(account);
        MotherOrderRecord mother = documents.findMotherOrderByCode(motherOrderCode)
                .orElseThrow(() -> new BusinessException("加工母单不存在：" + motherOrderCode));
        if (mother.customerId() == null || !mother.customerId().equals(customerId)) {
            throw new PermissionDeniedException("不能把备料领用到其他客户的加工单");
        }
        assertCustomer(account, customerId);
        StockDimension from = prepDimension(customerId, materialCode, warehouseCode, batchCode);
        StockDimension to = new StockDimension(
                OwnerType.CUSTOMER, customerId, customerId, mother.code(), null,
                materialCode, mother.warehouseCode(), StockStatus.RAW_WAITING
        );
        run(requestId, account, "PREP_ISSUE", mother.code(), payload(customerId, materialCode, quantity, weight, batchCode, mother.code()),
                session -> {
                    stocks.transfer(new StockTransferCommand(
                            "prep_issue", mother.code(), from, to, quantity, weight, "备料领用到加工单"
                    ), session);
                    return "prep-issued";
                });
    }

    /** 从加工母单待加工余额退回原备料批次，追加反向台账。 */
    public void returnToBatch(
            Long customerId,
            String materialCode,
            String warehouseCode,
            String batchCode,
            String motherOrderCode,
            BigDecimal quantity,
            BigDecimal weight,
            String requestId,
            AuthUserAccount account
    ) {
        requireAccount(account);
        MotherOrderRecord mother = documents.findMotherOrderByCode(motherOrderCode)
                .orElseThrow(() -> new BusinessException("加工母单不存在：" + motherOrderCode));
        if (mother.customerId() == null || !mother.customerId().equals(customerId)) {
            throw new PermissionDeniedException("不能把其他客户的加工料退回备料");
        }
        assertCustomer(account, customerId);
        StockDimension from = new StockDimension(
                OwnerType.CUSTOMER, customerId, customerId, mother.code(), null,
                materialCode, mother.warehouseCode(), StockStatus.RAW_WAITING
        );
        StockDimension to = prepDimension(customerId, materialCode, warehouseCode, batchCode);
        run(requestId, account, "PREP_RETURN", mother.code(), payload(customerId, materialCode, quantity, weight, batchCode, mother.code()),
                session -> {
                    stocks.transfer(new StockTransferCommand(
                            "prep_return", mother.code(), from, to, quantity, weight, "备料从加工单退回"
                    ), session);
                    return "prep-returned";
                });
    }

    public void receiveCompany(
            String materialCode,
            String warehouseCode,
            BigDecimal quantity,
            BigDecimal weight,
            String batchCode,
            String requestId,
            AuthUserAccount account
    ) {
        requireCompanyReader(account);
        StockDimension to = new StockDimension(
                OwnerType.COMPANY, null, null, "", null, materialCode, warehouseCode, StockStatus.RAW_WAITING, batchCode
        );
        run(requestId, account, "COMPANY_RECEIVE", batchCode, payload(null, materialCode, quantity, weight, batchCode, ""),
                session -> {
                    stocks.transfer(new StockTransferCommand(
                            "company_receipt", batchCode, null, to, quantity, weight, "公司自有生产物料入库"
                    ), session);
                    return "company-received";
                });
    }

    /** 公司料领用仍保持公司权属，只把待加工批次转入在制。 */
    public void issueCompany(
            String materialCode,
            String warehouseCode,
            BigDecimal quantity,
            BigDecimal weight,
            String batchCode,
            String requestId,
            AuthUserAccount account
    ) {
        moveCompany(materialCode, warehouseCode, quantity, weight, batchCode, requestId, account, true);
    }

    /** 公司料退回仍保持公司权属，从在制退回待加工批次。 */
    public void returnCompany(
            String materialCode,
            String warehouseCode,
            BigDecimal quantity,
            BigDecimal weight,
            String batchCode,
            String requestId,
            AuthUserAccount account
    ) {
        moveCompany(materialCode, warehouseCode, quantity, weight, batchCode, requestId, account, false);
    }

    private void moveCompany(
            String materialCode,
            String warehouseCode,
            BigDecimal quantity,
            BigDecimal weight,
            String batchCode,
            String requestId,
            AuthUserAccount account,
            boolean issue
    ) {
        requireCompanyReader(account);
        if (batchCode == null || batchCode.isBlank()) {
            throw new BusinessException("公司料批次不能为空");
        }
        StockDimension raw = new StockDimension(
                OwnerType.COMPANY, null, null, "", null, materialCode, warehouseCode, StockStatus.RAW_WAITING, batchCode.trim()
        );
        StockDimension wip = new StockDimension(
                OwnerType.COMPANY, null, null, "", null, materialCode, warehouseCode, StockStatus.WIP, batchCode.trim()
        );
        StockDimension from = issue ? raw : wip;
        StockDimension to = issue ? wip : raw;
        String action = issue ? "COMPANY_ISSUE" : "COMPANY_RETURN";
        String documentType = issue ? "company_issue" : "company_return";
        String remark = issue ? "公司自有生产物料领用" : "公司自有生产物料退回";
        run(requestId, account, action, batchCode.trim(), payload(null, materialCode, quantity, weight, batchCode, ""),
                session -> {
                    stocks.transfer(new StockTransferCommand(documentType, batchCode.trim(), from, to, quantity, weight, remark), session);
                    return issue ? "company-issued" : "company-returned";
                });
    }

    private StockDimension prepDimension(Long customerId, String materialCode, String warehouseCode, String batchCode) {
        if (batchCode == null || batchCode.isBlank()) {
            throw new BusinessException("备料批次不能为空");
        }
        return new StockDimension(
                OwnerType.CUSTOMER, customerId, customerId, "", null,
                materialCode, warehouseCode, StockStatus.RAW_WAITING, batchCode.trim()
        );
    }

    private void run(String requestId, AuthUserAccount account, String action, String target, String payload, BusinessWork work) {
        if (requestId == null || requestId.isBlank()) {
            throw new BusinessException("库存操作必须带请求编号");
        }
        if (workUnit == null) {
            throw new BusinessException("库存回放尚未装配");
        }
        workUnit.execute(new CommandCall(requestId, account.operator().trim(), action, "STOCK", target, payload), work);
    }

    private void assertCustomer(AuthUserAccount account, Long customerId) {
        if (!visibleCustomer(account, customerId)) {
            throw new PermissionDeniedException("客户不在当前账号的授权范围内");
        }
    }

    private boolean visibleCustomer(AuthUserAccount account, Long customerId) {
        if (account.dataScope() != DataScope.CUSTOMER_RANGE) {
            return true;
        }
        return account.customerScope().stream().anyMatch(value ->
                value.equals(String.valueOf(customerId)) || value.equals("客户#" + customerId));
    }

    private boolean visibleBalance(AuthUserAccount account, StockBalance balance) {
        if (balance.dimension().ownerType() == OwnerType.COMPANY) {
            return canReadCompany(account);
        }
        return visibleCustomer(account, balance.dimension().customerId());
    }

    private void requireCompanyReader(AuthUserAccount account) {
        requireAccount(account);
        if (!canReadCompany(account)) {
            throw new PermissionDeniedException("公司自有物料仅仓库、财务或管理员可以查看");
        }
    }

    private boolean canReadCompany(AuthUserAccount account) {
        return account.role() == RoleCode.ADMIN
                || account.role() == RoleCode.WAREHOUSE_KEEPER
                || account.role() == RoleCode.FINANCE_STATISTICIAN
                || account.role() == RoleCode.GENERAL_MANAGER;
    }

    private void requireAccount(AuthUserAccount account) {
        if (account == null || account.operator() == null || account.operator().isBlank()) {
            throw new BusinessException("请先登录后再操作库存");
        }
    }

    private String payload(Long customerId, String materialCode, BigDecimal quantity, BigDecimal weight, String batchCode, String mother) {
        return "customer=" + (customerId == null ? "" : customerId)
                + ";material=" + materialCode
                + ";qty=" + quantity.stripTrailingZeros().toPlainString()
                + ";weight=" + weight.stripTrailingZeros().toPlainString()
                + ";batch=" + batchCode
                + ";mother=" + mother;
    }
}
