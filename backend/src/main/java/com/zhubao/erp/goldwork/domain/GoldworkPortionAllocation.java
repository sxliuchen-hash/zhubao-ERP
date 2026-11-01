package com.zhubao.erp.goldwork.domain;

import com.zhubao.erp.common.error.BusinessException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 同一报工来源内的声明份额限额，不换算成色、单位或材料库存。 */
public final class GoldworkPortionAllocation {
    private GoldworkPortionAllocation() {}

    public static void assertDecision(GoldworkOperationReport report, List<GoldworkQualityDecision> decisions,
                                      List<GoldworkDelivery> deliveries, GoldworkQualityDecision incoming) {
        if (!incoming.priorQualityId().isEmpty()) {
            assertHistory(report, decisions, deliveries, decision(decisions, incoming.priorQualityId()));
        }
        assertDecisionSource(report, decisions, deliveries, incoming);
    }

    public static void assertDelivery(GoldworkOperationReport report, List<GoldworkQualityDecision> decisions,
                                      List<GoldworkDelivery> deliveries, GoldworkQualityDecision quality, GoldworkDelivery incoming) {
        if (report != null) assertHistory(report, decisions, deliveries, quality);
        List<Portion> used = decisions.stream().filter(item -> quality.qualityId().equals(item.priorQualityId()))
                .map(GoldworkPortionAllocation::portion).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        deliveries.stream().filter(item -> quality.qualityId().equals(item.qualityId())).map(GoldworkPortionAllocation::portion).forEach(used::add);
        assertAvailable(portion(quality), used, portion(incoming));
    }

    private static void assertHistory(GoldworkOperationReport report, List<GoldworkQualityDecision> decisions,
                                      List<GoldworkDelivery> deliveries, GoldworkQualityDecision current) {
        while (true) {
            assertDecisionSource(report, decisions, deliveries, current);
            if (current.priorQualityId().isEmpty()) return;
            current = decision(decisions, current.priorQualityId());
        }
    }

    private static void assertDecisionSource(GoldworkOperationReport report, List<GoldworkQualityDecision> decisions,
                                             List<GoldworkDelivery> deliveries, GoldworkQualityDecision incoming) {
        if (!report.reportId().equals(incoming.reportId()) || !report.goldworkCode().equals(incoming.goldworkCode())
                || !report.purity().equals(incoming.purity())) throw new BusinessException("质量份额与报工来源不一致");
        GoldworkQualityDecision parent = incoming.priorQualityId().isEmpty() ? null : decision(decisions, incoming.priorQualityId());
        if (parent != null && !parent.reportId().equals(report.reportId())) throw new BusinessException("返工只影响本份额");
        Portion source = parent == null ? new Portion(report.pieceCount(), report.netWeight(), report.weightUnit()) : portion(parent);
        List<Portion> used = decisions.stream().filter(item -> report.reportId().equals(item.reportId())
                        && incoming.priorQualityId().equals(item.priorQualityId()) && !incoming.qualityId().equals(item.qualityId()))
                .map(GoldworkPortionAllocation::portion).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (parent != null) deliveries.stream().filter(item -> parent.qualityId().equals(item.qualityId()))
                .map(GoldworkPortionAllocation::portion).forEach(used::add);
        assertAvailable(source, used, portion(incoming));
    }

    private static GoldworkQualityDecision decision(List<GoldworkQualityDecision> decisions, String id) {
        return decisions.stream().filter(item -> id.equals(item.qualityId())).findFirst()
                .orElseThrow(() -> new BusinessException("原轮次不存在"));
    }

    private static void assertAvailable(Portion source, List<Portion> used, Portion incoming) {
        if (incoming.weight != null && !source.unit.equals(incoming.unit)
                || used.stream().anyMatch(item -> item.weight != null && !source.unit.equals(item.unit))) {
            throw new BusinessException("份额重量单位必须与原来源一致");
        }
        assertQuantity(source.pieces, used.stream().map(Portion::pieces).toList(), incoming.pieces, "件数");
        assertQuantity(source.weight, used.stream().map(Portion::weight).toList(), incoming.weight, "重量");
    }

    private static void assertQuantity(BigDecimal source, List<BigDecimal> used, BigDecimal incoming, String name) {
        if (source == null) {
            if (incoming != null || used.stream().anyMatch(java.util.Objects::nonNull)) throw new BusinessException("来源" + name + "未知，份额待核");
            return;
        }
        if (incoming == null || used.stream().anyMatch(java.util.Objects::isNull)) throw new BusinessException("来源已知" + name + "，本份额或历史份额缺失，待核");
        BigDecimal total = used.stream().reduce(incoming, BigDecimal::add);
        if (total.compareTo(source) > 0) throw new BusinessException("累计" + name + "超过原来源可用份额");
    }

    private static Portion portion(GoldworkQualityDecision item) { return new Portion(item.pieceCount(), item.netWeight(), item.weightUnit()); }
    private static Portion portion(GoldworkDelivery item) { return new Portion(item.pieceCount(), item.netWeight(), item.weightUnit()); }
    private record Portion(BigDecimal pieces, BigDecimal weight, String unit) {
        private Portion { unit = unit == null ? "" : unit.trim(); }
    }
}
