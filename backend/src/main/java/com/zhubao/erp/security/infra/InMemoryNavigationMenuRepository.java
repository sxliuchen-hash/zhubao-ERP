package com.zhubao.erp.security.infra;

import com.zhubao.erp.security.domain.NavigationMenuItem;
import com.zhubao.erp.security.domain.NavigationMenuRepository;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.domain.RoleCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 默认内存导航菜单及角色绑定；配置 {@code zhubao.security.menu.repository=jdbc} 时改用持久化实现。
 */
@Repository
@ConditionalOnProperty(name = "zhubao.security.menu.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryNavigationMenuRepository implements NavigationMenuRepository {

    private final List<NavigationMenuItem> menuTree = List.of(
            menu("dashboard", "首页工作台", "项目进展 / 调度", "/dashboard", List.of()),
            menu("inbound", "来料母单", "收货 / 拆包", "/inbound", List.of(
                    PermissionCode.BUSINESS_INBOUND_CONFIRM.name(),
                    PermissionCode.DOCUMENT_ATTACHMENT_UPLOAD.name()
            )),
            menu("label", "标签", "打印 / 模板", "/label", List.of()),
            menu("production", "生产工单", "排产 / 报工", "/production", List.of(
                    PermissionCode.BUSINESS_PRODUCTION_REPORT.name(),
                    PermissionCode.MACHINE_OUTPUT_SUBMIT.name()
            )),
            menu("progress", "进度", "在制 / 客户", "/progress", List.of()),
            menu("quality", "质检返工", "缺陷 / 损耗", "/quality", List.of(
                    PermissionCode.BUSINESS_QUALITY_FREEZE.name(),
                    PermissionCode.BUSINESS_REWORK_DISPATCH.name(),
                    PermissionCode.BUSINESS_QUALITY_RELEASE.name()
            )),
            menu("inventory", "库存追溯", "客户所有权", "/inventory", List.of(
                    PermissionCode.BUSINESS_OUTBOUND_SHIPMENT.name(),
                    PermissionCode.BUSINESS_CUSTOMER_RETURN.name()
            )),
            menu("approvals", "审批工作台", "损耗 / 账号 / 异常", "/approvals", List.of(
                    PermissionCode.APPROVAL_DECISION.name(),
                    PermissionCode.BUSINESS_LOSS_APPROVAL.name()
            )),
            menu("finance", "财务", "客户账 / 工资 / 成本", "/finance", List.of(
                    PermissionCode.FINANCE_PRICE_MANAGE.name(),
                    PermissionCode.FINANCE_BILLING_POST.name(),
                    PermissionCode.FINANCE_RECEIPT.name(),
                    PermissionCode.FINANCE_PAYROLL.name(),
                    PermissionCode.FINANCE_CONSUMABLE.name(),
                    PermissionCode.FINANCE_ASSET.name(),
                    PermissionCode.FINANCE_PAYABLE.name()
            )),
            menu("configuration", "配置基线", "参数 / 构型", "/configuration", List.of()),
            menu("users", "用户管理", "账号 / 角色权限", "/users", List.of(
                    PermissionCode.AUTH_ACCOUNT_MANAGE.name(), PermissionCode.CUSTOMER_AUTHORIZATION_MANAGE.name()
            )),
            menu("documents", "文档附件", "证据 / 归档", "/documents", List.of(
                    PermissionCode.DOCUMENT_ATTACHMENT_UPLOAD.name()
            )),
            menu("sample", "样例库", "验收脚本", "/sample", List.of(
                    PermissionCode.REGISTER_IMPORT_REVIEW.name(),
                    PermissionCode.REGISTER_IMPORT_POSTING.name()
            )),
            menu("hardware", "软硬件联动", "设备 / 兜底", "/hardware", List.of(
                    PermissionCode.VIDEO_TASK_OPERATE.name(),
                    PermissionCode.GATEWAY_READING_CONSUME.name(),
                    PermissionCode.GATEWAY_DEVICE_MANAGE.name()
            )),
            menu("operations", "运维中心", "日志 / 指标", "/operations", List.of()),
            menu("mobile", "PWA 作业", "扫码 / 现场", "/mobile", List.of(
                    PermissionCode.BUSINESS_PRODUCTION_REPORT.name(),
                    PermissionCode.MACHINE_OUTPUT_SUBMIT.name()
            ))
    );

    private final Map<RoleCode, Set<String>> roleMenus = new EnumMap<>(RoleCode.class);

    public InMemoryNavigationMenuRepository() {
        allow(RoleCode.ADMIN, "dashboard", "inbound", "label", "production", "progress", "quality", "inventory", "approvals", "finance",
                "configuration", "users", "documents", "sample", "hardware", "operations", "mobile");
        allow(RoleCode.GENERAL_MANAGER, "dashboard", "inbound", "label", "production", "progress", "quality", "inventory", "approvals", "finance",
                "configuration", "documents", "sample", "hardware", "operations", "mobile");
        allow(RoleCode.SALES, "dashboard", "inbound", "label", "progress", "inventory", "documents", "sample");
        allow(RoleCode.RECEIVER, "dashboard", "inbound", "label", "progress", "documents", "hardware", "mobile");
        allow(RoleCode.WORKSHOP_LEADER, "dashboard", "production", "progress", "quality", "approvals", "sample",
                "hardware", "operations", "mobile");
        allow(RoleCode.OPERATOR, "dashboard", "production", "mobile");
        allow(RoleCode.QUALITY_INSPECTOR, "dashboard", "progress", "quality", "inventory", "documents", "mobile");
        allow(RoleCode.WAREHOUSE_KEEPER, "dashboard", "inbound", "label", "progress", "inventory", "documents", "sample",
                "hardware", "operations", "mobile");
        allow(RoleCode.FINANCE_STATISTICIAN, "dashboard", "progress", "approvals", "finance", "configuration");
    }

    @Override
    public List<NavigationMenuItem> findMenuItems() {
        return menuTree;
    }

    @Override
    public Map<RoleCode, Set<String>> findRoleMenuBindings() {
        return Map.copyOf(roleMenus);
    }

    private NavigationMenuItem menu(String code, String label, String module, String route, List<String> buttonCodes) {
        return new NavigationMenuItem(code, label, module, route, buttonCodes, List.of());
    }

    private void allow(RoleCode role, String... menuCodes) {
        roleMenus.put(role, Set.of(menuCodes));
    }
}
