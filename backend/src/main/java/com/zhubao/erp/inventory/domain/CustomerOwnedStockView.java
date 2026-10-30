package com.zhubao.erp.inventory.domain;

import com.zhubao.erp.business.domain.BusinessKind;

import java.math.BigDecimal;
import java.util.List;

/**
 * 一个客户的自有库存只读结果。外协是受托范围里的其中一项，不能再加进本厂。
 * 打样和金工适配未实现时不会返回空成功。
 */
public record CustomerOwnedStockView(
        long customerId,
        List<Line> lines,
        List<SourceRead> sources
) {
    public CustomerOwnedStockView {
        lines = lines == null ? List.of() : List.copyOf(lines);
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public enum Bucket {
        CONSIGNMENT,
        IN_FACTORY,
        OUTSOURCING,
        QUALITY,
        SHIPPED
    }

    public enum WeightRead {
        KNOWN,
        UNKNOWN,
        INCOMPLETE
    }

    public record Line(
            String materialCode,
            String unit,
            Bucket bucket,
            BigDecimal quantity,
            WeightRead weightRead,
            BigDecimal knownWeight,
            boolean reconciliationPending
    ) {
    }

    public record SourceRead(BusinessKind kind, String state, String note, boolean withheld) {
    }
}
