package com.zhubao.erp.business.domain;

import java.util.List;
import java.util.Optional;

public interface MasterDataRepository {
    List<CustomerProfile> listCustomers();
    Optional<CustomerProfile> findCustomer(long id);
    CustomerProfile saveCustomer(CustomerProfile profile);
    List<MaterialProfile> listMaterials();
    Optional<MaterialProfile> findMaterial(String code);
    MaterialProfile saveMaterial(MaterialProfile profile);
    List<RequirementShortcut> listShortcuts();
    RequirementShortcut saveShortcut(RequirementShortcut shortcut);
    Optional<RequirementShortcut> deleteShortcut(long shortcutId);
}
