package com.zhubao.erp.label.domain;

import java.util.List;
import java.util.Optional;

/**
 * 标签模板仓储。遵循"内存默认 + JDBC 可切换"模式。
 */
public interface LabelTemplateRepository {

    /** 新增模板，返回带自增主键的模板。 */
    LabelTemplate create(LabelTemplate template);

    /** 更新模板（按 id）。 */
    LabelTemplate update(LabelTemplate template);

    Optional<LabelTemplate> findById(Long id);

    Optional<LabelTemplate> findByTemplateCode(String templateCode);

    /** 查询某标签类型的默认模板。 */
    Optional<LabelTemplate> findDefaultByType(LabelType labelType);

    List<LabelTemplate> findAll();

    /** 清除某标签类型下所有模板的默认标记（设置新默认前调用）。 */
    void clearDefaultForType(LabelType labelType, Long exceptId);
}
