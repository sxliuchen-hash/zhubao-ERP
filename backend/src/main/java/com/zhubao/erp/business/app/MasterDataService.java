package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.domain.MasterDataRepository;
import com.zhubao.erp.business.domain.MaterialProfile;
import com.zhubao.erp.business.domain.RequirementShortcut;
import com.zhubao.erp.business.domain.RequirementTemplate;
import com.zhubao.erp.business.domain.RequirementText;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.app.CustomerAuthorizationService;
import com.zhubao.erp.security.domain.AuthUserAccountRepository;
import com.zhubao.erp.security.domain.CustomerScopeRules;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.LinkedHashSet;

@Service
public class MasterDataService {
    private final MasterDataRepository repository;
    private final CustomerAuthorizationService authorizations;
    private final AuthUserAccountRepository accounts;

    public MasterDataService(MasterDataRepository repository) {
        this(repository, null, null);
    }

    @Autowired
    public MasterDataService(MasterDataRepository repository, CustomerAuthorizationService authorizations,
                             AuthUserAccountRepository accounts) {
        this.repository = repository;
        this.authorizations = authorizations;
        this.accounts = accounts;
    }

    public List<CustomerProfile> customers() { return repository.listCustomers(); }

    public List<CustomerProfile> customersFor(OperatorContext operator) {
        return customers().stream()
                .filter(profile -> CustomerScopeRules.allowed(operator, profile.customerId(), profile.name()))
                .toList();
    }

    public List<com.zhubao.erp.business.domain.MaterialStorageChange> storageChanges(String code) { return repository.storageChanges(code); }

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
        return saveCustomer(input, null, "");
    }

    public CustomerProfile saveCustomer(CustomerProfile input, OperatorContext operator, String username) {
        boolean creating = input == null || input.customerId() <= 0;
        CustomerProfile saved = persistCustomer(input);
        if (creating) includeInCreatorScope(saved, operator, username);
        return saved;
    }

    private CustomerProfile persistCustomer(CustomerProfile input) {
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

    /** 授权客户范围的账号新建客户后，把该客户纳入本人范围，才能当场用来建母单。 */
    private void includeInCreatorScope(CustomerProfile saved, OperatorContext operator, String username) {
        if (operator == null || operator.dataScope() != DataScope.CUSTOMER_RANGE || accounts == null) return;
        if (username == null || username.isBlank()) return;
        String id = String.valueOf(saved.customerId());
        if (CustomerScopeRules.allowed(operator, saved.customerId(), saved.name())) return;
        if (authorizations != null && !authorizations.authorizationsFor(username).isEmpty()) {
            authorizations.grantForIntake(username, id, saved.name(), operator.operator());
            return;
        }
        accounts.findByUsername(username).ifPresent(account -> {
            if (account.customerScope().contains(id)) return;
            List<String> next = new ArrayList<>(account.customerScope());
            next.add(id);
            accounts.save(account.withCustomerScope(next));
        });
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
            if (value.startsWith("客户#")) {
                String id = value.substring("客户#".length()).trim();
                if (id.matches("[0-9]+") && profiles.stream().anyMatch(profile -> id.equals(String.valueOf(profile.customerId())))) {
                    resolved.add(id);
                }
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
        MaterialProfile previous = repository.findMaterial(code).orElse(null);
        var conditions = input.storageConditions() != null ? input.storageConditions()
                : previous == null || previous.storageConditions() == null ? List.<String>of() : previous.storageConditions();
        String instruction = input.storageInstruction() != null ? input.storageInstruction()
                : previous == null ? "" : previous.storageInstruction();
        var storage = new com.zhubao.erp.business.domain.StorageRequirements(conditions, instruction);
        return repository.saveMaterial(new MaterialProfile(code, name, text(input.aliases()), text(input.remark()), input.enabled(), storage.conditions(), storage.instruction()));
    }

    public List<RequirementShortcut> shortcuts() {
        return repository.listShortcuts();
    }

    public RequirementShortcut saveShortcut(RequirementShortcut input) {
        if (input == null) throw new BusinessException("快捷词不能为空");
        String phrase = required(input.phrase(), "快捷词");
        if (phrase.length() > 40) throw new BusinessException("快捷词过长");
        if (phrase.chars().anyMatch(ch -> ch == '、' || ch == ',' || ch == '，' || ch == ';' || ch == '；')) {
            throw new BusinessException("快捷词不要包含顿号或逗号");
        }
        String normalized = phrase.toLowerCase(Locale.ROOT);
        boolean duplicated = repository.listShortcuts().stream()
                .anyMatch(existing -> existing.phrase().trim().toLowerCase(Locale.ROOT).equals(normalized));
        if (duplicated) throw new BusinessException("快捷词已存在");
        int sort = repository.listShortcuts().stream().mapToInt(RequirementShortcut::sortOrder).max().orElse(0) + 1;
        return repository.saveShortcut(new RequirementShortcut(0, phrase, sort));
    }

    public RequirementShortcut removeShortcut(long shortcutId) {
        return repository.deleteShortcut(shortcutId).orElseThrow(() -> new BusinessException("快捷词不存在"));
    }

    public List<RequirementTemplate> templates(boolean includeDisabled) {
        return repository.listTemplates().stream()
                .filter(template -> includeDisabled || template.enabled())
                .toList();
    }

    public RequirementTemplate saveTemplate(String templateCode, String name, List<String> steps, int sortOrder,
                                            int version, String requestId) {
        String title = required(name, "模板名称");
        if (title.length() > 40) {
            throw new BusinessException("模板名称过长");
        }
        RequirementText.compose(steps, "");
        List<String> normalized = steps.stream().map(this::text).filter(step -> !step.isEmpty()).toList();
        String code = text(templateCode);
        if (code.isEmpty()) {
            String request = required(requestId, "新增模板必须带请求编号");
            if (request.length() > 80) {
                throw new BusinessException("请求编号过长");
            }
            Optional<RequirementTemplate> existing = repository.findTemplateByRequest(request);
            if (existing.isPresent()) {
                RequirementTemplate saved = existing.get();
                if (!saved.name().equals(title) || !saved.steps().equals(normalized)
                        || sortOrder > 0 && saved.sortOrder() != sortOrder) {
                    throw new BusinessException("同一请求的模板内容不一致");
                }
                return saved;
            }
            String generated = "TPL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
            int sort = sortOrder > 0 ? sortOrder : repository.listTemplates().stream().mapToInt(RequirementTemplate::sortOrder).max().orElse(0) + 1;
            return repository.insertTemplate(new RequirementTemplate(0, generated, title, normalized, true, sort, 1, request));
        }
        RequirementTemplate current = repository.findTemplate(code).orElseThrow(() -> new BusinessException("加工要求模板不存在"));
        if (repository.listTemplates().stream().anyMatch(template -> !template.templateCode().equals(code) && template.name().equalsIgnoreCase(title))) {
            throw new BusinessException("模板名称已存在");
        }
        return repository.updateTemplate(new RequirementTemplate(current.templateId(), code, title, normalized, current.enabled(),
                sortOrder > 0 ? sortOrder : current.sortOrder(), current.version(), current.requestId()), version);
    }

    public RequirementTemplate disableTemplate(String templateCode, int version) {
        RequirementTemplate current = repository.findTemplate(text(templateCode)).orElseThrow(() -> new BusinessException("加工要求模板不存在"));
        if (!current.enabled()) {
            return current;
        }
        return repository.updateTemplate(new RequirementTemplate(current.templateId(), current.templateCode(), current.name(),
                current.steps(), false, current.sortOrder(), current.version(), current.requestId()), version);
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
