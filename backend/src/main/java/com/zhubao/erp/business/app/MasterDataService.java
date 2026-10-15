package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.domain.MasterDataRepository;
import com.zhubao.erp.business.domain.MaterialProfile;
import com.zhubao.erp.common.error.BusinessException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.ArrayList;
import java.util.LinkedHashSet;

@Service
public class MasterDataService {
    private final MasterDataRepository repository;

    public MasterDataService(MasterDataRepository repository) { this.repository = repository; }

    public List<CustomerProfile> customers() { return repository.listCustomers(); }
    public List<MaterialProfile> materials() { return repository.listMaterials(); }

    public CustomerProfile customerForIntake(Long id) {
        if (id == null || id < 1) throw new BusinessException("请从客户档案选择客户");
        CustomerProfile profile = repository.findCustomer(id)
                .orElseThrow(() -> new BusinessException("客户档案不存在"));
        if (!profile.enabled()) throw new BusinessException("客户已停用，不能用于新单");
        return profile;
    }

    public MaterialProfile materialForIntake(String code) {
        if (code == null || code.isBlank()) throw new BusinessException("请选择材质");
        MaterialProfile profile = repository.findMaterial(code.trim())
                .orElseThrow(() -> new BusinessException("材质目录不存在：" + code));
        if (!profile.enabled()) throw new BusinessException("材质已停用，不能用于新单");
        return profile;
    }

    public CustomerProfile saveCustomer(CustomerProfile input) {
        if (input == null) throw new BusinessException("客户档案不能为空");
        String name = required(input.name(), "客户名称");
        if (name.length() > 120) throw new BusinessException("客户名称过长");
        CustomerProfile previous = input.customerId() > 0
                ? repository.findCustomer(input.customerId()).orElseThrow(() -> new BusinessException("客户档案不存在")) : null;
        String code = previous != null
                ? previous.customerCode()
                : "C-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
        LinkedHashSet<String> aliases = new LinkedHashSet<>(tokens(input.aliases()));
        if (previous != null) {
            aliases.addAll(tokens(previous.aliases()));
            if (!previous.name().equals(name)) aliases.add(previous.name());
        }
        aliases.remove(name);
        return repository.saveCustomer(new CustomerProfile(input.customerId(), code, name,
                String.join("|", aliases), text(input.contact()), text(input.phone()), text(input.remark()), input.enabled()));
    }

    /** 旧名称授权仅在能唯一匹配档案时转换为稳定 ID；无法确认归属的值不扩大权限。 */
    public List<String> stableCustomerScope(List<String> savedScope) {
        if (savedScope == null || savedScope.isEmpty()) return List.of();
        List<CustomerProfile> profiles = repository.listCustomers();
        LinkedHashSet<String> resolved = new LinkedHashSet<>();
        for (String raw : savedScope) {
            String value = text(raw);
            if (value.isEmpty()) continue;
            if (value.matches("[0-9]+")) {
                resolved.add(value);
                continue;
            }
            List<CustomerProfile> matching = profiles.stream().filter(profile ->
                    profile.customerCode().equalsIgnoreCase(value)
                            || profile.name().equalsIgnoreCase(value)
                            || tokens(profile.aliases()).stream().anyMatch(alias -> alias.equalsIgnoreCase(value))).toList();
            if (matching.size() == 1) resolved.add(String.valueOf(matching.get(0).customerId()));
        }
        return List.copyOf(resolved);
    }

    public MaterialProfile saveMaterial(MaterialProfile input) {
        if (input == null) throw new BusinessException("材质档案不能为空");
        String name = required(input.name(), "材质名称");
        if (name.length() > 120) throw new BusinessException("材质名称过长");
        String code = input.materialCode() == null || input.materialCode().isBlank()
                ? "MAT-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT)
                : input.materialCode().trim();
        return repository.saveMaterial(new MaterialProfile(code, name, text(input.aliases()), text(input.remark()), input.enabled()));
    }

    private String required(String value, String label) {
        String normalized = text(value);
        if (normalized.isEmpty()) throw new BusinessException(label + "不能为空");
        return normalized;
    }

    private String text(String value) { return value == null ? "" : value.trim(); }
    private List<String> tokens(String value) {
        List<String> rows = new ArrayList<>();
        for (String part : text(value).split("[|,，;；\\r\\n]+")) {
            if (!part.isBlank()) rows.add(part.trim());
        }
        return rows;
    }
}
