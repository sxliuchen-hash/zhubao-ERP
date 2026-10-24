package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.domain.MasterDataRepository;
import com.zhubao.erp.business.domain.MaterialProfile;
import com.zhubao.erp.business.domain.RequirementShortcut;
import com.zhubao.erp.business.domain.RequirementTemplate;
import com.zhubao.erp.common.error.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.master-data.repository", havingValue = "jdbc")
public class JdbcMasterDataRepository implements MasterDataRepository {
    private final DataSource dataSource;

    public JdbcMasterDataRepository(DataSource dataSource) { this.dataSource = dataSource; }

    @Override
    public List<CustomerProfile> listCustomers() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select * from biz_customer_profile order by customer_id");
             ResultSet rs = statement.executeQuery()) {
            List<CustomerProfile> rows = new ArrayList<>();
            while (rs.next()) rows.add(customer(rs));
            return rows;
        } catch (SQLException ex) { throw new IllegalStateException("客户档案读取失败", ex); }
    }

    @Override
    public Optional<CustomerProfile> findCustomer(long id) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select * from biz_customer_profile where customer_id = ?")) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(customer(rs)) : Optional.empty();
            }
        } catch (SQLException ex) { throw new IllegalStateException("客户档案读取失败", ex); }
    }

    @Override
    public CustomerProfile saveCustomer(CustomerProfile profile) {
        try (Connection connection = dataSource.getConnection()) {
            if (profile.customerId() > 0) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        update biz_customer_profile set display_name=?, normalized_name=?, aliases=?, contact_name=?,
                        phone=?, remark=?, enabled=? where customer_id=?
                        """)) {
                    bindCustomer(statement, profile, 1);
                    statement.setLong(8, profile.customerId());
                    if (statement.executeUpdate() != 1) throw new BusinessException("客户档案不存在");
                }
                return findCustomer(profile.customerId()).orElseThrow();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    insert into biz_customer_profile (customer_code, display_name, normalized_name, aliases,
                    contact_name, phone, remark, enabled) values (?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, profile.customerCode());
                bindCustomer(statement, profile, 2);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) throw new IllegalStateException("客户编号生成失败");
                    return findCustomer(keys.getLong(1)).orElseThrow();
                }
            }
        } catch (SQLException ex) { throw new BusinessException("客户编码或名称已存在，请核对档案"); }
    }

    private void bindCustomer(PreparedStatement statement, CustomerProfile profile, int start) throws SQLException {
        statement.setString(start, profile.name());
        statement.setString(start + 1, profile.name().trim().toLowerCase(java.util.Locale.ROOT));
        statement.setString(start + 2, profile.aliases());
        statement.setString(start + 3, profile.contact());
        statement.setString(start + 4, profile.phone());
        statement.setString(start + 5, profile.remark());
        statement.setBoolean(start + 6, profile.enabled());
    }

    @Override
    public List<MaterialProfile> listMaterials() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select * from biz_material_catalog order by display_name, material_code");
             ResultSet rs = statement.executeQuery()) {
            List<MaterialProfile> rows = new ArrayList<>();
            while (rs.next()) rows.add(material(rs));
            return rows;
        } catch (SQLException ex) { throw new IllegalStateException("材质目录读取失败", ex); }
    }

    @Override
    public Optional<MaterialProfile> findMaterial(String code) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select * from biz_material_catalog where material_code = ?")) {
            statement.setString(1, code);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(material(rs)) : Optional.empty();
            }
        } catch (SQLException ex) { throw new IllegalStateException("材质目录读取失败", ex); }
    }

    @Override
    public MaterialProfile saveMaterial(MaterialProfile profile) {
        String sql = findMaterial(profile.materialCode()).isPresent()
                ? "update biz_material_catalog set display_name=?, normalized_name=?, aliases=?, remark=?, enabled=?, storage_conditions=?, storage_instruction=? where material_code=?"
                : "insert into biz_material_catalog (display_name, normalized_name, aliases, remark, enabled, storage_conditions, storage_instruction, material_code) values (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                MaterialProfile previous;
                try (PreparedStatement lock = connection.prepareStatement("select * from biz_material_catalog where material_code=? for update")) {
                    lock.setString(1, profile.materialCode());
                    try (ResultSet rs = lock.executeQuery()) { previous = rs.next() ? material(rs) : null; }
                }
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, profile.name());
                    statement.setString(2, profile.name().trim().toLowerCase(java.util.Locale.ROOT));
                    statement.setString(3, profile.aliases()); statement.setString(4, profile.remark()); statement.setBoolean(5, profile.enabled());
                    statement.setString(6, profile.storageConditions() == null ? null : String.join("|", profile.storageConditions()));
                    statement.setString(7, profile.storageInstruction()); statement.setString(8, profile.materialCode());
                    statement.executeUpdate();
                }
                var before = com.zhubao.erp.business.domain.StorageRequirements.of(previous);
                var after = com.zhubao.erp.business.domain.StorageRequirements.of(profile);
                if (!java.util.Objects.equals(before, after)) {
                    try (PreparedStatement audit = connection.prepareStatement("insert into biz_material_storage_change (material_code, previous_conditions, previous_instruction, current_conditions, current_instruction, changed_at) values (?,?,?,?,?,?)")) {
                        audit.setString(1, profile.materialCode()); audit.setString(2, before == null ? null : before.encoded());
                        audit.setString(3, before == null ? null : before.instruction()); audit.setString(4, after == null ? null : after.encoded());
                        audit.setString(5, after == null ? null : after.instruction()); audit.setString(6, java.time.LocalDateTime.now().toString()); audit.executeUpdate();
                    }
                }
                connection.commit(); return profile;
            } catch (SQLException | RuntimeException ex) { connection.rollback(); throw ex; }
        } catch (SQLException ex) { throw new BusinessException("材质编码或名称已存在，或数据库尚未升级，请核对"); }
    }

    @Override
    public List<com.zhubao.erp.business.domain.MaterialStorageChange> storageChanges(String code) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("select * from biz_material_storage_change where material_code=? order by change_id")) {
            statement.setString(1, code);
            try (ResultSet rs = statement.executeQuery()) {
                var changes = new ArrayList<com.zhubao.erp.business.domain.MaterialStorageChange>();
                while (rs.next()) changes.add(new com.zhubao.erp.business.domain.MaterialStorageChange(code,
                        com.zhubao.erp.business.domain.StorageRequirements.read(rs.getString("previous_conditions"), rs.getString("previous_instruction")),
                        com.zhubao.erp.business.domain.StorageRequirements.read(rs.getString("current_conditions"), rs.getString("current_instruction")), rs.getString("changed_at")));
                return changes;
            }
        } catch (SQLException ex) { throw new IllegalStateException("保存要求变更记录读取失败", ex); }
    }

    private CustomerProfile customer(ResultSet rs) throws SQLException {
        return new CustomerProfile(rs.getLong("customer_id"), rs.getString("customer_code"),
                rs.getString("display_name"), rs.getString("aliases"), rs.getString("contact_name"),
                rs.getString("phone"), rs.getString("remark"), rs.getBoolean("enabled"));
    }

    private MaterialProfile material(ResultSet rs) throws SQLException {
        return new MaterialProfile(rs.getString("material_code"), rs.getString("display_name"),
                rs.getString("aliases"), rs.getString("remark"), rs.getBoolean("enabled"),
                rs.getString("storage_conditions") == null ? null : (rs.getString("storage_conditions").isEmpty() ? List.of() : java.util.Arrays.asList(rs.getString("storage_conditions").split("\\|"))), rs.getString("storage_instruction"));
    }

    @Override
    public List<RequirementShortcut> listShortcuts() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select shortcut_id, phrase, sort_order from biz_requirement_shortcut order by sort_order, shortcut_id");
             ResultSet rs = statement.executeQuery()) {
            List<RequirementShortcut> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(new RequirementShortcut(rs.getLong("shortcut_id"), rs.getString("phrase"), rs.getInt("sort_order")));
            }
            return rows;
        } catch (SQLException ex) {
            throw new IllegalStateException("加工要求快捷词读取失败", ex);
        }
    }

    @Override
    public RequirementShortcut saveShortcut(RequirementShortcut shortcut) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into biz_requirement_shortcut (phrase, normalized_phrase, sort_order) values (?, ?, ?)
                     """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, shortcut.phrase());
            statement.setString(2, shortcut.phrase().trim().toLowerCase(java.util.Locale.ROOT));
            statement.setInt(3, shortcut.sortOrder());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new IllegalStateException("快捷词编号生成失败");
                long id = keys.getLong(1);
                return listShortcuts().stream().filter(item -> item.shortcutId() == id).findFirst().orElseThrow();
            }
        } catch (SQLException ex) {
            throw new BusinessException("快捷词已存在");
        }
    }

    @Override
    public Optional<RequirementShortcut> deleteShortcut(long shortcutId) {
        RequirementShortcut existing = listShortcuts().stream()
                .filter(item -> item.shortcutId() == shortcutId).findFirst().orElse(null);
        if (existing == null) return Optional.empty();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "delete from biz_requirement_shortcut where shortcut_id = ?")) {
            statement.setLong(1, shortcutId);
            if (statement.executeUpdate() != 1) return Optional.empty();
            return Optional.of(existing);
        } catch (SQLException ex) {
            throw new IllegalStateException("加工要求快捷词删除失败", ex);
        }
    }

    @Override
    public List<RequirementTemplate> listTemplates() {
        try (Connection connection = dataSource.getConnection()) {
            return loadTemplates(connection, null);
        } catch (SQLException ex) {
            throw new IllegalStateException("加工要求模板读取失败", ex);
        }
    }

    @Override
    public Optional<RequirementTemplate> findTemplate(String templateCode) {
        try (Connection connection = dataSource.getConnection()) {
            List<RequirementTemplate> rows = loadTemplates(connection, templateCode);
            return rows.stream().findFirst();
        } catch (SQLException ex) {
            throw new IllegalStateException("加工要求模板读取失败", ex);
        }
    }

    @Override
    public Optional<RequirementTemplate> findTemplateByRequest(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return Optional.empty();
        }
        String sql = "select template_code from biz_requirement_template where request_key = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, requestId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return findTemplate(rs.getString("template_code"));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("加工要求模板读取失败", ex);
        }
    }

    @Override
    public RequirementTemplate insertTemplate(RequirementTemplate template) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previous = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                long id;
                try (PreparedStatement statement = connection.prepareStatement("""
                        insert into biz_requirement_template
                            (template_code, template_name, normalized_name, enabled, sort_order, version, request_key)
                        values (?, ?, ?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, template.templateCode());
                    statement.setString(2, template.name());
                    statement.setString(3, template.name().trim().toLowerCase(java.util.Locale.ROOT));
                    statement.setBoolean(4, template.enabled());
                    statement.setInt(5, template.sortOrder());
                    statement.setInt(6, template.version());
                    if (template.requestId() == null || template.requestId().isBlank()) {
                        statement.setNull(7, java.sql.Types.VARCHAR);
                    } else {
                        statement.setString(7, template.requestId());
                    }
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new IllegalStateException("模板编号生成失败");
                        }
                        id = keys.getLong(1);
                    }
                }
                insertSteps(connection, id, template.steps());
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                if (ex instanceof BusinessException business) {
                    throw business;
                }
                Optional<RequirementTemplate> winner = findTemplateByRequest(template.requestId());
                if (winner.isPresent()) {
                    RequirementTemplate saved = winner.get();
                    if (!saved.name().equals(template.name()) || !saved.steps().equals(template.steps())
                            || saved.sortOrder() != template.sortOrder()) {
                        throw new BusinessException("同一请求的模板内容不一致");
                    }
                    return saved;
                }
                throw new BusinessException("模板名称或编号已存在");
            } finally {
                connection.setAutoCommit(previous);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("加工要求模板写入失败", ex);
        }
        return findTemplate(template.templateCode()).orElseThrow();
    }

    @Override
    public RequirementTemplate updateTemplate(RequirementTemplate template, int expectedVersion) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previous = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                long id;
                try (PreparedStatement statement = connection.prepareStatement("""
                        update biz_requirement_template
                           set template_name = ?, normalized_name = ?, enabled = ?, sort_order = ?, version = version + 1
                         where template_code = ? and version = ?
                        """)) {
                    statement.setString(1, template.name());
                    statement.setString(2, template.name().trim().toLowerCase(java.util.Locale.ROOT));
                    statement.setBoolean(3, template.enabled());
                    statement.setInt(4, template.sortOrder());
                    statement.setString(5, template.templateCode());
                    statement.setInt(6, expectedVersion);
                    if (statement.executeUpdate() != 1) {
                        throw new BusinessException("模板已被其他人修改，请刷新后再保存");
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "select template_id from biz_requirement_template where template_code = ?")) {
                    statement.setString(1, template.templateCode());
                    try (ResultSet rs = statement.executeQuery()) {
                        if (!rs.next()) {
                            throw new BusinessException("加工要求模板不存在");
                        }
                        id = rs.getLong(1);
                    }
                }
                try (PreparedStatement delete = connection.prepareStatement(
                        "delete from biz_requirement_template_step where template_id = ?")) {
                    delete.setLong(1, id);
                    delete.executeUpdate();
                }
                insertSteps(connection, id, template.steps());
                connection.commit();
            } catch (BusinessException ex) {
                connection.rollback();
                throw ex;
            } catch (SQLException ex) {
                connection.rollback();
                throw new BusinessException("模板名称已存在");
            } finally {
                connection.setAutoCommit(previous);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("加工要求模板写入失败", ex);
        }
        return findTemplate(template.templateCode()).orElseThrow();
    }

    private void insertSteps(Connection connection, long templateId, List<String> steps) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into biz_requirement_template_step (template_id, step_order, phrase) values (?, ?, ?)")) {
            int order = 1;
            for (String step : steps) {
                statement.setLong(1, templateId);
                statement.setInt(2, order++);
                statement.setString(3, step);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private List<RequirementTemplate> loadTemplates(Connection connection, String templateCode) throws SQLException {
        String sql = """
                select template_id, template_code, template_name, enabled, sort_order, version, request_key
                  from biz_requirement_template
                """ + (templateCode == null ? " order by sort_order, template_id" : " where template_code = ?");
        List<RequirementTemplate> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (templateCode != null) {
                statement.setString(1, templateCode);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("template_id");
                    rows.add(new RequirementTemplate(id, rs.getString("template_code"), rs.getString("template_name"),
                            loadSteps(connection, id), rs.getBoolean("enabled"), rs.getInt("sort_order"),
                            rs.getInt("version"), rs.getString("request_key")));
                }
            }
        }
        return rows;
    }

    private List<String> loadSteps(Connection connection, long templateId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select phrase from biz_requirement_template_step where template_id = ? order by step_order")) {
            statement.setLong(1, templateId);
            try (ResultSet rs = statement.executeQuery()) {
                List<String> steps = new ArrayList<>();
                while (rs.next()) {
                    steps.add(rs.getString("phrase"));
                }
                return steps;
            }
        }
    }
}
