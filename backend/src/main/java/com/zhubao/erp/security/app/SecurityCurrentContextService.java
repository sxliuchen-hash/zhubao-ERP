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

    public SecurityCurrentContextService(PermissionGuard permissionGuard) {
        this.permissionGuard = permissionGuard;
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
                true,
                grants,
                grants.stream().map(SecurityCurrentContext.PermissionGrant::code).toList(),
                limitationNotes(),
                new SecurityCurrentContext.HeaderContract(
                        OperatorContextResolver.OPERATOR_HEADER,
                        OperatorContextResolver.ROLE_HEADER,
                        OperatorContextResolver.DATA_SCOPE_HEADER
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
            case SELF -> "后续真实登录接入后，只应读取当前操作人的任务、审批和本人报工记录。";
            case TEAM -> "后续真实登录接入后，只应读取同班组、同工位或当前负责队列内的业务对象。";
            case DEPARTMENT -> "后续真实登录接入后，只应读取本部门业务对象和部门内审批任务。";
            case CUSTOMER_RANGE -> "后续真实登录接入后，只应读取被授权客户、母单、工单和库存批次。";
            case ALL -> "当前评审默认全局可见；正式上线必须由真实角色、菜单权限和审计日志共同约束。";
        };
    }

    private List<String> limitationNotes() {
        return List.of(
                "当前身份来自请求头，尚未接入真实登录、JWT、Session 或 SSO。",
                "当前数据范围只做解析与展示，GET 工作台数据尚未按 dataScope 做真实过滤。",
                "当前菜单权限仍由前端导航展示控制，尚未接入后端菜单树和按钮级授权。",
                "敏感 POST 已接入轻量角色守卫，但复杂审批流和工资锁账仍未完成。"
        );
    }
}
