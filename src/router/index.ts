import { createRouter, createWebHashHistory, createWebHistory } from 'vue-router'
import routes from './routes'
import { useBaseStore } from '@/store/pinia'
import { IS_SUB_DOMAIN } from '@/config'

const router = createRouter({
  history: IS_SUB_DOMAIN ? createWebHashHistory() : createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    // console.log('savedPosition', savedPosition)
    if (savedPosition) {
      return savedPosition
    } else {
      return { top: 0 }
    }
  }
})

// Route-array order is an implementation detail, not navigation hierarchy.
// Keep a deterministic fallback for transition direction when a route is
// reached through an alias or a dynamically generated URL.
function navigationDepth(path: string): number {
  const normalized = path.split('?')[0].replace(/^\/+|\/+$/g, '')
  if (!normalized) return 0
  return normalized.split('/').length
}

router.beforeEach((to, from) => {
  // 清除滑动后的合成点击抑制标记，避免跨路由残留手势状态
  ;(window as any).isMoved = false
  ;(window as any).isMovedEl = null
  // Admin route guard
  if (to.path.startsWith('/admin')) {
    const baseStore = useBaseStore()
    const isAdmin = baseStore.isAdmin || localStorage.getItem('role') === 'ADMIN'
    if (!isAdmin) {
      return { path: '/home', replace: true }
    }
  }
  const baseStore = useBaseStore()
  //footer下面的5个按钮，对跳不要用动画
  const noAnimation = [
    '/',
    '/home',
    '/me',
    '/shop',
    '/message',
    '/publish',
    '/home/live',
    '/test',
    '/slide'
  ]
  if (noAnimation.indexOf(from.path) !== -1 && noAnimation.indexOf(to.path) !== -1) {
    return true
  }

  const toDepth = navigationDepth(to.path)
  const fromDepth = navigationDepth(from.path)
  // const fromDepth = routeDeep.indexOf(from.path)

  if (toDepth > fromDepth) {
    if (to.matched && to.matched.length) {
      const toComponentName = to.matched[0].components?.default.name
      baseStore.updateExcludeNames({ type: 'remove', value: toComponentName })
      // console.log('前进')
      // console.log('删除', toComponentName)
    }
  } else {
    if (from.matched && from.matched.length) {
      const fromComponentName = from.matched[0].components?.default.name
      baseStore.updateExcludeNames({ type: 'add', value: fromComponentName })

      // console.log('后退')
      // console.log('添加', fromComponentName)
    }
  }
  return true
})

;(window as any).$router = router
export default router
