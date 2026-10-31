package com.zhubao.erp.inventory.app;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.CustomerOwnedStockView;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerRepository;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.security.app.CustomerPortalIdentityService;
import com.zhubao.erp.security.domain.OperatorContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 按权属客户汇总寄存、本厂、外协、质量和已发。只读，不写出库、客户账或库存。
 * 外协从本厂加工量中划出，不重复相加。公司料即使写了客户号也不计入。
 */
@Service
public class CustomerOwnedStockQuery {

    private final StockLedgerRepository stocks;
    private final CustomerPortalIdentityService identities;
    private final CustomerOwnedOutsideShares outsideShares;
    private final CustomerStockUnitSource units;

    public CustomerOwnedStockQuery(StockLedgerRepository stocks, CustomerPortalIdentityService identities,
                                   CustomerOwnedOutsideShares outsideShares, CustomerStockUnitSource units) {
        this.stocks = stocks;
        this.identities = identities;
        this.outsideShares = outsideShares;
        this.units = units;
    }

    public CustomerOwnedStockView query(OperatorContext operator) {
        OperatorContext portal = identities.portalOperator(operator == null ? "" : operator.username());
        long customerId = Long.parseLong(portal.customerScope().get(0));
        List<StockBalance> owned = new ArrayList<>();
        boolean withheldSampling = false;
        boolean withheldGoldwork = false;
        Set<String> targets = new TreeSet<>();
        for (StockBalance balance : stocks.findAllBalances()) {
            if (balance == null || balance.dimension() == null || balance.quantity() == null) {
                continue;
            }
            if (balance.quantity().signum() == 0) {
                continue;
            }
            StockDimension dimension = balance.dimension();
            if (dimension.ownerType() != OwnerType.CUSTOMER || dimension.ownerCustomerId() == null
                    || dimension.ownerCustomerId() != customerId) {
                continue;
            }
            BusinessKind kind = kindOf(dimension);
            if (kind == BusinessKind.SAMPLING) {
                withheldSampling = true;
                continue;
            }
            if (kind == BusinessKind.GOLDWORK) {
                withheldGoldwork = true;
                continue;
            }
            owned.add(balance);
            if (!dimension.workOrderCode().isBlank()) {
                targets.add(dimension.workOrderCode());
            }
            if (!dimension.motherOrderCode().isBlank()) {
                targets.add(dimension.motherOrderCode());
            }
        }
        List<CustomerOwnedOutsideShares.Share> shares = outsideShares.shares(customerId, targets);
        Map<String, Group> groups = new LinkedHashMap<>();
        for (StockBalance balance : owned) {
            StockDimension dimension = balance.dimension();
            String unit = unitOf(dimension.motherOrderCode());
            Group group = groups.computeIfAbsent(dimension.materialCode() + "\n" + unit,
                    key -> new Group(dimension.materialCode(), unit));
            group.add(bucketOf(dimension.stockStatus()), balance.quantity(), weightOf(balance));
            if (balance.weightPending()) {
                group.pending.add(bucketOf(dimension.stockStatus()));
            }
            group.targets.add(dimension.workOrderCode());
            group.targets.add(dimension.motherOrderCode());
        }
        List<CustomerOwnedStockView.Line> lines = new ArrayList<>();
        for (Group group : groups.values()) {
            applyOutside(group, shares);
            lines.addAll(group.lines());
        }
        lines.sort(Comparator.comparing(CustomerOwnedStockView.Line::materialCode)
                .thenComparing(CustomerOwnedStockView.Line::unit)
                .thenComparing(line -> line.bucket().name()));
        return new CustomerOwnedStockView(customerId, lines, List.of(
                new CustomerOwnedStockView.SourceRead(BusinessKind.PROCESSING, "READ", "已读取普通加工的客户自有库存", false),
                new CustomerOwnedStockView.SourceRead(BusinessKind.SAMPLING, "NOT_IMPLEMENTED",
                        "打样库存适配尚未实现，不能当作已核对的空库存", withheldSampling),
                new CustomerOwnedStockView.SourceRead(BusinessKind.GOLDWORK, "NOT_IMPLEMENTED",
                        "金工库存适配尚未实现，不能当作已核对的空库存", withheldGoldwork)
        ));
    }

    private void applyOutside(Group group, List<CustomerOwnedOutsideShares.Share> shares) {
        BigDecimal outside = BigDecimal.ZERO;
        boolean outsideWeightKnown = true;
        BigDecimal outsideKnown = BigDecimal.ZERO;
        boolean anyOutside = false;
        for (CustomerOwnedOutsideShares.Share share : shares) {
            if (!group.materialCode.equals(share.materialCode()) || !group.targets.contains(share.targetCode())) {
                continue;
            }
            anyOutside = true;
            outside = outside.add(share.quantity());
            if (share.weight() == null || share.weight().state() != StockMeasurement.WeightState.KNOWN
                    || share.weight().value() == null) {
                outsideWeightKnown = false;
            } else {
                outsideKnown = outsideKnown.add(share.weight().value());
            }
        }
        if (!anyOutside || outside.signum() <= 0) {
            return;
        }
        BigDecimal pool = group.quantity(CustomerOwnedStockView.Bucket.IN_FACTORY);
        BigDecimal moved = outside.min(pool);
        boolean exact = outside.compareTo(pool) == 0;
        group.take(CustomerOwnedStockView.Bucket.IN_FACTORY, moved);
        if (!exact || !outsideWeightKnown) {
            group.markWeightUnknown(CustomerOwnedStockView.Bucket.IN_FACTORY);
        }
        StockMeasurement.Weight outsideWeight = exact && outsideWeightKnown
                ? StockMeasurement.known(outsideKnown)
                : StockMeasurement.unknown();
        group.add(CustomerOwnedStockView.Bucket.OUTSOURCING, outside, outsideWeight);
        if (outside.compareTo(pool) > 0) {
            group.pending.add(CustomerOwnedStockView.Bucket.OUTSOURCING);
        }
    }

    private static CustomerOwnedStockView.Bucket bucketOf(StockStatus status) {
        return switch (status) {
            case RAW_WAITING -> CustomerOwnedStockView.Bucket.CONSIGNMENT;
            case FROZEN, REWORK -> CustomerOwnedStockView.Bucket.QUALITY;
            case SHIPPED -> CustomerOwnedStockView.Bucket.SHIPPED;
            case WIP, SEMI_FINISHED, FINISHED, SCRAP -> CustomerOwnedStockView.Bucket.IN_FACTORY;
        };
    }

    private static StockMeasurement.Weight weightOf(StockBalance balance) {
        if (balance.weightPending() || balance.weight() == null) {
            return StockMeasurement.unknown();
        }
        return StockMeasurement.known(balance.weight());
    }

    private String unitOf(String motherOrderCode) {
        if (units == null || motherOrderCode == null || motherOrderCode.isBlank()) {
            return "";
        }
        String unit = units.unitOf(motherOrderCode);
        return unit == null ? "" : unit.trim();
    }

    private static BusinessKind kindOf(StockDimension dimension) {
        for (String code : List.of(dimension.workOrderCode(), dimension.motherOrderCode())) {
            if (code == null || code.isBlank()) {
                continue;
            }
            try {
                return BusinessTargetReference.fromCode(code).kind();
            } catch (BusinessException ex) {
                return BusinessKind.PROCESSING;
            }
        }
        return BusinessKind.PROCESSING;
    }

    private static final class Group {
        private final String materialCode;
        private final String unit;
        private final Set<String> targets = new TreeSet<>();
        private final Map<CustomerOwnedStockView.Bucket, BigDecimal> quantities = new EnumMap<>(CustomerOwnedStockView.Bucket.class);
        private final Map<CustomerOwnedStockView.Bucket, BigDecimal> knownWeights = new EnumMap<>(CustomerOwnedStockView.Bucket.class);
        private final Map<CustomerOwnedStockView.Bucket, Boolean> unknownWeight = new EnumMap<>(CustomerOwnedStockView.Bucket.class);
        private final Set<CustomerOwnedStockView.Bucket> pending = new TreeSet<>();

        private Group(String materialCode, String unit) {
            this.materialCode = materialCode;
            this.unit = unit;
        }

        private void add(CustomerOwnedStockView.Bucket bucket, BigDecimal quantity, StockMeasurement.Weight weight) {
            if (quantity == null || quantity.signum() == 0) {
                return;
            }
            quantities.merge(bucket, quantity, BigDecimal::add);
            if (weight == null || weight.state() != StockMeasurement.WeightState.KNOWN || weight.value() == null) {
                unknownWeight.put(bucket, true);
                return;
            }
            knownWeights.merge(bucket, weight.value(), BigDecimal::add);
            unknownWeight.putIfAbsent(bucket, false);
        }

        private void take(CustomerOwnedStockView.Bucket bucket, BigDecimal quantity) {
            if (quantity == null || quantity.signum() <= 0) {
                return;
            }
            BigDecimal current = quantities.getOrDefault(bucket, BigDecimal.ZERO);
            BigDecimal next = current.subtract(quantity);
            if (next.signum() <= 0) {
                quantities.remove(bucket);
                return;
            }
            quantities.put(bucket, next);
        }

        private void markWeightUnknown(CustomerOwnedStockView.Bucket bucket) {
            if (!quantities.containsKey(bucket)) {
                return;
            }
            unknownWeight.put(bucket, true);
            knownWeights.remove(bucket);
        }

        private BigDecimal quantity(CustomerOwnedStockView.Bucket bucket) {
            return quantities.getOrDefault(bucket, BigDecimal.ZERO);
        }

        private List<CustomerOwnedStockView.Line> lines() {
            List<CustomerOwnedStockView.Line> lines = new ArrayList<>();
            for (Map.Entry<CustomerOwnedStockView.Bucket, BigDecimal> entry : quantities.entrySet()) {
                if (entry.getValue().signum() == 0) {
                    continue;
                }
                boolean unknown = Boolean.TRUE.equals(unknownWeight.get(entry.getKey()));
                BigDecimal known = knownWeights.get(entry.getKey());
                CustomerOwnedStockView.WeightRead read;
                BigDecimal shown;
                if (unknown && known == null) {
                    read = CustomerOwnedStockView.WeightRead.UNKNOWN;
                    shown = null;
                } else if (unknown) {
                    read = CustomerOwnedStockView.WeightRead.INCOMPLETE;
                    shown = known;
                } else {
                    read = CustomerOwnedStockView.WeightRead.KNOWN;
                    shown = known;
                }
                lines.add(new CustomerOwnedStockView.Line(
                        materialCode, unit, entry.getKey(), entry.getValue(), read, shown,
                        pending.contains(entry.getKey())));
            }
            return lines;
        }
    }
}
