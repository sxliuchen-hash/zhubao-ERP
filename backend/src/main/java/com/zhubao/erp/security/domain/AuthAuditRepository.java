package com.zhubao.erp.security.domain;

import java.util.List;

public interface AuthAuditRepository {

    AuthAuditEvent save(AuthAuditEvent event);

    List<AuthAuditEvent> findRecent(int limit);
}
