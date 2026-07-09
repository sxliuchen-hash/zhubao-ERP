package com.zhubao.erp.security.infra;

import com.zhubao.erp.security.domain.NavigationMenuItem;
import com.zhubao.erp.security.domain.NavigationMenuRepository;
import com.zhubao.erp.security.domain.RoleCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Repository
@ConditionalOnProperty(name = "zhubao.security.menu.repository", havingValue = "jdbc")
public class JdbcNavigationMenuRepository implements NavigationMenuRepository {

    private final DataSource dataSource;

    public JdbcNavigationMenuRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<NavigationMenuItem> findMenuItems() {
        String sql = """
                select code, label, module, route, button_codes
                  from sys_navigation_menu
                 where enabled = 1
                 order by sort_order, id
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<NavigationMenuItem> items = new ArrayList<>();
            while (rs.next()) {
                items.add(new NavigationMenuItem(
                        rs.getString("code"),
                        rs.getString("label"),
                        rs.getString("module"),
                        rs.getString("route"),
                        parseButtonCodes(rs.getString("button_codes")),
                        List.of()
                ));
            }
            return List.copyOf(items);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load navigation menu items", ex);
        }
    }

    @Override
    public Map<RoleCode, Set<String>> findRoleMenuBindings() {
        String sql = "select role_code, menu_code from sys_role_menu";
        Map<RoleCode, Set<String>> bindings = new EnumMap<>(RoleCode.class);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                RoleCode role = parseRole(rs.getString("role_code"));
                if (role == null) {
                    continue;
                }
                bindings.computeIfAbsent(role, ignored -> new LinkedHashSet<>()).add(rs.getString("menu_code"));
            }
            return Map.copyOf(bindings);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load role menu bindings", ex);
        }
    }

    private List<String> parseButtonCodes(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .toList();
    }

    private RoleCode parseRole(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return RoleCode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
