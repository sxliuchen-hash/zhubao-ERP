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

    expect(wrapper.text()).toContain('来料收货拆包作业台');
    expect(wrapper.text()).toContain('今日母单池');
    expect(wrapper.find('table.inbound-dispatch-table').exists()).toBe(true);
    expect(wrapper.text()).toContain('入库证据槽');
    expect(wrapper.text()).toContain('入库校验规则');
    expect(wrapper.text()).toContain('确认入库');
    expect(wrapper.text()).toContain('拆包视频截取失败');
  });

  it('renders production station load and weighing handoff controls', async () => {
    const wrapper = mount(ProductionWorkspace);

    expect(wrapper.text()).toContain('生产派工报工作业台');
    expect(wrapper.text()).toContain('今日工单池');
    expect(wrapper.find('table.production-dispatch-table').exists()).toBe(true);
    expect(wrapper.findAll('table.production-dispatch-table tbody tr.is-selected')).toHaveLength(1);
    expect(wrapper.find('.active-object-card > strong').text()).toBe('ZD-20260706-006');
    expect(wrapper.text()).toContain('工位负载');
    expect(wrapper.text()).toContain('称重交接阻断');
    expect(wrapper.text()).toContain('扫码进站');
    expect(wrapper.text()).toContain('送检入账');
    expect(wrapper.text()).toContain('报工校验规则');
    expect(wrapper.text()).toContain('提交报工');
    expect(wrapper.text()).toContain('审批阻断');

    await wrapper.findAll('table.production-dispatch-table tbody tr')[0].trigger('click');

    expect(wrapper.find('.active-object-card > strong').text()).toBe('ZD-20260706-001');
  });

  it('renders quality metrics and loss approvals', async () => {
    const wrapper = mount(QualityWorkspace);

    expect(wrapper.text()).toContain('质检返工损耗作业台');
    expect(wrapper.text()).toContain('质检阻断队列');
    expect(wrapper.text()).toContain('今日质检池');
    expect(wrapper.find('table.quality-dispatch-table').exists()).toBe(true);
    expect(wrapper.findAll('table.quality-dispatch-table tbody tr.is-selected')).toHaveLength(1);
    expect(wrapper.find('.active-object-card > strong').text()).toBe('QC-20260706-021');
    expect(wrapper.text()).toContain('质检指标');
    expect(wrapper.text()).toContain('损耗审批');
    expect(wrapper.text()).toContain('冻结异常库存');
    expect(wrapper.text()).toContain('返工派发');
    expect(wrapper.text()).toContain('提交返工报工');
    expect(wrapper.text()).toContain('复检放行入库');
    expect(wrapper.text()).toContain('POST /rework-reports');
    expect(wrapper.text()).toContain('POST /quality-releases');
    expect(wrapper.text()).toContain('POST /loss-approvals');
    expect(wrapper.text()).toContain('工资试算快照');
    expect(wrapper.text()).toContain('复检放行');
    expect(wrapper.text()).toContain('LOSS-20260706-009');

    await wrapper.findAll('table.quality-dispatch-table tbody tr')[1].trigger('click');

    expect(wrapper.find('.active-object-card > strong').text()).toBe('QC-20260706-022');
  });

  it('renders inventory ledger details and trace evidence', async () => {
    const wrapper = mount(InventoryTraceWorkspace);

    expect(wrapper.text()).toContain('库存台账追溯作业台');
    expect(wrapper.text()).toContain('追溯阻断队列');
    expect(wrapper.text()).toContain('客户库存池');
    expect(wrapper.find('table.inventory-dispatch-table').exists()).toBe(true);
    expect(wrapper.findAll('table.inventory-dispatch-table tbody tr.is-selected')).toHaveLength(1);
    expect(wrapper.find('.active-object-card > strong').text()).toBe('MD-20260706-002');
    expect(wrapper.text()).toContain('确认出库');
    expect(wrapper.text()).toContain('退货入库');
    expect(wrapper.text()).toContain('售后复核');
    expect(wrapper.text()).toContain('客户权属');
    expect(wrapper.text()).toContain('证据归档');
    expect(wrapper.text()).toContain('库存台账明细');
    expect(wrapper.text()).toContain('追溯证据');
    expect(wrapper.text()).toContain('LEDGER-20260706-001');

    await wrapper.findAll('table.inventory-dispatch-table tbody tr')[1].trigger('click');

    expect(wrapper.find('.active-object-card > strong').text()).toBe('ZD-20260706-001');
  });

  it('renders mobile capability boundaries and offline drafts', () => {
    const wrapper = mount(MobileOpsWorkspace);

    expect(wrapper.text()).toContain('移动现场作业台');
    expect(wrapper.text()).toContain('PWA 能力边界');
    expect(wrapper.text()).toContain('离线草稿');
    expect(wrapper.text()).toContain('DRAFT-QC-003');
  });
});
