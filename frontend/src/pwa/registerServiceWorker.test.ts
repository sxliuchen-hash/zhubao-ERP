import { describe, expect, it } from 'vitest';
import { shouldRegisterServiceWorker } from './registerServiceWorker';

describe('service worker registration', () => {
  it('stays off inside the Android shell even in production', () => {
    expect(shouldRegisterServiceWorker('Mozilla/5.0 ZhubaoErpShell/0.1', true)).toBe(false);
  });

  it('registers for a normal production browser', () => {
    expect(shouldRegisterServiceWorker('Mozilla/5.0 Chrome/120.0.0.0', true)).toBe(true);
  });

  it('does not register during local development', () => {
    expect(shouldRegisterServiceWorker('Mozilla/5.0 Chrome/120.0.0.0', false)).toBe(false);
  });
});