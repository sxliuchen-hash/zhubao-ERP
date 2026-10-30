package com.zhubao.erp.finance.domain;

import java.util.Optional;

public interface EmployeeTargetClosureLookup {

    Optional<EmployeeTargetClosure> find(String targetCode);
}
