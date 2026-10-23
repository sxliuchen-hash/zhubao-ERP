import { createApp } from 'vue';
import App from './App.vue';
import { registerServiceWorker } from './pwa/registerServiceWorker';
import './styles.css';
import './features/workspace/workspaceShell.css';
import './business-workspaces.css';
import './system-workspaces.css';
import './register-import.css';

createApp(App).mount('#app');
registerServiceWorker();
