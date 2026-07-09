package com.zhubao.erp.machine.infra;

import com.zhubao.erp.machine.domain.MachineProfile;
import com.zhubao.erp.machine.domain.MachineProfileRepository;
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
@ConditionalOnProperty(name = "zhubao.machine-profile.repository", havingValue = "jdbc")
public class JdbcMachineProfileRepository implements MachineProfileRepository {

    private final DataSource dataSource;

    public JdbcMachineProfileRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public MachineProfile create(MachineProfile profile) {
        String sql = """
                insert into sys_machine (
                    machine_no, machine_name, machine_type, location, enabled, remark, created_by, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindColumns(statement, profile);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                Long id = keys.next() ? keys.getLong(1) : null;
                return new MachineProfile(
                        id, profile.machineNo(), profile.machineName(), profile.machineType(),
                        profile.location(), profile.enabled(), profile.remark(),
                        profile.createdBy(), profile.createdAt());
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to create machine profile: " + profile.machineNo(), ex);
        }
    }

    @Override
    public MachineProfile update(MachineProfile profile) {
        String sql = """
                update sys_machine
                   set machine_no = ?, machine_name = ?, machine_type = ?, location = ?,
                       enabled = ?, remark = ?
                 where id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, profile.machineNo());
            statement.setString(2, profile.machineName());
            statement.setString(3, profile.machineType());
            statement.setString(4, profile.location());
            statement.setBoolean(5, profile.enabled());
            statement.setString(6, profile.remark());
            statement.setLong(7, profile.id());
            statement.executeUpdate();
            return profile;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to update machine profile: " + profile.id(), ex);
        }
    }

    @Override
    public Optional<MachineProfile> findById(Long id) {
        String sql = selectSql() + " where id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load machine profile: " + id, ex);
        }
    }

    @Override
    public Optional<MachineProfile> findByMachineNo(String machineNo) {
        String sql = selectSql() + " where machine_no = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, machineNo);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load machine profile by no: " + machineNo, ex);
        }
    }

    @Override
    public List<MachineProfile> findAll() {
        String sql = selectSql() + " order by machine_no";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<MachineProfile> profiles = new ArrayList<>();
            while (rs.next()) {
                profiles.add(mapRow(rs));
            }
            return List.copyOf(profiles);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load machine profiles", ex);
        }
    }

    private void bindColumns(PreparedStatement statement, MachineProfile profile) throws SQLException {
        statement.setString(1, profile.machineNo());
        statement.setString(2, profile.machineName());
        statement.setString(3, profile.machineType());
        statement.setString(4, profile.location());
        statement.setBoolean(5, profile.enabled());
        statement.setString(6, profile.remark());
        statement.setString(7, profile.createdBy());
        statement.setString(8, profile.createdAt());
    }

    private String selectSql() {
        return "select id, machine_no, machine_name, machine_type, location, enabled, remark, created_by, created_at "
                + "from sys_machine";
    }

    private MachineProfile mapRow(ResultSet rs) throws SQLException {
        return new MachineProfile(
                rs.getLong("id"),
                rs.getString("machine_no"),
                rs.getString("machine_name"),
                rs.getString("machine_type"),
                rs.getString("location"),
                rs.getBoolean("enabled"),
                rs.getString("remark"),
                rs.getString("created_by"),
                rs.getString("created_at")
        );
    }
}
