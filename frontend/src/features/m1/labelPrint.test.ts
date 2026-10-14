import { describe, expect, it } from 'vitest';
import type { LabelPayload } from '../../services/labelApi';
import { buildLabelMarkup, buildLabelPrintDocument } from './labelPrint';

const payload: LabelPayload = {
  type: 'MOTHER',
  typeLabel: '母单',
  primaryCode: 'CC-20260709-001-JL',
  qrContent: 'JG|mother|CC-20260709-001-JL',
  fields: [
    { name: '客户', value: '恒信珠宝' },
    { name: '备注', value: '<script>' },
  ],
  footerLeft: '快递：顺丰',
  footerRight: '收货人：何琴',
  printedAt: '（本地样例）',
  brandTitle: '珠宝来料加工系统',
  templateCode: '',
  printerSetting: { widthMm: 60, heightMm: 40, dpi: 203, printerName: '', copies: 2 },
};

describe('黑白标签打印', () => {
  it('按标签纸毫米尺寸出黑白页，不使用彩色色块', () => {
    const html = buildLabelPrintDocument(payload);

    expect(html).toContain('@page { size: 60mm 40mm; margin: 0; }');
    expect(html).toContain('width:60mm;height:40mm');
    expect(html.match(/class="bw-sheet"/g)).toHaveLength(2);
    expect(html).not.toContain('#f4b740');
    expect(html).not.toContain('#1f2733');
    expect(html).toContain('CC-20260709-001-JL');
    expect(html).toContain('<svg');
  });

  it('字段按文本输出，不把内容当成标记', () => {
    const html = buildLabelMarkup(payload);

    expect(html).toContain('&lt;script&gt;');
    expect(html).not.toContain('<script>');
    expect(html).not.toContain('扫码看实时进度');
  });

  it('40×30 贴纸突出识别信息并省去重复单号和脚注', () => {
    const compact: LabelPayload = {
      ...payload,
      fields: [
        { name: '验证码', value: 'K7M2' },
        { name: '客户', value: '恒信珠宝' },
        { name: '加工要求', value: '内壁刻字' },
        { name: '期限', value: '2026-10-20' },
        { name: '单号', value: payload.primaryCode },
        { name: '物料', value: '戒托' },
      ],
      printerSetting: { ...payload.printerSetting, widthMm: 40, heightMm: 30, dpi: 300 },
    };
    const html = buildLabelPrintDocument(compact);

    expect(html).toContain('@page { size: 40mm 30mm; margin: 0; }');
    expect(html).toContain('class="bw-label bw-label--compact"');
    expect(html).toContain('width:40mm;height:30mm');
    expect(html).toContain('K7M2');
    expect(html).toContain('内壁刻字');
    expect(html).not.toContain('<dt>单号</dt>');
    expect(html).not.toContain('<dt>物料</dt>');
    expect(html).not.toContain('快递：顺丰');
  });
});
