<template>
  <div class="nivo-shell">
    <aside class="nivo-sidebar">
      <div class="brand">
        <img class="brand-logo" src="/gznxl-gu0vc-001.ico" alt="NIVO" /><strong>NIVO</strong>
      </div>
      <div class="sidebar-group">
        <nav class="primary-nav" aria-label="主导航">
          <RouterLink
            v-for="item in primaryNav.slice(0, 3)"
            :key="item.label"
            :to="item.to"
            :class="{ active: isActive(item.to) }"
            ><Icon :icon="item.icon" /><span>{{ item.label }}</span></RouterLink
          >
        </nav>
      </div>
      <div class="nav-divider"></div>
      <div class="sidebar-group">
        <nav class="primary-nav" aria-label="社交导航">
          <RouterLink
            v-for="item in primaryNav.slice(3)"
            :key="item.label"
            :to="item.to"
            :class="{ active: isActive(item.to) }"
            ><Icon :icon="item.icon" /><span>{{ item.label }}</span></RouterLink
          >
        </nav>
      </div>
      <div class="nav-divider"></div>
      <div class="sidebar-group">
        <nav class="secondary-nav" aria-label="发现">
          <RouterLink v-for="item in secondaryNav" :key="item.label" :to="item.to"
            ><Icon :icon="item.icon" /><span>{{ item.label }}</span
            ><b v-if="item.badge">●</b></RouterLink
          >
        </nav>
      </div>
      <div class="sidebar-spacer"></div>
      <button class="download-card" type="button" @click="loginVisible = true">
        <Icon icon="solar:download-minimalistic-linear" /><strong>下载 NIVO 客户端</strong
        ><small>桌面端体验更完整</small>
      </button>
      <div class="sidebar-tools">
        <button title="设置" type="button" @click="router.push('/me/right-menu/setting')">
          <Icon icon="solar:settings-linear" /></button
        ><button title="更多" type="button"><Icon icon="solar:widget-2-linear" /></button
        ><button title="帮助" type="button"><Icon icon="solar:question-circle-linear" /></button>
      </div>
    </aside>

    <div class="nivo-main">
      <header class="nivo-header">
        <form class="search-form" @submit.prevent="submitSearch">
          <input
            v-model="search"
            placeholder="搜索你感兴趣的内容"
            aria-label="搜索"
            @keydown.enter.prevent="submitSearch"
          /><button v-if="search" type="button" aria-label="清除" @click="search = ''">
            <Icon icon="solar:close-circle-linear" /></button
          ><button class="search-submit" type="submit" aria-label="搜索">
            <Icon icon="solar:magnifer-linear" />
          </button>
        </form>
        <div class="header-actions">
          <button type="button" class="header-link" @click="router.push('/shop')">
            <Icon icon="solar:wallet-money-linear" /><span>充钻石</span></button
          ><button type="button" class="header-link" @click="loginVisible = true">
            <Icon icon="solar:monitor-linear" /><span>客户端</span></button
          ><button type="button" class="header-link" @click="router.push('/shop')">
            <Icon icon="solar:wallpaper-linear" /><span>壁纸</span></button
          ><button type="button" class="header-link has-new" @click="router.push('/message')">
            <Icon icon="solar:bell-linear" /><span>通知</span><i>NEW</i></button
          ><button type="button" class="header-link" @click="router.push('/message')">
            <Icon icon="solar:chat-round-dots-linear" /><span>消息</span></button
          ><button type="button" class="header-link" @click="router.push('/publish')">
            <Icon icon="solar:add-square-linear" /><span>投稿</span>
          </button>
          <div
            class="profile-trigger"
            @mouseenter="cancelProfileHide"
            @mouseleave="scheduleProfileHide"
          >
            <button
              type="button"
              class="profile-button"
              :aria-label="store.isLoggedIn ? '打开个人菜单' : '登录'"
              @click="store.isLoggedIn ? (profileVisible = !profileVisible) : (loginVisible = true)"
            >
              <img :src="displayAvatar" alt="个人头像" @error="avatarFailed = true" />
            </button>
            <div v-if="profileVisible && store.isLoggedIn" class="profile-menu">
              <div class="profile-summary">
                <img :src="displayAvatar" alt="" @error="avatarFailed = true" />
                <div>
                  <strong>{{ store.userinfo.nickname || 'NIVO 用户' }}</strong
                  ><small>个人账号</small>
                </div>
              </div>
              <button
                v-for="item in profileMenu"
                :key="item.label"
                type="button"
                @click="router.push(item.to)"
              >
                <Icon :icon="item.icon" /><span>{{ item.label }}</span
                ><Icon class="menu-arrow" icon="solar:alt-arrow-right-linear" />
              </button>
            </div>
            <button
              v-else-if="profileVisible"
              type="button"
              class="profile-login-hint"
              @click="loginVisible = true"
            >
              <Icon icon="solar:login-3-linear" /><span>登录</span>
            </button>
          </div>
        </div>
      </header>
      <div class="nivo-content">
        <NivoDesktopHome v-if="isHome" /><NivoDesktopRecommend
          v-else-if="isRecommend"
        /><NivoDesktopVideoDetail v-else-if="route.path === '/video-detail'" /><RouterView v-else />
      </div>
    </div>

    <Teleport to="body"
      ><div v-if="loginVisible" class="login-backdrop" @click.self="loginVisible = false">
        <form
          class="login-modal"
          role="dialog"
          aria-modal="true"
          aria-label="登录 NIVO"
          @submit.prevent="submitLogin"
        >
          <button
            class="modal-close"
            type="button"
            aria-label="关闭登录窗口"
            @click="loginVisible = false"
          >
            <Icon icon="solar:close-circle-linear" />
          </button>
          <h2>登录后解锁完整内容</h2>
          <div class="login-columns">
            <div class="qr-column">
              <h3>NIVO 客户端登录</h3>
              <div class="qr-frame brand-qr"><img :src="brandLogo" alt="NIVO 标志" /></div>
              <p>使用 NIVO 客户端登录</p>
              <button type="button">客户端说明</button>
            </div>
            <div class="account-column">
              <div class="login-tabs"><b>邮箱验证码登录</b></div>
              <label :class="{ filled: loginEmail.trim() }"
                ><input
                  v-model="loginEmail"
                  type="email"
                  autocomplete="email"
                  placeholder="请输入邮箱" /></label
              ><label :class="{ filled: loginCode.trim() }"
                ><input
                  v-model="loginCode"
                  inputmode="numeric"
                  autocomplete="one-time-code"
                  placeholder="请输入验证码"
                /><button
                  type="button"
                  :class="{ ready: loginEmail.trim() && !sendingCode && !sendCodeCooldown }"
                  :disabled="!loginEmail.trim() || sendingCode || sendCodeCooldown > 0"
                  @click="sendCode"
                >
                  {{
                    sendingCode
                      ? '发送中'
                      : sendCodeCooldown > 0
                        ? `${sendCodeCooldown}s 后重新发送`
                        : '获取验证码'
                  }}
                </button></label
              >
              <p v-if="loginError" class="login-error">{{ loginError }}</p>
              <button
                class="modal-login"
                :class="{ ready: loginEmail.trim() && loginCode.trim() }"
                type="submit"
                :disabled="!loginEmail.trim() || !loginCode.trim() || loggingIn"
              >
                {{ loggingIn ? '登录中...' : '登录' }}</button
              ><small>登录即代表同意 用户协议 和 隐私政策</small>
            </div>
          </div>
        </form>
      </div></Teleport
    >
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import NivoDesktopHome from './NivoDesktopHome.vue'
import NivoDesktopVideoDetail from './NivoDesktopVideoDetail.vue'
import NivoDesktopRecommend from './NivoDesktopRecommend.vue'
import avatarImage from '@/assets/img/avatar.png'
import brandLogo from '@/assets/logo.png'
import { loginByEmail, sendEmailCode } from '@/api/auth'
import { panel } from '@/api/user'
import { connectSocket } from '@/utils/socket'
import { _checkImgUrl } from '@/utils'
import { useBaseStore } from '@/store/pinia'
import bus from '@/utils/bus'
const route = useRoute()
const router = useRouter()
const search = ref('')
const loginVisible = ref(false)
const profileVisible = ref(false)
const avatarFailed = ref(false)
const profileHideTimer = ref<number | null>(null)
const loginEmail = ref('')
const loginCode = ref('')
const sendingCode = ref(false)
const sendCodeCooldown = ref(0)
const loggingIn = ref(false)
const loginError = ref('')
const store = useBaseStore()
let sendCodeTimer: number | null = null
let sendRequestKey = ''
function avatarSource(value: any): string {
  if (!value) return ''
  if (typeof value === 'string') return value
  if (Array.isArray(value)) return avatarSource(value[0])
  if (Array.isArray(value.url_list)) return avatarSource(value.url_list[0])
  return value.url || value.uri || value.avatar168Url || value.avatar_url || ''
}
const rawAvatar = computed(
  () =>
    avatarSource(store.userinfo.avatar_168x168) ||
    avatarSource(store.userinfo.avatar_300x300) ||
    avatarSource(store.userinfo.avatar) ||
    avatarSource(store.userinfo.avatar_url) ||
    ''
)
const currentAvatar = computed(() => _checkImgUrl(rawAvatar.value) || avatarImage)
const displayAvatar = computed(() => {
  if (!store.isLoggedIn) return '/gznxl-gu0vc-001.ico'
  return avatarFailed.value ? avatarImage : currentAvatar.value
})
const isRecommend = computed(() => route.path === '/' && route.query.recommend === '1')
const isHome = computed(
  () => ['/home', '/slide', '/', '/jingxuan'].includes(route.path) && !isRecommend.value
)
const primaryNav = [
  { label: '精选', to: '/jingxuan', icon: 'solar:flame-linear' },
  { label: '推荐', to: '/?recommend=1', icon: 'solar:stars-linear' },
  { label: 'AI抖音', to: '/aisearch', icon: 'solar:magic-stick-3-linear' },
  { label: '关注', to: '/follow', icon: 'solar:user-heart-linear' },
  { label: '朋友', to: '/friend', icon: 'solar:users-group-rounded-linear' },
  { label: '我的', to: '/user/self?showTab=like', icon: 'solar:user-circle-linear' }
]
const secondaryNav = [
  { label: '直播', to: '/live', icon: 'solar:videocamera-record-linear' },
  { label: '放映厅', to: '/vs', icon: 'solar:clapperboard-play-linear' },
  { label: '短剧', to: '/series', icon: 'solar:play-stream-linear' },
  { label: '小游戏', to: '/microgame', icon: 'solar:gamepad-linear', badge: true }
]
const profileMenu = [
  { label: '个人主页', to: '/user/self', icon: 'solar:user-linear' },
  { label: '观看历史', to: '/me/right-menu/look-history', icon: 'solar:history-linear' },
  { label: '我的收藏', to: '/user/self?showTab=collect', icon: 'solar:star-linear' },
  { label: '内容偏好', to: '/me/right-menu/setting', icon: 'solar:sliders-minimalistic-linear' },
  { label: '设置', to: '/me/right-menu/setting', icon: 'solar:settings-linear' }
]
function isActive(to: string) {
  if (to === '/jingxuan')
    return route.path === '/jingxuan' || (route.path === '/home' && route.query.recommend !== '1')
  if (to === '/?recommend=1') return route.path === '/' && route.query.recommend === '1'
  const path = to.split('?')[0]
  return route.path === path || route.path.startsWith(path + '/')
}
function submitSearch() {
  if (search.value.trim()) router.push({ path: '/home/search', query: { q: search.value.trim() } })
}
function cancelProfileHide() {
  if (profileHideTimer.value) window.clearTimeout(profileHideTimer.value)
  profileHideTimer.value = null
  profileVisible.value = true
}
function scheduleProfileHide() {
  if (profileHideTimer.value) window.clearTimeout(profileHideTimer.value)
  profileHideTimer.value = window.setTimeout(() => {
    profileVisible.value = false
    profileHideTimer.value = null
  }, 600)
}
watch(currentAvatar, () => {
  avatarFailed.value = false
})
const cooldownStorageKey = computed(
  () => `nivo:email-code-cooldown:${loginEmail.value.trim().toLowerCase()}`
)
function beginCodeCooldown(seconds: number) {
  sendCodeCooldown.value = Math.max(1, Math.ceil(seconds))
  localStorage.setItem(cooldownStorageKey.value, String(Date.now() + sendCodeCooldown.value * 1000))
  if (sendCodeTimer) window.clearInterval(sendCodeTimer)
  sendCodeTimer = window.setInterval(() => {
    sendCodeCooldown.value = Math.max(
      0,
      Math.ceil((Number(localStorage.getItem(cooldownStorageKey.value) || 0) - Date.now()) / 1000)
    )
    if (!sendCodeCooldown.value) {
      sendingCode.value = false
      if (sendCodeTimer) {
        window.clearInterval(sendCodeTimer)
        sendCodeTimer = null
      }
    }
  }, 1000)
}
function restoreCodeCooldown() {
  const expiresAt = Number(localStorage.getItem(cooldownStorageKey.value) || 0)
  if (expiresAt > Date.now()) beginCodeCooldown(Math.ceil((expiresAt - Date.now()) / 1000))
  else localStorage.removeItem(cooldownStorageKey.value)
}
watch(loginEmail, () => {
  sendCodeCooldown.value = 0
  sendingCode.value = false
  restoreCodeCooldown()
})
function newRequestKey() {
  return typeof crypto !== 'undefined' && crypto.randomUUID
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`
}
async function sendCode() {
  const email = loginEmail.value.trim().toLowerCase()
  if (!email || sendingCode.value || sendCodeCooldown.value > 0) return
  sendingCode.value = true
  loginError.value = ''
  sendRequestKey ||= newRequestKey()
  const res = await sendEmailCode(email, sendRequestKey)
  const retryAfter = Number((res.data as any)?.retryAfterSeconds || 0)
  if (res.success) {
    beginCodeCooldown(retryAfter || 60)
    sendRequestKey = ''
  } else {
    loginError.value = res.message || '验证码发送失败'
    sendRequestKey = ''
    if (retryAfter > 0) beginCodeCooldown(retryAfter)
  }
  sendingCode.value = false
}
async function submitLogin() {
  if (!loginEmail.value.trim() || !loginCode.value.trim() || loggingIn.value) {
    loginError.value = '请输入邮箱和验证码'
    return
  }
  loggingIn.value = true
  loginError.value = ''
  const res = await loginByEmail(loginEmail.value.trim(), loginCode.value.trim())
  if (!res.success) {
    loginError.value = res.message || '登录失败，请检查验证码'
    loggingIn.value = false
    return
  }
  const token = res.data?.token
  if (!token) {
    loginError.value = '登录响应缺少令牌，请重试'
    loggingIn.value = false
    return
  }
  localStorage.setItem('token', token)
  store.token = token
  store.isLoggedIn = false
  store.profileLoaded = false
  const profile = await panel()
  if (profile.success) {
    store.setUserinfo(profile.data)
    store.isLoggedIn = true
    store.profileLoaded = true
    avatarFailed.value = false
  } else if (Number(profile.code) === 401) {
    store.token = ''
    localStorage.removeItem('token')
  }
  connectSocket().catch(() => {})
  loginVisible.value = false
  profileVisible.value = false
  loggingIn.value = false
}
onMounted(() => {
  bus.on('DESKTOP_LOGIN', () => (loginVisible.value = true))
  restoreCodeCooldown()
})
onUnmounted(() => {
  bus.off('DESKTOP_LOGIN')
  if (profileHideTimer.value) window.clearTimeout(profileHideTimer.value)
  if (sendCodeTimer) window.clearInterval(sendCodeTimer)
})
</script>

<style scoped lang="less">
.nivo-shell {
  display: flex;
  width: 100%;
  height: 100%;
  min-height: 620px;
  overflow: hidden;
  color: #f4f4f7;
  background: #12131c;
}
.nivo-sidebar {
  display: flex;
  flex: 0 0 190px;
  flex-direction: column;
  width: 190px;
  padding: 21px 14px 17px;
  box-sizing: border-box;
  border-right: 1px solid #242532;
  background: #151620;
}
.brand {
  display: flex;
  align-items: center;
  gap: 7px;
  height: 35px;
  padding: 0 8px 26px;
  color: #f7f7fa;
  font-size: 20px;
}
.brand-mark {
  display: grid;
  place-items: center;
  width: 29px;
  height: 29px;
  border-radius: 8px;
  color: #0f1118;
  background: linear-gradient(135deg, #40e8e2, #ff3b65);
  font-size: 18px;
  font-weight: 900;
}
.brand strong {
  letter-spacing: 1.7px;
}
.brand-sub {
  margin-left: 1px;
  color: #9296a5;
  font-size: 12px;
}
.primary-nav,
.secondary-nav {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.primary-nav a,
.secondary-nav a {
  position: relative;
  display: flex;
  align-items: center;
  gap: 13px;
  height: 45px;
  padding: 0 14px;
  border-radius: 9px;
  color: #b1b3be;
  text-decoration: none;
  font-size: 15px;
  font-weight: 600;
}
.primary-nav a:hover,
.secondary-nav a:hover {
  color: #fff;
  background: #20212c;
}
.primary-nav a.active {
  color: #fff;
  background: #30313e;
}
.primary-nav svg,
.secondary-nav svg {
  font-size: 21px;
}
.secondary-nav a b {
  position: absolute;
  left: 28px;
  top: 8px;
  color: #fa3d64;
  font-size: 12px;
}
.nav-divider {
  height: 1px;
  margin: 17px 10px 14px;
  background: #282934;
}
.sidebar-spacer {
  flex: 1;
}
.download-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 13px 12px;
  border: 1px solid #2c3142;
  border-radius: 10px;
  color: #e8eaf1;
  text-align: left;
  background: #191b27;
  cursor: pointer;
}
.download-card svg {
  color: #2ee5dc;
  font-size: 20px;
}
.download-card strong {
  font-size: 12px;
}
.download-card small {
  color: #8d92a1;
  font-size: 10px;
}
.sidebar-tools {
  display: flex;
  justify-content: space-between;
  margin-top: 17px;
  padding: 0 4px;
}
.sidebar-tools button {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 0;
  color: #8f94a4;
  background: transparent;
  cursor: pointer;
}
.sidebar-tools svg {
  font-size: 19px;
}
.nivo-main {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  background: #12131c;
}
.nivo-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: 0 0 70px;
  height: 70px;
  padding: 0 22px;
  box-sizing: border-box;
  border-bottom: 1px solid #242532;
  background: #12131c;
}
.search-form {
  display: flex;
  align-items: center;
  width: min(510px, 42vw);
  height: 44px;
  padding-left: 16px;
  gap: 10px;
  border: 1px solid #30323f;
  border-radius: 14px;
  color: #777b89;
  background: #252631;
}
.search-form:focus-within {
  border-color: #555969;
  background: #2b2c38;
}
.search-form input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  color: #f6f7fb;
  background: transparent;
  font: inherit;
}
.search-form input::placeholder {
  color: #777b89;
}
.search-form button {
  border: 0;
  color: #9da1ae;
  background: transparent;
  cursor: pointer;
}
.search-submit {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 30px;
  margin-right: 5px;
  padding: 0 12px;
  border-left: 1px solid #393b49 !important;
  color: #f5f6f8 !important;
  font-weight: 700;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 13px;
}
.header-link {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 0;
  border: 0;
  color: #a9adba;
  background: transparent;
  font-size: 10px;
  cursor: pointer;
}
.header-link:hover {
  color: #fff;
}
.header-link svg {
  font-size: 19px;
}
.header-link i {
  position: absolute;
  top: -6px;
  right: -9px;
  padding: 1px 3px;
  border-radius: 5px;
  color: #fff;
  background: #f33961;
  font-size: 8px;
  font-style: normal;
}
.login-button {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 38px;
  padding: 0 18px;
  border: 0;
  border-radius: 13px;
  color: #fff;
  background: #ff315b;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}
.login-button:hover {
  background: #ff486c;
}
.nivo-content {
  flex: 1;
  min-height: 0;
  overflow: auto;
  background: #12131c;
}
.profile-trigger {
  position: relative;
  align-self: center;
  margin-left: 4px;
  padding: 7px 0;
}
.profile-button {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  padding: 0;
  border: 2px solid #3a3d4c;
  border-radius: 50%;
  background: #272936;
  cursor: pointer;
}
.profile-button img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
}
.profile-menu {
  position: absolute;
  z-index: 20;
  top: 52px;
  right: -8px;
  width: 220px;
  padding: 10px;
  border: 1px solid #353846;
  border-radius: 10px;
  background: #20212c;
  box-shadow: 0 16px 40px #0008;
}
.profile-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 8px 13px;
  border-bottom: 1px solid #353744;
}
.profile-summary img {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  object-fit: cover;
}
.profile-summary div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.profile-summary strong {
  color: #fff;
  font-size: 13px;
}
.profile-summary small {
  color: #9ea2b0;
  font-size: 11px;
}
.profile-menu > button {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  height: 38px;
  padding: 0 8px;
  border: 0;
  border-radius: 6px;
  color: #d3d5dd;
  background: transparent;
  text-align: left;
  cursor: pointer;
}
.profile-menu > button:hover {
  color: #fff;
  background: #30323f;
}
.profile-menu > button svg {
  font-size: 17px;
}
.profile-menu .menu-arrow {
  margin-left: auto;
  color: #777b8b;
  font-size: 14px;
}
.profile-trigger::after {
  position: absolute;
  z-index: 19;
  top: 42px;
  right: -22px;
  width: 140px;
  height: 16px;
  content: '';
}
.login-backdrop {
  position: fixed;
  z-index: 1000;
  inset: 0;
  display: grid;
  place-items: center;
  background: #000b;
}
.login-modal {
  position: relative;
  width: min(860px, calc(100vw - 40px));
  padding: 36px 46px 29px;
  border-radius: 18px;
  color: #282a34;
  background: #fff;
  box-shadow: 0 25px 90px #0008;
}
.modal-close {
  position: absolute;
  top: 21px;
  right: 23px;
  border: 0;
  color: #a4a7af;
  background: transparent;
  cursor: pointer;
}
.modal-close svg {
  font-size: 22px;
}
.login-modal h2 {
  margin: 0 0 31px;
  text-align: center;
  font-size: 25px;
}
.login-columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  min-height: 365px;
}
.qr-column {
  display: flex;
  flex-direction: column;
  align-items: center;
  border-right: 1px solid #ececf0;
}
.qr-column h3 {
  margin: 4px 0 20px;
  font-size: 16px;
  font-weight: 500;
}
.qr-frame {
  display: grid;
  place-items: center;
  width: 210px;
  height: 210px;
  border: 1px solid #edf0f3;
  border-radius: 13px;
}
.qr-frame img {
  width: 182px;
  height: 182px;
  object-fit: contain;
}
.qr-column p {
  margin: 19px 0 13px;
  color: #92959e;
  font-size: 13px;
}
.qr-column em {
  color: #fa4264;
  font-style: normal;
}
.qr-column button {
  padding: 7px 14px;
  border: 1px solid #ebedf0;
  border-radius: 16px;
  color: #777b84;
  background: #fff;
  cursor: pointer;
}
.account-column {
  display: flex;
  flex-direction: column;
  padding: 0 60px;
}
.login-tabs {
  display: flex;
  gap: 25px;
  margin: 4px 0 27px;
  font-size: 16px;
}
.login-tabs b {
  color: #242732;
}
.login-tabs span {
  color: #b6b9c1;
}
.account-column label {
  display: flex;
  align-items: center;
  height: 53px;
  margin-bottom: 17px;
  padding: 0 14px;
  border-radius: 10px;
  color: #676c77;
  background: #f6f6f8;
}
.account-column label span {
  padding-right: 12px;
  border-right: 1px solid #e6e7ea;
}
.account-column input {
  flex: 1;
  min-width: 0;
  padding-left: 12px;
  border: 0;
  outline: 0;
  background: transparent;
  font: inherit;
}
.account-column label button {
  border: 0;
  color: #a4a7af;
  background: transparent;
  cursor: pointer;
}
.modal-login {
  height: 53px;
  margin-top: 24px;
  border: 0;
  border-radius: 11px;
  color: #fff;
  background: #ffadc0;
  font-size: 16px;
  cursor: pointer;
}
.account-column small {
  margin-top: 15px;
  color: #b9bbc1;
  text-align: center;
  font-size: 11px;
}
@media (max-width: 1100px) {
  .nivo-sidebar {
    flex-basis: 170px;
    width: 170px;
  }
  .header-actions {
    gap: 8px;
  }
  .header-link:nth-child(-n + 3) {
    display: none;
  }
  .login-button {
    padding: 0 12px;
  }
}
</style>
<style scoped lang="less">
.brand-logo {
  width: 29px;
  height: 29px;
  border-radius: 8px;
  object-fit: cover;
}
.account-column label button.ready {
  color: #ff315b;
  font-weight: 700;
}
.login-error {
  margin: 2px 0 -12px;
  color: #ed4165;
  font-size: 12px;
}
.modal-login:disabled,
.account-column label button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}
.nivo-sidebar {
  flex-basis: 178px;
  width: 178px;
  border-right: 0;
  padding-top: 12px;
}
.sidebar-group {
  display: flex;
  flex-direction: column;
}
.primary-nav,
.secondary-nav {
  gap: 1px;
}
.primary-nav a,
.secondary-nav a {
  height: 39px;
}
.brand {
  padding-bottom: 14px;
}
.nav-divider {
  height: 1px;
  margin: 12px 10px;
  background: #292b37;
}
.nivo-header {
  position: relative;
  border-bottom: 0;
}
.search-form {
  position: absolute;
  left: 50%;
  width: min(460px, 42vw);
  transform: translateX(-50%);
}
.header-actions {
  margin-left: auto;
}
.search-icon {
  color: #8790a8;
  font-size: 18px;
}
.profile-button img {
  display: block;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
}
.profile-login-hint {
  position: absolute;
  z-index: 30;
  top: 50px;
  right: -8px;
  display: flex;
  align-items: center;
  gap: 7px;
  height: 34px;
  padding: 0 12px;
  border: 1px solid #353846;
  border-radius: 8px;
  color: #f3f4f8;
  background: #20212c;
  box-shadow: 0 12px 26px #0006;
  cursor: pointer;
  pointer-events: auto;
}
.profile-login-hint:hover {
  color: #fff;
  border-color: #ff4969;
}
.profile-login-hint svg {
  font-size: 16px;
  color: #ff4969;
}
.account-column input {
  height: 36px;
  min-height: 36px;
  box-sizing: border-box;
  font-size: 14px;
  line-height: 36px;
}
.account-column label {
  box-sizing: border-box;
}
.account-column label.filled {
  color: #343744;
  background: #eef0f5;
}
.account-column label:focus-within {
  outline: 2px solid #ff9bb1;
  outline-offset: 1px;
  background: #fff;
}
.account-column label button.ready {
  color: #ff315b;
  font-weight: 700;
}
.modal-login.ready {
  color: #fff;
  background: #ff315b;
}
.search-form {
  padding: 0 10px 0 16px;
}
.search-form .search-icon {
  display: none;
}
.search-submit {
  display: grid;
  place-items: center;
  width: 36px;
  height: 30px;
  margin-left: auto;
  margin-right: 0;
  padding: 0;
  gap: 0;
}
.search-submit span {
  display: none;
}
.search-submit svg {
  font-size: 18px;
}
.account-column label {
  padding-left: 14px;
}
.account-column label input {
  padding-left: 0;
}
.brand-qr img {
  width: 92px;
  height: 92px;
  border-radius: 22px;
}
</style>
