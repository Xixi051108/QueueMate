import { createApp } from 'vue'
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElInputNumber,
  ElOption,
  ElSelect,
  ElSkeleton,
  ElTag,
} from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './styles/tokens.css'
import './styles/global.css'

const app = createApp(App)

;[
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElInputNumber,
  ElOption,
  ElSelect,
  ElSkeleton,
  ElTag,
].forEach((component) => app.component(component.name, component))

app.use(router).mount('#app')
