package com.zhubao.erp.security.app;

import com.zhubao.erp.security.domain.NavigationMenuItem;
import com.zhubao.erp.security.domain.NavigationMenuRepository;
import com.zhubao.erp.security.domain.RoleCode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class NavigationMenuService {

    private final NavigationMenuRepository repository;

    public NavigationMenuService(NavigationMenuRepository repository) {
        this.repository = repository;
    }

    public List<NavigationMenuItem> menuTreeFor(RoleCode role) {
        Set<String> allowedCodes = roleMenus().getOrDefault(role, Set.of("dashboard"));
        return repository.findMenuItems().stream()
                .filter(item -> allowedCodes.contains(item.code()))
                .toList();
    }

    public List<String> menuCodesFor(RoleCode role) {
        return menuTreeFor(role).stream()
                .map(NavigationMenuItem::code)
                .toList();
    }

    public List<NavigationMenuItem> fullMenuTree() {
        return repository.findMenuItems();
    }

    private Map<RoleCode, Set<String>> roleMenus() {
        return repository.findRoleMenuBindings();
    }
}
