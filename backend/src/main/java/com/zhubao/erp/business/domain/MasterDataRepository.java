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
    default List<MaterialStorageChange> storageChanges(String materialCode) { return List.of(); }
    List<RequirementShortcut> listShortcuts();
    RequirementShortcut saveShortcut(RequirementShortcut shortcut);
    Optional<RequirementShortcut> deleteShortcut(long shortcutId);
    List<RequirementTemplate> listTemplates();
    Optional<RequirementTemplate> findTemplate(String templateCode);
    Optional<RequirementTemplate> findTemplateByRequest(String requestId);
    RequirementTemplate insertTemplate(RequirementTemplate template);
    RequirementTemplate updateTemplate(RequirementTemplate template, int expectedVersion);
}
