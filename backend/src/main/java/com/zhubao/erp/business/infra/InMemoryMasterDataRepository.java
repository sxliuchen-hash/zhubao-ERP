package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.domain.MasterDataRepository;
import com.zhubao.erp.business.domain.MaterialProfile;
import com.zhubao.erp.business.domain.RequirementShortcut;
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
    private final ConcurrentHashMap<Long, CustomerProfile> customers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MaterialProfile> materials = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, RequirementShortcut> shortcuts = new ConcurrentHashMap<>();
    private final AtomicLong nextCustomerId = new AtomicLong(1004);
    private final AtomicLong nextShortcutId = new AtomicLong(0);

    public InMemoryMasterDataRepository() {
        saveCustomer(new CustomerProfile(1001, "C-1001", "恒信珠宝", "", "", "", "", true));
        saveCustomer(new CustomerProfile(1002, "C-1002", "雅辰饰品", "", "", "", "", true));
        saveCustomer(new CustomerProfile(1003, "C-1003", "璟瑞工贸", "", "", "", "", true));
        saveCustomer(new CustomerProfile(1004, "C-1004", "瑞禾珠宝", "", "", "", "", true));
        saveMaterial(new MaterialProfile("AU9999", "足金", "黄金", "", true));
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
}
