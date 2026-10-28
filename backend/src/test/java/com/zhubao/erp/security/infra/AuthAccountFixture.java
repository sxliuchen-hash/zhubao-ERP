package com.zhubao.erp.security.infra;

import com.zhubao.erp.security.app.PasswordHasher;
import com.zhubao.erp.security.domain.*;
import java.util.List;

/** 角色登录测试按需显式建立测试账号。 */
public final class AuthAccountFixture {
    private AuthAccountFixture() { }
    public static InMemoryAuthUserAccountRepository repository(PasswordHasher hasher) {
        var repository = new InMemoryAuthUserAccountRepository(hasher);
        seed(repository, hasher);
        return repository;
    }
    public static void seed(AuthUserAccountRepository repository, PasswordHasher passwordHasher) {
        repository.save(account("admin", "admin123", "系统管理员", RoleCode.ADMIN, DataScope.ALL, passwordHasher));
        repository.save(account("leader", "leader123", "车间组长 马杰", RoleCode.WORKSHOP_LEADER, DataScope.TEAM, passwordHasher));
        repository.save(account("qc", "qc123", "质检员 周娜", RoleCode.QUALITY_INSPECTOR, DataScope.DEPARTMENT, passwordHasher));
        repository.save(account("warehouse", "warehouse123", "库管员 何琳", RoleCode.WAREHOUSE_KEEPER, DataScope.DEPARTMENT, passwordHasher));
        repository.save(account("operator", "operator123", "操作工 王强", RoleCode.OPERATOR, DataScope.SELF, passwordHasher));
        repository.save(account("carve", "carve123", "雕刻工 林海", RoleCode.OPERATOR, DataScope.SELF, passwordHasher)
                .withAllowedProcesses(List.of("雕刻")));
        repository.save(account("inlay", "inlay123", "镶嵌工 赵敏", RoleCode.OPERATOR, DataScope.SELF, passwordHasher)
                .withAllowedProcesses(List.of("镶嵌")));
        repository.save(account("polish", "polish123", "抛光工 陈刚", RoleCode.OPERATOR, DataScope.SELF, passwordHasher)
                .withAllowedProcesses(List.of("抛光")));
    }
    private static AuthUserAccount account(String username, String password, String operator, RoleCode role, DataScope scope, PasswordHasher hasher) {
        return new AuthUserAccount(username, hasher.hash(password), operator, role, scope, true, true);
    }
}
