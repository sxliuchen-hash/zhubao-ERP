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
  it('紧凑模式为冷真圆章保留固定位置并显示来源', () => {
    const html = buildLabelMarkup({ ...payload, printerSetting: { ...payload.printerSetting, widthMm: 40, heightMm: 30 }, storageSources: [{ sourceCode: 'MD-1:1', sourceLabel: '明细1', materialCode: 'XY', recorded: true, marks: ['冷', '真'], instruction: '' }] });
    expect(html).toContain('bw-has-storage');
    expect(html).toContain('--storage-height:5mm');
    expect(html.match(/<circle /g)).toHaveLength(2);
    expect(html).toContain('明细1');
    expect(html).not.toContain('ⓒ');
  });
  it('多来源或说明放不下时拒绝打印，不静默截断', () => {
    const source = { sourceCode: 'MD-1:1', sourceLabel: '明细1', materialCode: 'XY', recorded: true, marks: ['冷', '真'], instruction: '' };
    expect(() => buildLabelMarkup({ ...payload, printerSetting: { ...payload.printerSetting, widthMm: 40, heightMm: 30 }, storageSources: [source, { ...source, sourceCode: 'MD-1:2', sourceLabel: '明细2' }] })).toThrow('空间不足');
    const large = buildLabelMarkup({ ...payload, storageSources: [source, { ...source, sourceCode: 'MD-1:2', sourceLabel: '明细2' }] });
    expect(large).toContain('明细2');
    expect(large.match(/<circle /g)).toHaveLength(4);
  });

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
    expect(html).toContain('<div class="bw-top"><span class="bw-type">母单</span><span class="bw-code">CC-20260709-001-JL</span></div>');
    expect(html.indexOf('class="bw-top"')).toBeLessThan(html.indexOf('class="bw-qr"'));
    expect(html).toContain('float: left');
    expect(html).toContain('K7M2');
    expect(html).toContain('内壁刻字');
    expect(html).not.toContain('<dt>单号</dt>');
    expect(html).not.toContain('<dt>物料</dt>');
    expect(html).not.toContain('快递：顺丰');
  });

  it('紧凑贴纸在选择客户暗记后显示页脚字母', () => {
    const marked: LabelPayload = {
      ...payload,
      fields: [
        { name: '期限', value: '2026-10-20' },
      ],
      footerLeft: '',
      footerRight: 'HXZB',
      templateCode: '',
      printerSetting: { ...payload.printerSetting, widthMm: 40, heightMm: 30, dpi: 300 },
    };
    const html = buildLabelMarkup(marked);
    expect(html).toContain('class="bw-foot"');
    expect(html).toContain('HXZB');
    expect(html).not.toContain('恒信珠宝');
  });

  it('自定义模板在紧凑纸张保留全部选定字段并应用字号版式', () => {
    const configured: LabelPayload = {
      ...payload,
      templateCode: 'MOTHER-CUSTOM',
      fields: [
        { name: '客户', value: '甲' }, { name: '要求', value: '乙' },
        { name: '期限', value: '丙' }, { name: '重量', value: '丁' },
        { name: '单号', value: '戊' },
      ],
      printerSetting: { ...payload.printerSetting, widthMm: 40, heightMm: 30 },
      layout: { mode: 'COMPACT', qrWidthMm: 15, codeFontPt: 10, fieldFontPt: 8,
        emphasisFontPt: 9, titleFontPt: 7, footerFontPt: 6 },
    };
    const html = buildLabelPrintDocument(configured);
    expect(html).toContain('--bw-qr-width:15mm;');
    expect(html).toContain('--bw-code-font:10pt;');
    expect(html).toContain('--bw-field-font:8pt;');
    expect(html).toContain('<dt>重量</dt>');
    expect(html).toContain('<dt>单号</dt>');
    expect(html).toContain('快递：顺丰');
    expect(html).toContain('class="bw-label bw-label--compact"');
  });

  it('版式选择可覆盖纸张尺寸，异常字号不会进入打印样式', () => {
    const configured: LabelPayload = {
      ...payload,
      layout: { mode: 'STANDARD', qrWidthMm: 20, codeFontPt: 500, fieldFontPt: null,
        emphasisFontPt: null, titleFontPt: null, footerFontPt: null },
      printerSetting: { ...payload.printerSetting, widthMm: 40, heightMm: 30 },
    };
    const html = buildLabelMarkup(configured);
    expect(html).not.toContain('bw-label--compact');
    expect(html).toContain('--bw-qr-width:20mm;');
    expect(html).not.toContain('500pt');
  });
});
