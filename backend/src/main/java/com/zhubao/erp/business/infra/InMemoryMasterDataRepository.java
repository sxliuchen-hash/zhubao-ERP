package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.domain.MasterDataRepository;
import com.zhubao.erp.business.domain.MaterialProfile;
import com.zhubao.erp.business.domain.RequirementShortcut;
import com.zhubao.erp.business.domain.RequirementTemplate;
import com.zhubao.erp.common.error.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
@ConditionalOnProperty(name = "zhubao.master-data.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryMasterDataRepository implements MasterDataRepository {
    private final java.util.List<com.zhubao.erp.business.domain.MaterialStorageChange> storageChanges = new java.util.ArrayList<>();
    @Override public synchronized java.util.List<com.zhubao.erp.business.domain.MaterialStorageChange> storageChanges(String code) {
        return storageChanges.stream().filter(item -> item.materialCode().equals(code)).toList();
    }
    private final ConcurrentHashMap<Long, CustomerProfile> customers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MaterialProfile> materials = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, RequirementShortcut> shortcuts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RequirementTemplate> templates = new ConcurrentHashMap<>();
    private final AtomicLong nextCustomerId = new AtomicLong(1004);
    private final AtomicLong nextShortcutId = new AtomicLong(0);
    private final AtomicLong nextTemplateId = new AtomicLong(2);

    public InMemoryMasterDataRepository() {
        saveCustomer(new CustomerProfile(1001, "C-1001", "恒信珠宝", "", "", "", "", true));
        saveCustomer(new CustomerProfile(1002, "C-1002", "雅辰饰品", "", "", "", "", true));
        saveCustomer(new CustomerProfile(1003, "C-1003", "璟瑞工贸", "", "", "", "", true));
        saveCustomer(new CustomerProfile(1004, "C-1004", "瑞禾珠宝", "", "", "", "", true));
        saveMaterial(new MaterialProfile("AU9999", "足金", "黄金", "", true));
        templates.put("TPL-BEAD-BASIC", new RequirementTemplate(1, "TPL-BEAD-BASIC", "常规珠型加工",
                List.of("开粗", "车形（车成珠型）", "抛光"), true, 1, 1, ""));
        templates.put("TPL-BEAD-CARVE", new RequirementTemplate(2, "TPL-BEAD-CARVE", "珠型雕刻加工",
                List.of("开粗", "车形（车成珠型）", "雕刻", "抛光"), true, 2, 1, ""));
    }

    @Override
    public List<CustomerProfile> listCustomers() {
        return customers.values().stream().sorted(Comparator.comparingLong(CustomerProfile::customerId)).toList();
    }

    @Override
    public Optional<CustomerProfile> findCustomer(long id) { return Optional.ofNullable(customers.get(id)); }

    @Override
    public synchronized CustomerProfile saveCustomer(CustomerProfile profile) {
        for (CustomerProfile existing : customers.values()) {
            if (existing.customerId() != profile.customerId()
                    && existing.name().trim().equalsIgnoreCase(profile.name().trim())) {
                throw new BusinessException("客户名称已存在，请核对客户档案");
            }
        }
        long id = profile.customerId() > 0 ? profile.customerId() : nextCustomerId.incrementAndGet();
        CustomerProfile saved = new CustomerProfile(id, profile.customerCode().isBlank() ? "C-" + id : profile.customerCode(),
                profile.name(), profile.aliases(), profile.contact(), profile.phone(), profile.remark(), profile.enabled());
        customers.put(id, saved);
        return saved;
    }

    @Override
    public List<MaterialProfile> listMaterials() {
        return materials.values().stream().sorted(Comparator.comparing(MaterialProfile::name)).toList();
    }

    @Override
    public Optional<MaterialProfile> findMaterial(String code) { return Optional.ofNullable(materials.get(code)); }

    @Override
    public synchronized MaterialProfile saveMaterial(MaterialProfile profile) {
        for (MaterialProfile existing : materials.values()) {
            if (!existing.materialCode().equals(profile.materialCode())
                    && existing.name().trim().equalsIgnoreCase(profile.name().trim())) {
                throw new BusinessException("材质名称已存在，请选择现有材质");
            }
        }
        MaterialProfile previous = materials.get(profile.materialCode());
        if (!java.util.Objects.equals(com.zhubao.erp.business.domain.StorageRequirements.of(previous), com.zhubao.erp.business.domain.StorageRequirements.of(profile))) {
            storageChanges.add(new com.zhubao.erp.business.domain.MaterialStorageChange(profile.materialCode(),
                    com.zhubao.erp.business.domain.StorageRequirements.of(previous), com.zhubao.erp.business.domain.StorageRequirements.of(profile), java.time.LocalDateTime.now().toString()));
        }
        materials.put(profile.materialCode(), profile);
        return profile;
    }

    @Override
    public List<RequirementShortcut> listShortcuts() {
        return shortcuts.values().stream()
                .sorted(Comparator.comparingInt(RequirementShortcut::sortOrder).thenComparingLong(RequirementShortcut::shortcutId))
                .toList();
    }

    @Override
    public synchronized RequirementShortcut saveShortcut(RequirementShortcut shortcut) {
        long id = shortcut.shortcutId() > 0 ? shortcut.shortcutId() : nextShortcutId.incrementAndGet();
        RequirementShortcut saved = new RequirementShortcut(id, shortcut.phrase(), shortcut.sortOrder());
        shortcuts.put(id, saved);
        return saved;
    }

    @Override
    public synchronized Optional<RequirementShortcut> deleteShortcut(long shortcutId) {
        return Optional.ofNullable(shortcuts.remove(shortcutId));
    }

    @Override
    public List<RequirementTemplate> listTemplates() {
        return templates.values().stream()
                .sorted(Comparator.comparingInt(RequirementTemplate::sortOrder).thenComparingLong(RequirementTemplate::templateId))
                .toList();
    }

    @Override
    public Optional<RequirementTemplate> findTemplate(String templateCode) {
        return Optional.ofNullable(templates.get(templateCode));
    }

    @Override
    public Optional<RequirementTemplate> findTemplateByRequest(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return Optional.empty();
        }
        return templates.values().stream().filter(template -> requestId.equals(template.requestId())).findFirst();
    }

    @Override
    public synchronized RequirementTemplate insertTemplate(RequirementTemplate template) {
        for (RequirementTemplate existing : templates.values()) {
            if (!template.requestId().isBlank() && template.requestId().equals(existing.requestId())) {
                if (!existing.name().equals(template.name()) || !existing.steps().equals(template.steps())
                        || existing.sortOrder() != template.sortOrder()) {
                    throw new BusinessException("同一请求的模板内容不一致");
                }
                return existing;
            }
            if (existing.name().equalsIgnoreCase(template.name()) || existing.templateCode().equals(template.templateCode())) {
                throw new BusinessException("模板名称或编号已存在");
            }
        }
        long id = nextTemplateId.incrementAndGet();
        RequirementTemplate saved = new RequirementTemplate(id, template.templateCode(), template.name(), template.steps(),
                template.enabled(), template.sortOrder(), template.version(), template.requestId());
        templates.put(saved.templateCode(), saved);
        return saved;
    }

    @Override
    public synchronized RequirementTemplate updateTemplate(RequirementTemplate template, int expectedVersion) {
        RequirementTemplate current = templates.get(template.templateCode());
        if (current == null) {
            throw new BusinessException("加工要求模板不存在");
        }
        if (current.version() != expectedVersion) {
            throw new BusinessException("模板已被其他人修改，请刷新后再保存");
        }
        if (templates.values().stream().anyMatch(existing -> !existing.templateCode().equals(template.templateCode())
                && existing.name().equalsIgnoreCase(template.name()))) {
            throw new BusinessException("模板名称已存在");
        }
        RequirementTemplate saved = new RequirementTemplate(current.templateId(), current.templateCode(), template.name(),
                template.steps(), template.enabled(), template.sortOrder(), current.version() + 1, current.requestId());
        templates.put(saved.templateCode(), saved);
        return saved;
    }
}
