package com.zhubao.erp.business;

/** 老业务场景迁移到正式请求链时使用的显式测试工作单元。 */
public final class BusinessWorkFixture {
    private BusinessWorkFixture() { }
    public static com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository documents(String code, long customerId) {
        var repository = new com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository();
        repository.saveMotherOrder(new com.zhubao.erp.business.domain.MotherOrderRecord(code, customerId, customerId, "MAT", "2026-10-29", "", "", "", "", "", "测试货品", "CUSTOMER", true, "RAW", java.math.BigDecimal.TEN, "件", java.math.BigDecimal.TEN, "RAW_WAITING", "", "", null, "", "收货", "", "", "INBOUND_CONFIRMED", "2026-10-29", "要求", "2026-11-30", "测试客户"));
        return repository;
    }
    public static com.zhubao.erp.work.app.BusinessWorkUnit unit() {
        return new com.zhubao.erp.work.app.MemoryBusinessWorkUnit(new com.zhubao.erp.work.infra.InMemoryCommandRequestRepository());
    }
    public static com.zhubao.erp.work.app.BusinessWorkUnit unit(javax.sql.DataSource dataSource) throws java.sql.SQLException {
        if (dataSource == null) return unit();
        migrate(dataSource, "db/mysql/V44__m3_command_request.sql");
        return new com.zhubao.erp.work.app.JdbcBusinessWorkUnit(dataSource,
                new com.zhubao.erp.work.infra.JdbcCommandRequestRepository(dataSource));
    }
    public static void migrate(javax.sql.DataSource dataSource, String resource) throws java.sql.SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var input = new org.springframework.core.io.ClassPathResource(resource).getInputStream()) {
            String sql = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).replaceAll("(?m)^--.*$", "");
            for (String command : sql.split(";")) if (!command.isBlank()) statement.execute(command);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("无法读取测试所用迁移", exception);
        }
    }
    public static com.zhubao.erp.work.domain.WorkSession session() {
        return new com.zhubao.erp.work.domain.WorkSession() {
            public java.util.Optional<java.sql.Connection> connection() { return java.util.Optional.empty(); }
            public void onRollback(Runnable undo) { }
        };
    }
}
