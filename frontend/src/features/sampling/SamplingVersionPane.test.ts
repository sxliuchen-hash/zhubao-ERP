import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import SamplingVersionPane from './SamplingVersionPane.vue';
import { addSamplingVersion, confirmSamplingVersion, fetchSamplingVersions } from '../../services/samplingApi';

vi.mock('../../services/samplingApi', () => ({
  fetchSamplingVersions: vi.fn(),
  addSamplingVersion: vi.fn(),
  confirmSamplingVersion: vi.fn(),
  rejectSamplingVersion: vi.fn(),
}));

const pending = {
  samplingCode: 'DY-20261031-0001',
  kind: 'MODEL',
  kindLabel: '模型',
  versionNo: 1,
  parameterText: '直径 18',
  evidenceCode: '',
  modelFileName: '戒托.3dm',
  modelFormat: '3DM',
  modelVersion: 'v1',
  modelLocationNote: '样品柜',
  modelEvidenceCode: '',
  modelReferenced: true,
  sourceCustody: 'EXTERNAL_REFERENCE',
  backupClaim: 'NOT_BACKED_UP',
  decision: 'PENDING',
  decisionLabel: '待确认',
  decisionReason: '',
  decisionCredential: '',
  decidedBy: '',
  decidedOn: '',
};

describe('打样版本与客户确认', () => {
  it('待确认版本显式提示，确认不上传模型文件', async () => {
    vi.mocked(fetchSamplingVersions).mockResolvedValue([pending]);
    vi.mocked(confirmSamplingVersion).mockResolvedValue({ ...pending, decision: 'CONFIRMED', decisionLabel: '已确认' });
    const wrapper = mount(SamplingVersionPane);
    await wrapper.get('[aria-label="版本打样号"]').setValue('DY-20261031-0001');
    await wrapper.get('button').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('待确认');
    expect(wrapper.text()).toContain('存放引用不代表已备份外部原件');
    expect(wrapper.find('input[type="file"]').exists()).toBe(false);
    await wrapper.findAll('button').find((button) => button.text() === '确认')!.trigger('click');
    await flushPromises();
    expect(vi.mocked(confirmSamplingVersion)).toHaveBeenCalledWith('DY-20261031-0001', expect.objectContaining({
      kind: 'MODEL',
      versionNo: 1,
    }));
    expect(vi.mocked(addSamplingVersion)).not.toHaveBeenCalled();
  });
});
