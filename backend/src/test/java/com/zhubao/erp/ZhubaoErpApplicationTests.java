package com.zhubao.erp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ZhubaoErpApplicationTests {

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Test
    void contextLoads() {
    }

    /** 归档原型不再注册路由，避免旧接口仍被组件扫描带回运行时。 */
    @Test
    void archivedPrototypeApisAreNotRegistered() {
        var paths = requestMappingHandlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(mapping -> mapping.getPatternValues().stream())
                .toList();

        assertThat(paths).contains("/api/auth/accounts", "/api/auth/account-options", "/api/auth/accounts/{username}/delete",
                "/api/register-import/templates", "/api/documents/captures");
        assertThat(paths)
                .noneMatch(path -> path.startsWith("/api/configuration/baseline")
                        || path.startsWith("/api/samples")
                        || path.startsWith("/api/video-clip-tasks"));
    }
}
