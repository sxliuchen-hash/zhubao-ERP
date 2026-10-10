import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import OperationsCenterWorkspace from './OperationsCenterWorkspace.vue';

describe('OperationsCenterWorkspace', () => {
  it('renders logs, server metrics, errors, database backup and service status', () => {
    const wrapper = mount(OperationsCenterWorkspace);

    expect(wrapper.text()).toContain('运维中心');
    expect(wrapper.text()).toContain('硬件在线');
    expect(wrapper.text()).toContain('拆包台摄像头');
    expect(wrapper.text()).toContain('存储占用');
    expect(wrapper.text()).toContain('附件存储');
    expect(wrapper.text()).toContain('磁盘占用');
    expect(wrapper.text()).toContain('M3.62 运维告警汇总');
    expect(wrapper.text()).toContain('PWA 静态资源');
    expect(wrapper.text()).toContain('腾讯云 COS 备份');
    expect(wrapper.text()).toContain('告警汇总');
    expect(wrapper.text()).toContain('OPS-ALERT-BACKUP-DB-BACKUP-POC');
    expect(wrapper.text()).toContain('恢复演练校验');
    expect(wrapper.text()).toContain('前后端/数据库运行情况');
    expect(wrapper.text()).toContain('服务器运行指标');
    expect(wrapper.text()).toContain('报错汇总');
    expect(wrapper.text()).toContain('近期日志');
    expect(wrapper.text()).toContain('数据库备份情况');
    expect(wrapper.text()).toContain('结构化恢复演练记录');
    expect(wrapper.text()).toContain('RESTORE-DRILL-VERIFY-RESTORE-20260707-090000');
    expect(wrapper.text()).toContain('backup_checksum');
    expect(wrapper.text()).toContain('table_count');
    expect(wrapper.text()).toContain('文件大小');
    expect(wrapper.text()).toContain('SHA-256');
    expect(wrapper.text()).toContain('运维接入清单');
    expect(wrapper.text()).toContain('DB-BACKUP-POC');
    expect(wrapper.text()).toContain('RESTORE-DRILL-POC');
    expect(wrapper.text()).toContain('COS-BACKUP-DISABLED');
    expect(wrapper.text()).toContain('OPS-CHK-007');
    expect(wrapper.text()).toContain('OPS-CHK-008');
    expect(wrapper.text()).toContain('TRACE-OPS-001');
    expect(wrapper.find('table.ops-data-table').exists()).toBe(true);
  });
});
