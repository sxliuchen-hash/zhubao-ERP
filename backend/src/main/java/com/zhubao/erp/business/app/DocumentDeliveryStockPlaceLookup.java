package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.business.domain.BusinessTargetRole;
import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.DeliveryStockPlace;
import com.zhubao.erp.business.domain.DeliveryStockPlaceLookup;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.OwnerType;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 从已经存在的母单或子单读取成品位置。打样号和金工号不查母单。
 */
@Component
public class DocumentDeliveryStockPlaceLookup implements DeliveryStockPlaceLookup {

    private final BusinessDocumentRepository documents;

    public DocumentDeliveryStockPlaceLookup(BusinessDocumentRepository documents) {
        this.documents = documents;
    }

    @Override
    public Optional<DeliveryStockPlace> place(BusinessTargetReference target) {
        if (target == null || target.kind() != BusinessKind.PROCESSING) {
            return Optional.empty();
        }
        if (target.role() == BusinessTargetRole.MOTHER) {
            return documents.findMotherOrderByCode(target.code()).map(DocumentDeliveryStockPlaceLookup::fromMother);
        }
        if (target.role() == BusinessTargetRole.WORK_ORDER) {
            return documents.findChildWorkOrderByCode(target.code()).map(DocumentDeliveryStockPlaceLookup::fromChild);
        }
        return Optional.empty();
    }

    private static DeliveryStockPlace fromMother(MotherOrderRecord order) {
        if (order.customerId() == null) {
            throw new BusinessException("加工目标缺少客户");
        }
        long owner = order.ownerCustomerId() == null ? order.customerId() : order.ownerCustomerId();
        return new DeliveryStockPlace(order.code(), order.code(), order.customerId(), owner,
                ownerType(order.ownerType(), order.customerOwned()), warehouse(order.warehouseCode()), order.materialCode());
    }

    private static DeliveryStockPlace fromChild(ChildWorkOrderRecord order) {
        if (order.customerId() == null) {
            throw new BusinessException("加工目标缺少客户");
        }
        return new DeliveryStockPlace(order.motherOrderCode(), order.code(), order.customerId(), order.customerId(),
                OwnerType.CUSTOMER, warehouse(order.warehouseCode()), order.materialCode());
    }

    private static OwnerType ownerType(String raw, boolean customerOwned) {
        if ("COMPANY".equals(raw)) {
            return OwnerType.COMPANY;
        }
        if ("CUSTOMER".equals(raw) || customerOwned) {
            return OwnerType.CUSTOMER;
        }
        throw new BusinessException("缺少物料权属");
    }

    private static String warehouse(String warehouseCode) {
        String warehouse = warehouseCode == null ? "" : warehouseCode.trim();
        if (warehouse.isEmpty() || warehouse.length() > 50) {
            throw new BusinessException("加工目标缺少成品仓库");
        }
        return warehouse;
    }
}
