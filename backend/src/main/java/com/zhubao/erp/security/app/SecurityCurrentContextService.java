package com.zhubao.erp.security.app;

import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.domain.SecurityCurrentContext;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class SecurityCurrentContextService {

    private final PermissionGuard permissionGuard;
    private final NavigationMenuService navigationMenuService;

    public SecurityCurrentContextService(
            PermissionGuard permissionGuard,
            NavigationMenuService navigationMenuService
    ) {
        this.permissionGuard = permissionGuard;
        this.navigationMenuService = navigationMenuService;
    }

    public SecurityCurrentContext build(OperatorContext operatorContext) {
        List<SecurityCurrentContext.PermissionGrant> grants = permissionGuard
                .allowedPermissionsFor(operatorContext.role())
                .stream()
                .sorted(Comparator.comparing(PermissionCode::name))
                .map(permission -> new SecurityCurrentContext.PermissionGrant(permission.name(), permission.label()))
                .toList();

        return new SecurityCurrentContext(
                operatorContext.operator(),
                operatorContext.role().name(),
                roleLabel(operatorContext.role()),
                operatorContext.dataScope().name(),
                dataScopeLabel(operatorContext.dataScope()),
                dataBoundary(operatorContext.dataScope()),
                operatorContext.customerScope(),
                operatorContext.headerBased(),
                grants,
                grants.stream().map(SecurityCurrentContext.PermissionGrant::code).toList(),
                navigationMenuService.menuCodesFor(operatorContext.role()),
                navigationMenuService.menuTreeFor(operatorContext.role()),
                limitationNotes(operatorContext.headerBased()),
                new SecurityCurrentContext.HeaderContract(
                        OperatorContextResolver.OPERATOR_HEADER,
                        OperatorContextResolver.ROLE_HEADER,
                        OperatorContextResolver.DATA_SCOPE_HEADER,
                        OperatorContextResolver.CUSTOMER_SCOPE_HEADER
                )
        );
    }

    private String roleLabel(RoleCode role) {
        return switch (role) {
            case ADMIN -> "系统管理员";
            case GENERAL_MANAGER -> "总经理";
            case SALES -> "业务员";
            case RECEIVER -> "收货员";
            case WORKSHOP_LEADER -> "车间组长";
            case OPERATOR -> "操作工";
            case QUALITY_INSPECTOR -> "质检员";
            case WAREHOUSE_KEEPER -> "库管员";
            case FINANCE_STATISTICIAN -> "财务统计";
        };
    }

    private String dataScopeLabel(DataScope dataScope) {
        return switch (dataScope) {
            case SELF -> "本人任务";
            case TEAM -> "本班组/工位";
            case DEPARTMENT -> "本部门";
            case CUSTOMER_RANGE -> "授权客户范围";
            case ALL -> "全部数据";
        };
    }

    private String dataBoundary(DataScope dataScope) {
        return switch (dataScope) {
            case SELF -> "业务工作台 GET 已按当前操作人文本匹配本人任务、审批和本人报工记录。";
            case TEAM -> "业务工作台 GET 已按当前角色职责关键词收敛到同班组、同工位或当前负责队列内的业务对象。";
            case DEPARTMENT -> "业务工作台 GET 已按当前角色部门职责关键词收敛到本部门业务对象和部门内审批任务。";
            case CUSTOMER_RANGE -> "业务工作台 GET 已按客户范围请求头收敛到被授权客户、母单、工单和库存批次。";
            case ALL -> "当前评审默认全局可见；正式上线必须由真实角色、菜单权限和审计日志共同约束。";
        };
    }

    private List<String> limitationNotes(boolean headerBased) {
        if (headerBased) {
            return List.of(
                    "当前身份来自请求头，可用于评审和自动化测试兜底。",
                    "业务工作台 GET 已接入首版 dataScope 过滤；跨对象客户授权表和深层行级权限仍需继续固化。",
                    "当前菜单权限已由后端菜单树返回，前端登录会话会按 menuCodes/menuTree 过滤侧栏；请求头兜底仍展示评审全量导航。",
                    "敏感 POST 已接入轻量角色守卫，但复杂审批流和工资锁账仍未完成。"
            );
        }
        return List.of(
                "当前身份来自登录会话 token，已覆盖敏感 POST 权限守卫。",
                "M3.97 已接入账号仓储、PBKDF2 密码哈希、登录失败锁定、账号启停、管理员解锁、生产密码策略、密码过期边界、登录态自助改密续期和账号审批摘要，当前默认 seed 仍为评审账号；正式上线必须接入生产账号前置审批、SSO/JWT 和审计保全。",
                "业务工作台 GET 已接入首版 dataScope 过滤；登录账号的客户授权表、组织树和深层行级权限仍需继续固化。",
                "菜单权限已返回后端菜单树和按钮权限基线，前端侧栏已按登录会话菜单编码过滤；生产菜单持久化仍需继续固化。"
        );
    }
}
