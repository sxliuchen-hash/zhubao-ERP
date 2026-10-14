import { describe, expect, it } from 'vitest';
import { previewTarget } from './previewNavigation';

describe('证据预览导航', () => {
  it('APK 内使用当前窗口，浏览器使用新标签页', () => {
    expect(previewTarget('Mozilla/5.0 ZhubaoErpShell/0.1.0')).toBe('_self');
    expect(previewTarget('Mozilla/5.0 Chrome/120.0')).toBe('_blank');
  });
});
