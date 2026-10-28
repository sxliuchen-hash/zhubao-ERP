import { installLegacyWebViewPolyfills, recoverBlankPage } from './compat/legacyWebView';
import { createApp } from 'vue';
import App from './App.vue';
import { registerServiceWorker } from './pwa/registerServiceWorker';
import './styles.css';
import './features/workspace/workspaceShell.css';
import './business-workspaces.css';
import './system-workspaces.css';
import './register-import.css';
import './information-layout.css';

installLegacyWebViewPolyfills();

const app = createApp(App);
app.config.errorHandler = (error) => {
  console.error(error);
  // Vue 先回调这里，再把失败的根节点补成注释。等这一帧补完再判断是不是真的空白。
  globalThis.setTimeout?.(recoverBlankPage, 0);
};
app.mount('#app');
registerServiceWorker();
