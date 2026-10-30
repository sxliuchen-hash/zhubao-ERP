package com.zhubao.erp.scan.domain;

import com.zhubao.erp.common.error.BusinessException;

import java.util.List;

/**
 * 不可枚举的公开访问标识。停用后业务交付仍在。
 */
public record PublicDeliveryAccess(
        String accessId,
        String deliveryId,
        long customerId,
        List<String> bagCodes,
        List<String> publicPhotoIds,
        boolean enabled,
        int version
) {
    public PublicDeliveryAccess {
        if (accessId == null || !accessId.matches("^[0-9a-f]{64}$")) {
            throw new BusinessException("公开访问标识无效");
        }
        if (deliveryId == null || deliveryId.isBlank()) {
            throw new BusinessException("交付草稿不存在");
        }
        deliveryId = deliveryId.trim();
        bagCodes = bagCodes == null ? List.of() : List.copyOf(bagCodes);
        publicPhotoIds = publicPhotoIds == null ? List.of() : List.copyOf(publicPhotoIds);
        for (String photoId : publicPhotoIds) {
            if (photoId == null || photoId.isBlank() || photoId.indexOf('/') >= 0 || photoId.indexOf('\\') >= 0
                    || photoId.indexOf(':') >= 0 || photoId.indexOf(',') >= 0) {
                throw new BusinessException("公开图片不能使用内部地址");
            }
        }
        if (version < 1) {
            throw new BusinessException("公开访问版本不正确");
        }
    }
}
