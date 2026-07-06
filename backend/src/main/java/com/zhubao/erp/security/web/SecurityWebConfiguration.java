package com.zhubao.erp.security.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SecurityWebConfiguration implements WebMvcConfigurer {

    private final PermissionGuardInterceptor permissionGuardInterceptor;

    public SecurityWebConfiguration(PermissionGuardInterceptor permissionGuardInterceptor) {
        this.permissionGuardInterceptor = permissionGuardInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(permissionGuardInterceptor);
    }
}
