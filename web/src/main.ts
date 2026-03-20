import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import { AUTH_UNAUTHORIZED_EVENT } from './api/http'
import { pinia } from './stores'
import { useAuthStore } from './stores/auth'
import './styles/index.css'

const app = createApp(App)

app.use(pinia)
app.use(router)
app.use(ElementPlus)

const authStore = useAuthStore(pinia)

window.addEventListener(AUTH_UNAUTHORIZED_EVENT, async () => {
  authStore.reset()
  if (router.currentRoute.value.path !== '/login') {
    await router.replace('/login')
  }
})

app.mount('#app')
