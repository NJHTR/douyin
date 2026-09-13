<template>
  <NivoDesktopShell v-if="showDesktopShell" />
  <router-view v-else v-slot="{ Component }">
    <transition :name="transitionName">
      <keep-alive :exclude="store.excludeNames">
        <component :is="Component" />
      </keep-alive>
    </transition>
  </router-view>
  <Call />
  <CallPanel />
</template>
<script setup lang="ts">
/*
* try {navigator.control.gesture(false);} catch (e) {} //UC浏览器关闭默认手势事件
try {navigator.control.longpressMenu(false);} catch (e) {} //关闭长按弹出菜单
* */
import Call from './components/Call.vue'
import CallPanel from '@/modules/rtc/components/CallPanel.vue'
import NivoDesktopShell from './components/NivoDesktopShell.vue'
import { useBaseStore } from '@/store/pinia.js'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import bus from '@/utils/bus'
import {
  installNotificationFeedbackUnlock,
  installSoundLifecycle,
  notificationKindFromType,
  playSound
} from '@/utils/notificationFeedback'

const store = useBaseStore()
const route = useRoute()
const transitionName = ref('go')
const isDesktop = ref(false)
const showDesktopShell = computed(() => isDesktop.value && !route.path.startsWith('/login') && !route.path.startsWith('/admin'))
let mediaQuery: MediaQueryList | undefined
const updateViewportMode = () => { isDesktop.value = window.matchMedia('(min-width: 1000px)').matches }
let removeFeedbackUnlock = () => {}
let removeSoundLifecycle = () => {}

function navigationDepth(path: string): number {
  const normalized = path.split('?')[0].replace(/^\/+|\/+$/g, '')
  if (!normalized) return 0
  return normalized.split('/').length
}

function isFromCurrentUser(message: any): boolean {
  const currentUid = String(store.userinfo?.uid ?? '')
  if (!currentUid) return false
  return [message?.from_user_id, message?.fromUserId]
    .filter((value) => value !== undefined && value !== null)
    .some((value) => String(value) === currentUid)
}

function playIncomingFeedback(kind: 'chat' | 'group', message: any) {
  if (isFromCurrentUser(message)) return
  void playSound('message.received', {
    eventId: String(message?.id ?? message?.message_id ?? `${kind}:${message?.create_time ?? Date.now()}`),
    isCurrentConversation: route.path.includes('/chat/')
  })
}

function onChatMessage(message: any) {
  playIncomingFeedback('chat', message)
}

function onGroupMessage(message: any) {
  playIncomingFeedback('group', message)
}

function onNotification(message: any) {
  if (isFromCurrentUser(message)) return
  const kind = notificationKindFromType(message?.type)
  const event = kind === 'mention' || kind === 'friend' ? 'notification.important' : 'notification.normal'
  void playSound(event, {
    eventId: String(message?.id ?? message?.notification_id ?? `${event}:${message?.create_time ?? Date.now()}`),
    isCurrentConversation: route.path.includes('/chat/')
  })
}

function onCallSignal(message: any) {
  // RTC 通话的持续回铃/来电提示由 useRtcStore 按生命周期管理，避免
  // call_request 在这里再额外播放一次导致双重声音。
  void message
}

// watch $route 决定使用哪种过渡
watch(
  () => route.path,
  (to, from) => {
    store.setMaskDialog({ state: false, mode: store.maskDialogMode })
    //底部tab的按钮，跳转是不需要用动画的
    let noAnimation = [
      '/',
      '/home',
      '/slide',
      '/me',
      '/shop',
      '/message',
      '/publish',
      '/home/live',
      'slide',
      '/test'
    ]
    if (noAnimation.indexOf(from) !== -1 && noAnimation.indexOf(to) !== -1) {
      return (transitionName.value = '')
    }
    const toDepth = navigationDepth(to)
    const fromDepth = navigationDepth(from)
    transitionName.value = toDepth > fromDepth ? 'go' : 'back'
  }
)

function resetVhAndPx() {
  let vh = window.innerHeight * 0.01
  document.documentElement.style.setProperty('--vh', `${vh}px`)
  //document.documentElement.style.fontSize = document.documentElement.clientWidth / 375 + 'px'
}

const retryRestore = () => {
  if (localStorage.getItem('token') && !store.profileLoaded) void store.restoreSession()
}

onMounted(() => {
  mediaQuery = window.matchMedia('(min-width: 1000px)')
  updateViewportMode()
  mediaQuery.addEventListener?.('change', updateViewportMode)
  void store.init()
  window.addEventListener('online', retryRestore)
  window.addEventListener('focus', retryRestore)
  removeFeedbackUnlock = installNotificationFeedbackUnlock()
  removeSoundLifecycle = installSoundLifecycle()
  bus.on('CHAT_MESSAGE', onChatMessage)
  bus.on('GROUP_MESSAGE', onGroupMessage)
  bus.on('NEW_NOTIFICATION', onNotification)
  bus.on('CALL_SIGNAL', onCallSignal)
  resetVhAndPx()
  // 监听resize事件 视图大小发生变化就重新计算1vh的值
  window.addEventListener('resize', () => {
    resetVhAndPx()
  })
})

onUnmounted(() => {
  mediaQuery?.removeEventListener?.('change', updateViewportMode)
  removeFeedbackUnlock()
  removeSoundLifecycle()
  bus.off('CHAT_MESSAGE', onChatMessage)
  bus.off('GROUP_MESSAGE', onGroupMessage)
  bus.off('NEW_NOTIFICATION', onNotification)
  bus.off('CALL_SIGNAL', onCallSignal)
  window.removeEventListener('online', retryRestore)
  window.removeEventListener('focus', retryRestore)
})
</script>

<style lang="less">
@import './assets/less/index';

* {
  user-select: none;
}

input,
textarea {
  user-select: auto;
}

#app {
  height: 100%;
  width: 100%;
  position: relative;
  font-size: 14rem;
}

@media screen and (min-width: 1000px) {
  #app {
    width: 100% !important;
    position: relative;
  }
}

.go-enter-from {
  transform: translate3d(100%, 0, 0);
}

//最终状态
.back-enter-to,
.back-enter-from,
.go-enter-to,
.go-leave-from {
  transform: translate3d(0, 0, 0);
}

.go-leave-to {
  transform: translate3d(-100%, 0, 0);
}

.go-enter-active,
.go-leave-active,
.back-enter-active,
.back-leave-active {
  transition: all 0.3s;
}

.back-enter-from {
  transform: translate3d(-100%, 0, 0);
}

.back-leave-to {
  transform: translate3d(100%, 0, 0);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
