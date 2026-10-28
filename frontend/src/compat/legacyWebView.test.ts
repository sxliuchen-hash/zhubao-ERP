import { afterEach, describe, expect, it } from 'vitest';
import { installLegacyWebViewPolyfills, recoverBlankPage } from './legacyWebView';

describe('legacy WebView', () => {
  const originalAt = Array.prototype.at;

  afterEach(() => {
    if (originalAt) Object.defineProperty(Array.prototype, 'at', { configurable: true, writable: true, value: originalAt });
  });

  it('fills Array.prototype.at for Android 10 WebViews and keeps negative indexes', () => {
    Reflect.deleteProperty(Array.prototype, 'at');
    installLegacyWebViewPolyfills();

    expect([1, 2, 3].at(-1)).toBe(3);
    expect([1, 2, 3].at(0)).toBe(1);
    expect([1, 2, 3].at(9)).toBeUndefined();
  });

  it('shows a way out when the signed-in page rendered nothing', () => {
    const root = document.createElement('div');
    recoverBlankPage(root);

    expect(root.textContent).toContain('页面没有显示出来');
    expect(root.querySelector('button')?.textContent).toBe('清除登录并重试');
  });

  it('leaves a visible workbench alone', () => {
    const root = document.createElement('div');
    root.innerHTML = '<h1>工作台</h1>';
    recoverBlankPage(root);

    expect(root.querySelector('h1')?.textContent).toBe('工作台');
    expect(root.textContent).not.toContain('页面没有显示出来');
  });
});
