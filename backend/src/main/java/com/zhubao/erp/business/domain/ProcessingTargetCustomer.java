package com.zhubao.erp.business.domain;

import java.util.Optional;

/**
 * 读取已经存在的普通加工母单或子单。找不到时返回空，不创建单据。
 */
public interface ProcessingTargetCustomer {

    Optional<ProcessingTargetFacts> facts(BusinessTargetReference target);
}
