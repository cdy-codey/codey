import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import VForm3 from 'vform3-builds'
import 'vform3-builds/dist/designer.style.css'
import App from './App.vue'
import { router } from './router'

createApp(App)
  .use(ElementPlus)
  .use(VForm3)
  .use(router)
  .mount('#app')
