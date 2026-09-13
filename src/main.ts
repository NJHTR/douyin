import { createApp } from 'vue'
import App from './App.vue'
import './assets/less/index.less'
import router from './router'
import mixin from './utils/mixin'
import VueLazyload from '@jambonn/vue-lazyload'
import { createPinia } from 'pinia'
import { useClick } from '@/utils/hooks/useClick'
import bus, { EVENT_KEY } from '@/utils/bus'

window.isMoved = false
;(window as any).isMovedEl = null
window.isMuted = true
window.showMutedNotice = true

// A swipe can generate a synthetic click immediately after pointerup.  Keep
// the suppression local to that one gesture instead of monkey-patching the
// browser's EventTarget prototype (which breaks removeEventListener identity
// and causes listener leaks in Vue/third-party components).
document.addEventListener(
  'click',
  (event) => {
    if (!window.isMoved) return
    const movedEl = (window as any).isMovedEl as HTMLElement | null
    if (movedEl?.contains?.(event.target as Node)) {
      event.preventDefault()
      event.stopImmediatePropagation()
    }
  },
  true
)

const vClick = useClick()
const pinia = createPinia()
const app = createApp(App)
app.config.errorHandler = (err: any, instance: any, info: string) => {
  console.error('[Vue Error]', err, '\n  component:', instance?.$?.type?.name || instance?.type?.name || 'unknown', '\n  info:', info)
}
app.mixin(mixin)
const loadImage = new URL('./assets/img/icon/img-loading.png', import.meta.url).href
app.use(VueLazyload, {
  preLoad: 1.3,
  loading: loadImage,
  attempt: 1
})
app.use(pinia)
app.use(router)
app.mount('#app')
app.directive('click', vClick)

// 真实后端: http://localhost:8080/api
// v1.0 曾使用 startMock() 本地数据
setTimeout(() => {
  bus.emit(EVENT_KEY.HIDE_MUTED_NOTICE)
  window.showMutedNotice = false
}, 2000)
bus.on(EVENT_KEY.REMOVE_MUTED, () => {
  window.isMuted = false
})
