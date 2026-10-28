/** 安卓容器里的 WebView 不使用 PWA。安卓 10 上 Service Worker 接管页面后，有的机会一直白屏。 */
export function shouldRegisterServiceWorker(userAgent: string, production: boolean): boolean {
  return production && !userAgent.includes('ZhubaoErpShell');
}

export function registerServiceWorker() {
  if (!('serviceWorker' in navigator)) return;
  if (!shouldRegisterServiceWorker(navigator.userAgent, import.meta.env.PROD)) {
    void navigator.serviceWorker.getRegistrations().then((registrations) => {
      registrations.forEach((registration) => {
        void registration.unregister();
      });
    });
    return;
  }
  window.addEventListener('load', () => {
    void navigator.serviceWorker.register('/sw.js');
  });
}
