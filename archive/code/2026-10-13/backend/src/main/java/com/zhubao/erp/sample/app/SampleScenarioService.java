package com.zhubao.erp.sample.app;

import com.zhubao.erp.sample.domain.SampleScenario;
import com.zhubao.erp.sample.domain.SampleScenarioStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SampleScenarioService {

    // M2 keeps deterministic review samples in code; M3 should move them to seed data.
    private final List<SampleScenario> scenarios = List.of(
            new SampleScenario(
                    "SAMPLE-MAIN-001",
                    "客户来料主链路",
                    "业务评审",
                    "验证从快递收货到追溯查询的端到端样例",
                    List.of("客户订单", "来料母单", "拆包称重", "子工单", "报工", "质检", "成品入库", "出库追溯"),
                    List.of("母单标签", "称重交接", "拆包视频", "库存台账", "质检照片"),
                    SampleScenarioStatus.READY
            ),
            new SampleScenario(
                    "SAMPLE-REWORK-001",
                    "质检返工样例",
                    "质检评审",
                    "验证返工只走一套工单状态机",
                    List.of("报工完成", "质检不合格", "生成返工工单", "返工报工", "复检放行"),
                    List.of("缺陷照片", "返工原因", "返工工单", "复检记录"),
                    SampleScenarioStatus.READY
            ),
            new SampleScenario(
                    "SAMPLE-LOSS-001",
                    "超标损耗审批样例",
                    "车间评审",
                    "验证损耗归因能追溯到称重交接和工序投入",
                    List.of("工序投入", "产出称重", "损耗计算", "超标审批", "工资试算扣减"),
                    List.of("投入重量", "产出重量", "审批记录", "工资试算快照"),
                    SampleScenarioStatus.NEEDS_DATA
            ),
            new SampleScenario(
                    "SAMPLE-HW-001",
                    "视频与标签兜底样例",
                    "硬件评审",
                    "验证硬件失败不阻塞主链路",
                    List.of("开始拆包", "记录时间段", "视频截取失败", "人工上传", "追溯关联"),
                    List.of("NVR 时间段", "失败日志", "人工上传文件", "操作日志"),
                    SampleScenarioStatus.READY
            )
    );

    public List<SampleScenario> findAll() {
        return scenarios;
    }

    public long readyCount() {
        return scenarios.stream()
                .filter(scenario -> scenario.status() == SampleScenarioStatus.READY)
                .count();
    }
}
