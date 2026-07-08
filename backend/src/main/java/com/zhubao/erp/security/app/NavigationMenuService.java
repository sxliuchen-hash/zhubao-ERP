package com.zhubao.erp.security.app;

import com.zhubao.erp.security.domain.NavigationMenuItem;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.domain.RoleCode;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class NavigationMenuService {

    private final List<NavigationMenuItem> menuTree = List.of(
            menu("dashboard", "首页工作台", "项目进展 / 调度", "/dashboard", List.of()),
            menu("inbound", "来料母单", "收货 / 拆包", "/inbound", List.of(
                    PermissionCode.BUSINESS_INBOUND_CONFIRM.name(),
                    PermissionCode.DOCUMENT_ATTACHMENT_UPLOAD.name()
            )),
            menu("production", "生产工单", "排产 / 报工", "/production", List.of(
                    PermissionCode.BUSINESS_PRODUCTION_REPORT.name(),
                    PermissionCode.MACHINE_OUTPUT_SUBMIT.name()
            )),
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
            menu("configuration", "配置基线", "参数 / 构型", "/configuration", List.of(
                    PermissionCode.AUTH_ACCOUNT_MANAGE.name()
            )),
            menu("documents", "文档附件", "证据 / 归档", "/documents", List.of(
                    PermissionCode.DOCUMENT_ATTACHMENT_UPLOAD.name()
            )),
            menu("sample", "样例库", "验收脚本", "/sample", List.of(
                    PermissionCode.REGISTER_IMPORT_REVIEW.name(),
                    PermissionCode.REGISTER_IMPORT_POSTING.name()
            )),
            menu("hardware", "软硬件联动", "设备 / 兜底", "/hardware", List.of(
                    PermissionCode.VIDEO_TASK_OPERATE.name()
            )),
            menu("operations", "运维中心", "日志 / 指标", "/operations", List.of()),
            menu("mobile", "PWA 作业", "扫码 / 现场", "/mobile", List.of(
                    PermissionCode.BUSINESS_PRODUCTION_REPORT.name(),
                    PermissionCode.MACHINE_OUTPUT_SUBMIT.name()
            ))
    );
    private final Map<RoleCode, Set<String>> roleMenus = new EnumMap<>(RoleCode.class);

    public NavigationMenuService() {
        allow(RoleCode.ADMIN, "dashboard", "inbound", "production", "quality", "inventory", "approvals",
                "configuration", "documents", "sample", "hardware", "operations", "mobile");
        allow(RoleCode.GENERAL_MANAGER, "dashboard", "inbound", "production", "quality", "inventory", "approvals",
                "configuration", "documents", "sample", "hardware", "operations", "mobile");
        allow(RoleCode.SALES, "dashboard", "inbound", "inventory", "documents", "sample");
        allow(RoleCode.RECEIVER, "dashboard", "inbound", "documents", "hardware", "mobile");
        allow(RoleCode.WORKSHOP_LEADER, "dashboard", "production", "quality", "approvals", "sample",
                "hardware", "operations", "mobile");
        allow(RoleCode.OPERATOR, "dashboard", "production", "mobile");
        allow(RoleCode.QUALITY_INSPECTOR, "dashboard", "quality", "inventory", "documents", "mobile");
        allow(RoleCode.WAREHOUSE_KEEPER, "dashboard", "inbound", "inventory", "documents", "sample",
                "hardware", "operations", "mobile");
        allow(RoleCode.FINANCE_STATISTICIAN, "dashboard", "approvals", "configuration");
    }

    public List<NavigationMenuItem> menuTreeFor(RoleCode role) {
        Set<String> allowedCodes = roleMenus.getOrDefault(role, Set.of("dashboard"));
        return menuTree.stream()
                .filter(item -> allowedCodes.contains(item.code()))
                .toList();
    }

    public List<String> menuCodesFor(RoleCode role) {
        return menuTreeFor(role).stream()
                .map(NavigationMenuItem::code)
                .toList();
    }

    public List<NavigationMenuItem> fullMenuTree() {
        return menuTree;
    }

    private NavigationMenuItem menu(
            String code,
            String label,
            String module,
            String route,
            List<String> buttonCodes
    ) {
        return new NavigationMenuItem(code, label, module, route, buttonCodes, List.of());
    }

    private void allow(RoleCode role, String... menuCodes) {
        roleMenus.put(role, Set.of(menuCodes));
    }
}
