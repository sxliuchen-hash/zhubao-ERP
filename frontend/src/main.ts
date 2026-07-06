import { createApp } from 'vue';
import App from './App.vue';
import { registerServiceWorker } from './pwa/registerServiceWorker';
import './styles.css';

createApp(App).mount('#app');
registerServiceWorker();
