package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.MasterDataRepository;
import com.zhubao.erp.business.domain.RequirementTemplate;
import com.zhubao.erp.business.domain.RequirementText;
import com.zhubao.erp.business.infra.InMemoryMasterDataRepository;
import com.zhubao.erp.business.infra.JdbcMasterDataRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 内存与真实 V69 SQL 的 H2 实现共用模板保存、回放、冲突及停用契约。 */
class RequirementTemplateServiceTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void orderedStepsAndReplayIncludeSortOrder(boolean jdbc) throws Exception {
        MasterDataService service = new MasterDataService(repository(jdbc));
        RequirementTemplate saved = service.saveTemplate("", "现场珠型", List.of(" 开粗 ", "车形", "抛光"), 9, 0, "create-1");
        assertThat(saved.steps()).containsExactly("开粗", "车形", "抛光");
        assertThat(service.saveTemplate("", "现场珠型", saved.steps(), 9, 0, "create-1").templateCode()).isEqualTo(saved.templateCode());
        assertThatThrownBy(() -> service.saveTemplate("", "现场珠型", saved.steps(), 10, 0, "create-1"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不一致");
        assertThat(RequirementText.compose(saved.steps(), "孔径 2 毫米"))
                .isEqualTo("先开粗，再车形，最后抛光\n孔径 2 毫米");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void staleEditsAndRepeatedDisableKeepSavedText(boolean jdbc) throws Exception {
        MasterDataService service = new MasterDataService(repository(jdbc));
        RequirementTemplate saved = service.saveTemplate("", "现场组合", List.of("开粗", "抛光"), 5, 0, "edit-1");
        String snapshot = RequirementText.compose(saved.steps(), "");
        RequirementTemplate updated = service.saveTemplate(saved.templateCode(), saved.name(), List.of("雕刻", "抛光"), 6, saved.version(), "");
        assertThat(updated.version()).isEqualTo(saved.version() + 1);
        assertThatThrownBy(() -> service.saveTemplate(saved.templateCode(), saved.name(), List.of("车形"), 6, saved.version(), ""))
                .isInstanceOf(BusinessException.class).hasMessageContaining("刷新");
        RequirementTemplate disabled = service.disableTemplate(saved.templateCode(), updated.version());
        assertThat(disabled.enabled()).isFalse();
        assertThat(service.disableTemplate(saved.templateCode(), updated.version())).isEqualTo(disabled);
        assertThat(service.templates(false)).extracting(RequirementTemplate::templateCode).doesNotContain(saved.templateCode());
        assertThat(snapshot).isEqualTo("先开粗，再抛光");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentCreateWithOneRequestProducesOneTemplate(boolean jdbc) throws Exception {
        MasterDataService service = new MasterDataService(repository(jdbc));
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> { start.await(); return service.saveTemplate("", "并发组合", List.of("开粗", "车形"), 9, 0, "same-request"); });
            var second = pool.submit(() -> { start.await(); return service.saveTemplate("", "并发组合", List.of("开粗", "车形"), 9, 0, "same-request"); });
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).templateCode()).isEqualTo(second.get(10, TimeUnit.SECONDS).templateCode());
            assertThat(service.templates(true).stream().filter(template -> template.name().equals("并发组合"))).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsInvalidOrOversizedDescriptions(boolean jdbc) throws Exception {
        MasterDataService service = new MasterDataService(repository(jdbc));
        assertThatThrownBy(() -> service.saveTemplate("", "空步骤", List.of(), 1, 0, "empty"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.saveTemplate("", "复合步骤", List.of("开粗、抛光"), 1, 0, "compound"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.saveTemplate("", "超长步骤", List.of("粗".repeat(41)), 1, 0, "long"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> RequirementText.compose(List.of("抛光"), "长".repeat(500)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("500");
    }

    private MasterDataRepository repository(boolean jdbc) throws Exception {
        if (!jdbc) return new InMemoryMasterDataRepository();
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:tpl_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        String sql = Files.readString(Path.of("src/main/resources/db/mysql/V69__m3_requirement_template.sql"));
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String command : MigrationCatalog.statements(sql)) statement.execute(command);
        }
        return new JdbcMasterDataRepository(dataSource);
    }
}
