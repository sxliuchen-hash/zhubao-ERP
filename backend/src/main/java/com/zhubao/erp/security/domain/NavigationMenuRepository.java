package com.zhubao.erp.security.domain;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Source of the navigation menu tree and per-role menu bindings.
 *
 * <p>Extracted from the hard-coded service so menus and role visibility can be customized per
 * deployment. The in-memory implementation preserves the original review defaults; the JDBC
 * implementation reads from {@code sys_navigation_menu} / {@code sys_role_menu}.</p>
 */
public interface NavigationMenuRepository {

    /** The full ordered menu tree (all items, regardless of role). */
    List<NavigationMenuItem> findMenuItems();

    /** Menu codes visible to each role. */
    Map<RoleCode, Set<String>> findRoleMenuBindings();
}
