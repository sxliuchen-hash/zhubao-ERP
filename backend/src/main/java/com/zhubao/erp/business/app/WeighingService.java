package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.ActivityBagRepository;
import com.zhubao.erp.business.domain.BagWeightFact;
import com.zhubao.erp.business.domain.BagWeightRepository;
import com.zhubao.erp.business.domain.BagWeightState;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 记录本袋实际重量。补录不改变工序数量，也不产生工费。
 * 待复称和未知都不是 0，不能按颗数把原袋重量分摊到子袋。
 */
@Service
public class WeighingService implements WeighingMarker {

    private final ActivityBagRepository bags;
    private final BagWeightRepository weights;
    private final BusinessWorkUnit workUnit;

    public WeighingService(ActivityBagRepository bags, BagWeightRepository weights, BusinessWorkUnit workUnit) {
        this.bags = bags;
        this.weights = weights;
        this.workUnit = workUnit;
    }

    @Override
    public void markPending(String bagId, WorkSession session) {
        BagWeightFact current = weights.find(bagId).orElse(null);
        int version = current == null ? 1 : current.version() + 1;
        weights.save(new BagWeightFact(bagId, BagWeightState.PENDING_REWEIGH, null, version), session);
    }

    public BagWeightFact current(String bagId) {
        BagWeightFact stored = weights.find(bagId).orElse(null);
        if (stored != null) {
            return stored;
        }
        ActivityBag bag = bags.find(bagId).orElseThrow(() -> new BusinessException("袋不存在"));
        if (bag.weight().state() == StockMeasurement.WeightState.KNOWN && bag.weight().value() != null) {
            return new BagWeightFact(bag.id(), BagWeightState.KNOWN, bag.weight().value(), bag.version());
        }
        return new BagWeightFact(bag.id(), BagWeightState.UNKNOWN, null, bag.version());
    }

    public void assertReadyForTransfer(String bagId) {
        BagWeightFact fact = current(bagId);
        if (fact.state() != BagWeightState.KNOWN || fact.value() == null) {
            throw new BusinessException("需要本份额的实际重量后才能转移或发货");
        }
    }

    /**
     * 零数量补录实际重量。只改适用重量和袋版本，不增加工序数量，不写工资。
     */
    public BagWeightFact supplement(String requestId, String bagId, int expectedVersion, BigDecimal weight, OperatorContext actor) {
        if (actor == null || actor.operator() == null || actor.operator().isBlank()) {
            throw new BusinessException("请先登录后再称重");
        }
        if (weight == null) {
            throw new BusinessException("请填写实际称重，未知重量不能按 0 补录");
        }
        if (weight.signum() < 0) {
            throw new BusinessException("重量不能为负");
        }
        ActivityBag bag = bags.find(required(bagId)).orElseThrow(() -> new BusinessException("袋不存在"));
        String payload = bag.id() + "|" + expectedVersion + "|" + weight.toPlainString() + "|0";
        String text = workUnit.execute(new CommandCall(
                requestId, actor.operator().trim(), "WEIGHT_SUPPLEMENT", "BAG", bag.id(), payload
        ), session -> {
            ActivityBag current = bags.find(bag.id()).orElseThrow(() -> new BusinessException("袋不存在"));
            if (current.version() != expectedVersion) {
                throw new BusinessException("袋已被其他人修改，请刷新后重试");
            }
            BigDecimal quantityBefore = current.quantity();
            ActivityBag weighed = new ActivityBag(
                    current.id(), current.targetCode(), current.materialCode(), current.requirementVersion(),
                    current.sourceKey(), current.parentId(), current.closed(), quantityBefore,
                    StockMeasurement.known(weight), current.version() + 1);
            bags.save(weighed, current.version(), session);
            BagWeightFact previous = weights.find(current.id()).orElse(null);
            int weightVersion = previous == null ? 1 : previous.version() + 1;
            BagWeightFact fact = new BagWeightFact(current.id(), BagWeightState.KNOWN, weight, weightVersion);
            weights.save(fact, session);
            if (bags.find(current.id()).orElseThrow().quantity().compareTo(quantityBefore) != 0) {
                throw new BusinessException("补录重量不能改变工序数量");
            }
            return fact.bagId() + "|" + fact.value().toPlainString() + "|" + fact.version();
        });
        String[] parts = text.split("\\|", -1);
        return new BagWeightFact(parts[0], BagWeightState.KNOWN, new BigDecimal(parts[1]), Integer.parseInt(parts[2]));
    }

    private static String required(String bagId) {
        if (bagId == null || bagId.isBlank()) {
            throw new BusinessException("袋不存在");
        }
        return bagId.trim();
    }
}
