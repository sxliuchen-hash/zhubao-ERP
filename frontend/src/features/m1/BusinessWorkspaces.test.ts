import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import InboundWorkspace from './InboundWorkspace.vue';
import InventoryTraceWorkspace from './InventoryTraceWorkspace.vue';
import MobileOpsWorkspace from './MobileOpsWorkspace.vue';
import ProductionWorkspace from './ProductionWorkspace.vue';
import QualityWorkspace from './QualityWorkspace.vue';

describe('M1 business workspaces', () => {
  it('renders inbound receiving evidence and exception handling', () => {
    const wrapper = mount(InboundWorkspace);

    expect(wrapper.text()).toContain('来料接收控制台');
    expect(wrapper.text()).toContain('来料作业主控台');
    expect(wrapper.find('table.module-table').exists()).toBe(true);
    expect(wrapper.text()).toContain('入库检查点');
    expect(wrapper.text()).toContain('入库异常处置');
    expect(wrapper.text()).toContain('拆包视频截取失败');
  });

  it('renders production station load and weighing handoff controls', () => {
    const wrapper = mount(ProductionWorkspace);

    expect(wrapper.text()).toContain('生产工单调度台');
    expect(wrapper.text()).toContain('生产派工主控台');
    expect(wrapper.find('table.module-table').exists()).toBe(true);
    expect(wrapper.text()).toContain('工位负载');
    expect(wrapper.text()).toContain('称重交接记录');
    expect(wrapper.text()).toContain('审批阻断');
  });

  it('renders quality metrics and loss approvals', () => {
    const wrapper = mount(QualityWorkspace);

    expect(wrapper.text()).toContain('质检返工处理台');
    expect(wrapper.text()).toContain('质检返工真实动作核验');
    expect(wrapper.text()).toContain('质检作业主控台');
    expect(wrapper.find('table.module-table').exists()).toBe(true);
    expect(wrapper.text()).toContain('质检指标');
    expect(wrapper.text()).toContain('损耗审批');
    expect(wrapper.text()).toContain('冻结异常库存');
    expect(wrapper.text()).toContain('提交返工报工');
    expect(wrapper.text()).toContain('复检放行入库');
    expect(wrapper.text()).toContain('POST /rework-reports');
    expect(wrapper.text()).toContain('POST /quality-releases');
    expect(wrapper.text()).toContain('POST /loss-approvals');
    expect(wrapper.text()).toContain('工资试算快照');
    expect(wrapper.text()).toContain('复检放行');
    expect(wrapper.text()).toContain('LOSS-20260706-009');
  });

  it('renders inventory ledger details and trace evidence', () => {
    const wrapper = mount(InventoryTraceWorkspace);

    expect(wrapper.text()).toContain('库存与追溯证据台');
    expect(wrapper.text()).toContain('库存追溯主控台');
    expect(wrapper.find('table.module-table').exists()).toBe(true);
    expect(wrapper.text()).toContain('确认出库');
    expect(wrapper.text()).toContain('退货入库');
    expect(wrapper.text()).toContain('库存台账明细');
    expect(wrapper.text()).toContain('追溯证据');
    expect(wrapper.text()).toContain('LEDGER-20260706-001');
  });

  it('renders mobile capability boundaries and offline drafts', () => {
    const wrapper = mount(MobileOpsWorkspace);

    expect(wrapper.text()).toContain('移动现场作业台');
    expect(wrapper.text()).toContain('PWA 能力边界');
    expect(wrapper.text()).toContain('离线草稿');
    expect(wrapper.text()).toContain('DRAFT-QC-003');
  });
});
