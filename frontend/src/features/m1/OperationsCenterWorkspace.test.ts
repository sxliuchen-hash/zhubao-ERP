import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import OperationsCenterWorkspace from './OperationsCenterWorkspace.vue';

describe('OperationsCenterWorkspace', () => {
  it('renders logs, server metrics, errors, database backup and service status', () => {
    const wrapper = mount(OperationsCenterWorkspace);

    expect(wrapper.text()).toContain('运维中心');
    expect(wrapper.text()).toContain('M3.49 运行控制台');
    expect(wrapper.text()).toContain('PWA 静态资源');
    expect(wrapper.text()).toContain('前后端/数据库运行情况');
    expect(wrapper.text()).toContain('服务器运行指标');
    expect(wrapper.text()).toContain('报错汇总');
    expect(wrapper.text()).toContain('近期日志');
    expect(wrapper.text()).toContain('数据库备份情况');
    expect(wrapper.text()).toContain('文件大小');
    expect(wrapper.text()).toContain('SHA-256');
    expect(wrapper.text()).toContain('运维接入清单');
    expect(wrapper.text()).toContain('DB-BACKUP-POC');
    expect(wrapper.text()).toContain('TRACE-OPS-001');
    expect(wrapper.find('table.ops-data-table').exists()).toBe(true);
  });
});
