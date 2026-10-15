package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.domain.MasterDataRepository;
import com.zhubao.erp.business.domain.MaterialProfile;
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
                ? "update biz_material_catalog set display_name=?, normalized_name=?, aliases=?, remark=?, enabled=? where material_code=?"
                : "insert into biz_material_catalog (display_name, normalized_name, aliases, remark, enabled, material_code) values (?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, profile.name());
            statement.setString(2, profile.name().trim().toLowerCase(java.util.Locale.ROOT));
            statement.setString(3, profile.aliases());
            statement.setString(4, profile.remark());
            statement.setBoolean(5, profile.enabled());
            statement.setString(6, profile.materialCode());
            statement.executeUpdate();
            return profile;
        } catch (SQLException ex) { throw new BusinessException("材质编码或名称已存在，请选择现有材质"); }
    }

    private CustomerProfile customer(ResultSet rs) throws SQLException {
        return new CustomerProfile(rs.getLong("customer_id"), rs.getString("customer_code"),
                rs.getString("display_name"), rs.getString("aliases"), rs.getString("contact_name"),
                rs.getString("phone"), rs.getString("remark"), rs.getBoolean("enabled"));
    }

    private MaterialProfile material(ResultSet rs) throws SQLException {
        return new MaterialProfile(rs.getString("material_code"), rs.getString("display_name"),
                rs.getString("aliases"), rs.getString("remark"), rs.getBoolean("enabled"));
    }
}
