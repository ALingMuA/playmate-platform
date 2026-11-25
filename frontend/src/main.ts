import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

import App from './App.vue'
import router from './router'
import { setUnauthorizedHandler } from './api/http'
import { useUserStore } from './stores/user'
import './styles/global.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)

setUnauthorizedHandler(async () => {
  const userStore = useUserStore(pinia)
  userStore.logout()
  const current = router.currentRoute.value
  const isInitial = current.matched.length === 0
  const currentPath = isInitial && typeof location !== 'undefined' ? location.pathname : current.path
  if (!currentPath.startsWith('/login')) {
    const redirect = isInitial && typeof location !== 'undefined'
      ? `${location.pathname}${location.search}${location.hash}`
      : current.fullPath
    await router.replace({
      path: '/login',
      query: redirect && redirect !== '/' && redirect !== '/login' ? { redirect } : undefined,
    })
  }
})

app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
