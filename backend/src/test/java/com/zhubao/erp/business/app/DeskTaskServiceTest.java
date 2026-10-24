package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.CancelDeskTaskCommand;
import com.zhubao.erp.business.domain.CompleteDeskTaskCommand;
import com.zhubao.erp.business.domain.CreateDeskTaskCommand;
import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.domain.DeskTask;
import com.zhubao.erp.business.domain.DeskTaskStatus;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.business.infra.InMemoryDeskTaskRepository;
import com.zhubao.erp.business.infra.InMemoryMasterDataRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.AuthUserAccountRepository;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeskTaskServiceTest {

    private InMemoryBusinessDocumentRepository documents;
    private InMemoryDeskTaskRepository tasks;
    private AccountBook accounts;
    private DeskTaskService service;
    private AuthUserAccount sales;
    private AuthUserAccount designer;
    private AuthUserAccount admin;

    @BeforeEach
    void setUp() {
        documents = new InMemoryBusinessDocumentRepository();
        tasks = new InMemoryDeskTaskRepository();
        accounts = new AccountBook();
        InMemoryMasterDataRepository customers = new InMemoryMasterDataRepository();
        customers.saveCustomer(new CustomerProfile(1099, "C-1099", "停用客户", "", "", "", "", false));
        documents.saveMotherOrder(mother("MD-HX-001", 1001, "恒信珠宝"));
        documents.saveMotherOrder(mother("MD-YC-001", 1002, "雅辰饰品"));
        sales = account("sales", "业务员 林岚", RoleCode.SALES, DataScope.CUSTOMER_RANGE, List.of("恒信珠宝"), true);
        designer = account("designer", "建模员", RoleCode.OPERATOR, DataScope.SELF, List.of(), true);
        admin = account("admin", "管理员", RoleCode.ADMIN, DataScope.ALL, List.of(), true);
        accounts.add(sales);
        accounts.add(designer);
        accounts.add(admin);
        accounts.add(account("old", "停用员", RoleCode.OPERATOR, DataScope.SELF, List.of(), false));
        service = new DeskTaskService(tasks, accounts, customers, documents,
                new BusinessDataAccess(documents, new StockLedgerService(new InMemoryStockLedgerRepository())),
                new PermissionGuard());
    }

    @Test
    void 创建请求回放且内容变化被拒绝也不写工序() {
        DeskTask created = service.create(command("req-1", "戒托建模", 1001L, "MD-HX-001", "designer"), sales);
        DeskTask replay = service.create(command("req-1", "戒托建模", 1001L, "MD-HX-001", "designer"), sales);

        assertThat(replay.taskCode()).isEqualTo(created.taskCode());
        assertThat(replay.kind().label()).isEqualTo("建模");
        assertThat(replay.customerName()).isEqualTo("恒信珠宝");
        assertThat(tasks.listAll()).hasSize(1);
        assertThatThrownBy(() -> service.create(command("req-1", "改了标题", 1001L, "MD-HX-001", "designer"), sales))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内容不一致");
        assertThat(documents.listAllProcessStationHandoffs()).isEmpty();
        assertThat(documents.listAllChildWorkOrders()).isEmpty();
    }

    @Test
    void 停用客户和母单客户不一致都不能指派() {
        assertThatThrownBy(() -> service.create(command("req-off", "停用客户建模", 1099L, "", "designer"), admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已停用");
        assertThatThrownBy(() -> service.create(command("req-person", "停用责任人", null, "", "old"), admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("责任人已停用");
        assertThatThrownBy(() -> service.create(command("req-mix", "客户不对", 1001L, "MD-YC-001", "designer"), admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不一致");
        assertThat(tasks.listAll()).isEmpty();
    }

    @Test
    void 看不见的母单不能挂上待办() {
        AuthUserAccount other = account("other", "其他业务", RoleCode.SALES, DataScope.CUSTOMER_RANGE, List.of("雅辰饰品"), true);
        accounts.add(other);

        assertThatThrownBy(() -> service.create(command("req-hide", "越权母单", null, "MD-HX-001", "designer"), other))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("母单");
        assertThat(tasks.listAll()).isEmpty();
    }

    @Test
    void 只有责任人能完成且重复请求回放() {
        DeskTask created = service.create(command("req-do", "戒托建模", null, "MD-HX-001", "designer"), sales);
        assertThat(created.customerId()).isEqualTo(1001L);

        assertThatThrownBy(() -> service.complete(created.taskCode(), new CompleteDeskTaskCommand("done-1", 1, "做好了"), sales))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("责任人");
        DeskTask done = service.complete(created.taskCode(), new CompleteDeskTaskCommand("done-1", 1, "做好了"), designer);
        DeskTask replay = service.complete(created.taskCode(), new CompleteDeskTaskCommand("done-1", 1, "做好了"), designer);

        assertThat(done.status()).isEqualTo(DeskTaskStatus.DONE);
        assertThat(replay.taskCode()).isEqualTo(done.taskCode());
        assertThat(replay.version()).isEqualTo(2);
        assertThatThrownBy(() -> service.complete(created.taskCode(), new CompleteDeskTaskCommand("done-1", 2, "另一句"), designer))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内容不一致");
        assertThatThrownBy(() -> service.complete(created.taskCode(), new CompleteDeskTaskCommand("done-2", 2, "再交一次"), designer))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已经完成");
        assertThatThrownBy(() -> service.complete(created.taskCode(), new CompleteDeskTaskCommand("done-stale", 1, "过期"), designer))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 创建人可以作废责任人不能替作废() {
        DeskTask created = service.create(command("req-cancel", "戒托建模", null, "", "designer"), sales);
        assertThatThrownBy(() -> service.cancel(created.taskCode(), new CancelDeskTaskCommand("cancel-1", 1, "不做了"), designer))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("创建人");

        DeskTask cancelled = service.cancel(created.taskCode(), new CancelDeskTaskCommand("cancel-1", 1, "不做了"), sales);
        assertThat(cancelled.status()).isEqualTo(DeskTaskStatus.CANCELLED);
        assertThat(service.cancel(created.taskCode(), new CancelDeskTaskCommand("cancel-1", 1, "不做了"), sales).version()).isEqualTo(2);
        assertThatThrownBy(() -> service.complete(created.taskCode(), new CompleteDeskTaskCommand("done-after", 2, ""), designer))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已经作废");
    }

    @Test
    void 列表只含本人管理员才能看全部未完成() {
        service.create(command("req-a", "给建模", null, "", "designer"), sales);
        AuthUserAccount polish = account("polish", "抛光工", RoleCode.OPERATOR, DataScope.SELF, List.of(), true);
        accounts.add(polish);
        service.create(command("req-b", "给抛光", null, "", "polish"), sales);

        assertThat(service.list("assigned", designer)).extracting(DeskTask::title).containsExactly("给建模");
        assertThat(service.list("issued", sales)).extracting(DeskTask::title).containsExactly("给建模", "给抛光");
        assertThat(service.list("open", admin)).hasSize(2);
        assertThatThrownBy(() -> service.list("open", sales))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> service.create(command("req-op", "操作工自建", null, "", "designer"), designer))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void 陈旧版本不能作废() {
        DeskTask created = service.create(command("req-ver", "戒托建模", null, "", "designer"), sales);
        assertThatThrownBy(() -> service.cancel(created.taskCode(), new CancelDeskTaskCommand("cancel-stale", 0, "过期"), sales))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请刷新后重试");
        assertThat(tasks.findByCode(created.taskCode()).orElseThrow().status()).isEqualTo(DeskTaskStatus.OPEN);
    }

    private CreateDeskTaskCommand command(String requestId, String title, Long customerId, String mother, String assignee) {
        return new CreateDeskTaskCommand("建模", title, "说明", customerId, mother, assignee, "2026-11-01", requestId);
    }

    private AuthUserAccount account(String username, String operator, RoleCode role, DataScope scope,
                                    List<String> customers, boolean enabled) {
        return new AuthUserAccount(username, "hash", operator, role, scope, enabled, false,
                Instant.now(), 0, null, customers, false, false, List.of(), List.of());
    }

    private MotherOrderRecord mother(String code, long customerId, String customerName) {
        return new MotherOrderRecord(code, customerId, customerId, "AU750", "2026-10-22", "", "", "",
                "何琳", "", "戒托", "客户", true, "WH", BigDecimal.ONE, "件", BigDecimal.ONE, "RAW_WAITING", "",
                "", null, "", "何琳", "", "", "已入库", "2026-10-22T00:00:00Z", "建模", "2026-10-30", customerName);
    }

    private static final class AccountBook implements AuthUserAccountRepository {
        private final Map<String, AuthUserAccount> rows = new LinkedHashMap<>();

        void add(AuthUserAccount account) {
            rows.put(account.username().toLowerCase(Locale.ROOT), account);
        }

        @Override
        public AuthUserAccount save(AuthUserAccount account) {
            add(account);
            return account;
        }

        @Override
        public List<AuthUserAccount> findAll() {
            return List.copyOf(rows.values());
        }

        @Override
        public Optional<AuthUserAccount> findByUsername(String username) {
            String key = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
            return Optional.ofNullable(rows.get(key));
        }

        @Override
        public List<String> findActiveUsernames() {
            return List.of();
        }

        @Override
        public void updateEnabled(String username, boolean enabled) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void unlock(String username) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void recordLoginFailure(String username, int failedLoginCount, Instant lockedUntil) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void recordLoginSuccess(String username) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean consumeTemporaryPassword(String username) {
            throw new UnsupportedOperationException();
        }
    }
}
